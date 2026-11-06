package com.wxn.chatbot.kanban.repository;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.kanban.dto.KanbanDtos.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class KanbanRepository {

    private final MongoTemplate mongo;
    private final com.wxn.chatbot.sla.service.SlaService sla;

    public KanbanRepository(MongoTemplate mongo, com.wxn.chatbot.sla.service.SlaService sla) {
        this.sla = sla;
        this.mongo = mongo;
    }

    private Criteria criteria(Filter f) {
        List<Criteria> clauses = new ArrayList<>();
        if (f.status() != null && !f.status().isBlank()) {
            var status = com.wxn.chatbot.conversation.model.ConversationStatus.valueOf(f.status());
            if (status.name().equals("PENDING") || status.name().equals("IN_PROGRESS")) {
                clauses.add(Criteria.where("status").nin("CLOSED", "RESOLVED", "WAITING_CUSTOMER"));
                clauses.add(
                    status.name().equals("PENDING")
                        ? Criteria.where("assignedMemberId").is(null)
                        : Criteria.where("assignedMemberId").ne(null)
                );
            } else {
                clauses.add(Criteria.where("status").is(status.name()));
            }
        }
        if (f.assignedMemberId() != null) {
            clauses.add(Criteria.where("assignedMemberId").is(f.assignedMemberId()));
        }
        if (Boolean.TRUE.equals(f.unassigned())) {
            clauses.add(Criteria.where("assignedMemberId").is(null));
        }
        if (f.channelId() != null) {
            clauses.add(Criteria.where("channelId").is(f.channelId()));
        }
        if (f.priority() != null && !f.priority().isBlank()) {
            clauses.add(
                "NORMAL".equals(f.priority())
                    ? new Criteria().orOperator(
                          Criteria.where("priority").is("NORMAL"),
                          Criteria.where("priority").is(null)
                      )
                    : Criteria.where("priority").is(f.priority())
            );
        }
        if (f.search() != null && !f.search().isBlank()) {
            String term = Pattern.quote(f.search().trim());
            clauses.add(
                new Criteria().orOperator(
                    Criteria.where("displayName").regex(term, "i"),
                    Criteria.where("contactPhone").regex(term),
                    Criteria.where("lastMessagePreview").regex(term, "i")
                )
            );
        }
        return clauses.isEmpty() ? new Criteria() : new Criteria().andOperator(clauses);
    }

    private Document op(String operator, Object... values) {
        return new Document(operator, java.util.Arrays.asList(values));
    }

    private Object deadline(String field, double minutes) {
        return op(
            "$add",
            new Document("$ifNull", List.of("$" + field, new java.util.Date(253402214400000L))),
            Math.round(minutes * 60000)
        );
    }

    private List<Document> pipeline(Filter f) {
        var policy = sla.policy();
        var now = new java.util.Date();
        Object closed = op("$in", "$status", List.of("CLOSED", "RESOLVED"));
        Object waiting = op(
            "$and",
            op("$ne", new Document("$ifNull", java.util.Arrays.asList("$waitingForAgentSince", null)), null),
            op("$ne", "$status", "WAITING_CUSTOMER")
        );
        Object response = op(
            "$cond",
            op("$eq", new Document("$ifNull", java.util.Arrays.asList("$lastOutboundAt", null)), null),
            deadline("waitingForAgentSince", policy.firstResponseMinutes()),
            deadline("waitingForAgentSince", policy.nextResponseMinutes())
        );
        Object responseWarning = op(
            "$cond",
            op("$eq", new Document("$ifNull", java.util.Arrays.asList("$lastOutboundAt", null)), null),
            deadline(
                "waitingForAgentSince",
                (policy.firstResponseMinutes() * policy.warningThresholdPercent()) / 100.0
            ),
            deadline(
                "waitingForAgentSince",
                (policy.nextResponseMinutes() * policy.warningThresholdPercent()) / 100.0
            )
        );
        Object breached = op(
            "$and",
            op("$not", closed),
            op(
                "$or",
                op("$and", waiting, op("$gte", now, response)),
                op("$gte", now, deadline("cycleStartedAt", policy.resolutionMinutes()))
            )
        );
        Object risk = op(
            "$and",
            op("$not", closed),
            op(
                "$or",
                op("$and", waiting, op("$gte", now, responseWarning)),
                op(
                    "$gte",
                    now,
                    deadline(
                        "cycleStartedAt",
                        (policy.resolutionMinutes() * policy.warningThresholdPercent()) / 100.0
                    )
                )
            )
        );
        Object slaStatus = policy.enabled()
            ? op(
                  "$cond",
                  closed,
                  "COMPLETED",
                  op(
                      "$cond",
                      breached,
                      "BREACHED",
                      op(
                          "$cond",
                          risk,
                          "AT_RISK",
                          op(
                              "$cond",
                              op(
                                  "$or",
                                  waiting,
                                  op(
                                      "$ne",
                                      new Document(
                                          "$ifNull",
                                          java.util.Arrays.asList("$cycleStartedAt", null)
                                      ),
                                      null
                                  )
                              ),
                              "ON_TRACK",
                              "NOT_APPLICABLE"
                          )
                      )
                  )
              )
            : "NOT_CONFIGURED";
        Object priorityScore = op(
            "$cond",
            op("$eq", "$priority", "URGENT"),
            45,
            op("$cond", op("$eq", "$priority", "HIGH"), 25, 0)
        );
        Object unassigned = op(
            "$cond",
            op("$eq", new Document("$ifNull", java.util.Arrays.asList("$assignedMemberId", null)), null),
            35,
            0
        );
        Object slaScore = op(
            "$cond",
            op("$eq", "$_slaStatus", "BREACHED"),
            100,
            op("$cond", op("$eq", "$_slaStatus", "AT_RISK"), 50, 0)
        );
        List<Document> stages = new ArrayList<>();
        stages.add(new Document("$match", criteria(f).getCriteriaObject()));
        stages.add(new Document("$addFields", new Document("_slaStatus", slaStatus)));
        if (f.sla() != null && !f.sla().isBlank()) {
            stages.add(new Document("$match", new Document("_slaStatus", f.sla())));
        }
        stages.add(
            new Document(
                "$addFields",
                new Document(
                    "_attentionScore",
                    op("$cond", closed, 0, op("$add", priorityScore, unassigned, slaScore))
                )
            )
        );
        return stages;
    }

    public List<Conversation> page(Filter f) {
        int size = f.size() == null ? 30 : f.size();
        int page = f.page() == null ? 0 : f.page();
        var stages = pipeline(f);
        Document sort = new Document();
        if (f.sort() == null || "ATTENTION".equals(f.sort())) {
            sort.append("_attentionScore", -1).append("waitingForAgentSince", 1).append("createdAt", 1);
        } else {
            sort.append(
                "OLDEST".equals(f.sort()) ? "createdAt" : "updatedAt",
                "OLDEST".equals(f.sort()) ? 1 : -1
            );
        }
        sort.append("_id", 1);
        stages.add(new Document("$sort", sort));
        stages.add(new Document("$skip", (long) page * size));
        stages.add(new Document("$limit", size));
        return metrics(stages)
            .stream()
            .map(d -> mongo.getConverter().read(Conversation.class, d))
            .toList();
    }

    public long count(Filter f) {
        var stages = pipeline(f);
        stages.add(new Document("$count", "total"));
        var rows = metrics(stages);
        return rows.isEmpty() ? 0 : ((Number) rows.get(0).get("total")).longValue();
    }

    public List<Document> metrics(List<Document> pipeline) {
        return mongo
            .getCollection("conversations")
            .aggregate(pipeline)
            .allowDiskUse(true)
            .into(new ArrayList<>());
    }

    public Conversation priority(
        String id,
        Priority request,
        com.wxn.chatbot.conversation.document.ConversationActivity activity
    ) {
        var updated = mongo.findAndModify(
            Query.query(
                Criteria.where("_id").is(new ObjectId(id)).and("version").is(request.expectedVersion())
            ),
            new Update()
                .push("pendingActivities", activity)
                .set("priority", request.priority())
                .inc("version", 1)
                .set("updatedAt", java.time.OffsetDateTime.now()),
            FindAndModifyOptions.options().returnNew(true),
            Conversation.class
        );
        if (updated == null) {
            throw ApiException.conflict("Conversa alterada; atualize antes de repetir");
        }
        return updated;
    }
}
