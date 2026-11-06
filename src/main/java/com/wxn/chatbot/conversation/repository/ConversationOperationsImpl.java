package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.*;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;

public class ConversationOperationsImpl implements ConversationOperations {

    private final MongoTemplate mongo;

    public ConversationOperationsImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Optional<Conversation> refreshPreview(ObjectId id, ObjectId message, String preview) {
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(id).addCriteria(
                    Criteria.where("lastMessageId").is(message).and("lastMessagePreview").ne(preview)
                ),
                new Update().set("lastMessagePreview", preview).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public void linkContact(ObjectId id, ObjectId contact) {
        mongo.updateFirst(
            scope(id).addCriteria(Criteria.where("contactId").is(null)),
            new Update().set("contactId", contact),
            Conversation.class
        );
    }

    private Query scope(ObjectId id) {
        return Query.query(Criteria.where("_id").is(id));
    }

    public Optional<Conversation> updateDisplayName(ObjectId id, String displayName) {
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(id).addCriteria(
                    Criteria.where("displayName").ne(displayName).and("displayNameCustomized").ne(true)
                ),
                new Update()
                    .set("displayName", displayName)
                    .max("updatedAt", OffsetDateTime.now())
                    .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public void recordDirection(ObjectId id, OffsetDateTime at, boolean inbound) {
        java.util.Date timestamp = java.util.Date.from(at.toInstant());
        Object waiting = new Document("$ifNull", List.of("$waitingForAgentSince", timestamp));
        Object pending = inbound
            ? new Document(
                  "$cond",
                  List.of(
                      new Document(
                          "$gt",
                          List.of(
                              timestamp,
                              new Document("$ifNull", List.of("$lastOutboundAt", new java.util.Date(0)))
                          )
                      ),
                      new Document("$min", List.of(waiting, timestamp)),
                      new Document("$ifNull", List.of("$waitingForAgentSince", "$$REMOVE"))
                  )
              )
            : new Document(
                  "$cond",
                  List.of(
                      new Document("$lte", List.of(waiting, timestamp)),
                      "$$REMOVE",
                      "$waitingForAgentSince"
                  )
              );
        String field = inbound ? "lastInboundAt" : "lastOutboundAt";
        mongo
            .getCollection("conversations")
            .updateOne(
                new Document("_id", id),
                List.of(
                    new Document(
                        "$set",
                        new Document(field, new Document("$max", List.of("$" + field, timestamp))).append(
                            "waitingForAgentSince",
                            pending
                        )
                    )
                )
            );
    }

    public Optional<Conversation> updateActivity(
        ObjectId id,
        OffsetDateTime messageAt,
        ObjectId messageId,
        String preview
    ) {
        Criteria newerMessage = new Criteria().orOperator(
            Criteria.where("lastMessageAt").lt(messageAt),
            Criteria.where("lastMessageAt").is(null),
            new Criteria().andOperator(
                Criteria.where("lastMessageAt").is(messageAt),
                new Criteria().orOperator(
                    Criteria.where("lastMessageId").lt(messageId),
                    Criteria.where("lastMessageId").is(null)
                )
            )
        );
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(id).addCriteria(newerMessage),
                new Update()
                    .max("updatedAt", messageAt)
                    .set("lastMessageAt", messageAt)
                    .set("lastMessageId", messageId)
                    .set("lastMessagePreview", preview)
                    .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public Optional<Conversation> changeState(
        Conversation current,
        long expectedVersion,
        ConversationStatus status,
        Long memberId,
        OffsetDateTime closedAt,
        ConversationActivity activity
    ) {
        Query query = scope(current.getId());
        query.addCriteria(Criteria.where("version").is(expectedVersion));
        Update update = new Update()
            .set("status", status.name())
            .set("assignedMemberId", memberId)
            .set("closedAt", closedAt)
            .max("updatedAt", activity.getCreatedAt())
            .set("version", expectedVersion + 1)
            .push("pendingActivities", activity);
        if (
            status == ConversationStatus.RESOLVED ||
            status == ConversationStatus.CLOSED ||
            status == ConversationStatus.WAITING_CUSTOMER
        ) {
            update.unset("waitingForAgentSince");
        }
        update.set("lastStatusChangedAt", activity.getCreatedAt());
        if (!Objects.equals(current.getAssignedMemberId(), memberId)) {
            update.set("assignedAt", activity.getCreatedAt());
        }
        if (status == ConversationStatus.RESOLVED && current.operationalStatus() != status) {
            update.set("resolvedAt", activity.getCreatedAt());
            if (current.getCycleStartedAt() != null) {
                update.push(
                    "resolutionCycles",
                    new Document("resolvedAt", java.util.Date.from(activity.getCreatedAt().toInstant()))
                        .append("startedAt", java.util.Date.from(current.getCycleStartedAt().toInstant()))
                        .append("memberId", memberId)
                );
            }
        }
        if (
            (current.operationalStatus() == ConversationStatus.RESOLVED ||
                current.operationalStatus() == ConversationStatus.CLOSED) &&
            status != ConversationStatus.RESOLVED &&
            status != ConversationStatus.CLOSED
        ) {
            update
                .set("cycleStartedAt", activity.getCreatedAt())
                .set("reopenedAt", activity.getCreatedAt())
                .inc("reopenCount", 1)
                .unset("resolvedAt");
        }
        return Optional.ofNullable(
            mongo.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public void acknowledgeActivity(ObjectId id, ObjectId activityId) {
        mongo.updateFirst(
            scope(id),
            new Update().pull("pendingActivities", new Document("_id", activityId)),
            Conversation.class
        );
    }

    public List<Conversation> search(
        ConversationStatus status,
        Long assignedMemberId,
        boolean unassigned,
        int page,
        int size,
        String search,
        boolean probeNext
    ) {
        List<Criteria> conditions = new ArrayList<>();

        if (status == ConversationStatus.PENDING || status == ConversationStatus.IN_PROGRESS) {
            conditions.add(Criteria.where("status").nin("CLOSED", "RESOLVED", "WAITING_CUSTOMER"));
            conditions.add(
                status == ConversationStatus.PENDING
                    ? Criteria.where("assignedMemberId").is(null)
                    : Criteria.where("assignedMemberId").ne(null)
            );
        } else if (status != null) {
            conditions.add(Criteria.where("status").is(status.name()));
        }
        if (assignedMemberId != null) {
            conditions.add(Criteria.where("assignedMemberId").is(assignedMemberId));
        }
        if (unassigned) {
            conditions.add(Criteria.where("assignedMemberId").is(null));
        }
        if (search != null && !search.isBlank()) {
            conditions.add(searchIdentity(search.trim()));
        }
        Query query = Query.query(
            conditions.isEmpty()
                ? new Criteria()
                : new Criteria().andOperator(conditions.toArray(Criteria[]::new))
        );
        return mongo.find(
            query
                .with(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("_id")))
                .skip((long) page * size)
                .limit(size + (probeNext ? 1 : 0)),
            Conversation.class
        );
    }

    private Criteria searchIdentity(String term) {
        List<Criteria> identities = new ArrayList<>();
        identities.add(Criteria.where("displayName").regex(Pattern.quote(term), "i"));
        String phone = term.replaceAll("[ ()-]", "");
        if (!phone.isEmpty()) {
            identities.add(Criteria.where("contactPhone").regex(Pattern.quote(phone), "i"));
        }
        identities.add(Criteria.where("externalChatId").regex(Pattern.quote(term), "i"));
        if (ObjectId.isValid(term)) {
            identities.add(Criteria.where("_id").is(new ObjectId(term)));
        }
        return new Criteria().orOperator(identities.toArray(Criteria[]::new));
    }
}
