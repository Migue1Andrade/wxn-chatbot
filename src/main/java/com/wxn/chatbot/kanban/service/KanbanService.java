package com.wxn.chatbot.kanban.service;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.kanban.dto.KanbanDtos.*;
import com.wxn.chatbot.kanban.repository.KanbanRepository;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.stereotype.Service;

@Service
public class KanbanService {

    private final KanbanRepository repository;
    private final ConversationService conversations;
    private final AttentionScoreService attention;
    private final ConversationEventPublisher events;
    private final com.wxn.chatbot.conversation.service.ConversationActivityService activities;
    private final com.wxn.chatbot.sla.service.SlaService sla;

    public KanbanService(
        KanbanRepository repository,
        ConversationService conversations,
        AttentionScoreService attention,
        ConversationEventPublisher events,
        com.wxn.chatbot.conversation.service.ConversationActivityService activities,
        com.wxn.chatbot.sla.service.SlaService sla
    ) {
        this.repository = repository;
        this.conversations = conversations;
        this.attention = attention;
        this.events = events;
        this.activities = activities;
        this.sla = sla;
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private Card card(Conversation c, com.wxn.chatbot.sla.service.SlaService.Policy policy) {
        var score = attention.evaluate(c);
        var result = sla.evaluate(c, policy, Instant.now());
        var reasons = new ArrayList<>(result.reasons());
        reasons.addAll(score.reasons());
        int slaScore = "BREACHED".equals(result.status()) ? 100 : "AT_RISK".equals(result.status()) ? 50 : 0;
        return new Card(
            ConversationMapper.response(c),
            c.getPriority() == null ? "NORMAL" : c.getPriority(),
            c.getDepartmentId(),
            instant(c.getWaitingForAgentSince()),
            instant(c.getResolvedAt()),
            instant(c.getCycleStartedAt()),
            c.getReopenCount() == null ? 0 : c.getReopenCount(),
            score.score() + slaScore,
            reasons,
            result.status()
        );
    }

    public Page page(Filter f) {
        long total = repository.count(f);
        var policy = sla.policy();
        var rows = repository
            .page(f)
            .stream()
            .map(c -> card(c, policy))
            .toList();
        return new Page(
            rows,
            total,
            ((long) (f.page() == null ? 0 : f.page()) + 1) * (f.size() == null ? 30 : f.size()) < total
        );
    }

    public Card details(String id) {
        return card(conversations.require(id), sla.policy());
    }

    public Card priority(String id, Priority request, Long actorMemberId) {
        var current = conversations.require(id);
        var activity = activities.prepare(
            current,
            com.wxn.chatbot.conversation.model.ConversationActivityType.CONVERSATION_PRIORITY_CHANGED,
            actorMemberId,
            current.getAssignedMemberId(),
            "Prioridade: " + request.priority(),
            request.expectedVersion() + 1,
            OffsetDateTime.now()
        );
        Conversation updated = repository.priority(id, request, activity);
        activities.flush(updated);
        events.publish(ConversationEventTypes.CONVERSATION_UPDATED, ConversationMapper.response(updated));
        return card(updated, sla.policy());
    }

    public Metrics metrics() {
        var rows = repository.metrics(
            List.of(
                new Document(
                    "$group",
                    new Document("_id", "$status").append("count", new Document("$sum", 1))
                )
            )
        );
        List<Group> groups = rows
            .stream()
            .map(d -> new Group(d.getString("_id"), ((Number) d.get("count")).longValue()))
            .toList();
        long open = groups
            .stream()
            .filter(g -> !"CLOSED".equals(g.status()) && !"RESOLVED".equals(g.status()))
            .mapToLong(Group::count)
            .sum();
        var counts = repository.metrics(
            List.of(
                new Document(
                    "$facet",
                    new Document()
                        .append(
                            "unassigned",
                            List.of(
                                new Document(
                                    "$match",
                                    new Document("assignedMemberId", null).append(
                                        "status",
                                        new Document("$nin", List.of("CLOSED", "RESOLVED"))
                                    )
                                ),
                                new Document("$count", "value")
                            )
                        )
                        .append(
                            "waiting",
                            List.of(
                                new Document(
                                    "$match",
                                    new Document("waitingForAgentSince", new Document("$ne", null)).append(
                                        "status",
                                        new Document("$nin", List.of("CLOSED", "RESOLVED"))
                                    )
                                ),
                                new Document("$count", "value")
                            )
                        )
                        .append(
                            "resolved",
                            List.of(
                                new Document(
                                    "$match",
                                    new Document(
                                        "resolvedAt",
                                        new Document(
                                            "$gte",
                                            Date.from(
                                                LocalDate.now(ZoneOffset.UTC)
                                                    .atStartOfDay(ZoneOffset.UTC)
                                                    .toInstant()
                                            )
                                        )
                                    )
                                ),
                                new Document("$count", "value")
                            )
                        )
                        .append(
                            "average",
                            List.of(
                                new Document("$unwind", "$resolutionCycles"),
                                new Document(
                                    "$group",
                                    new Document("_id", null).append(
                                        "value",
                                        new Document(
                                            "$avg",
                                            new Document(
                                                "$divide",
                                                List.of(
                                                    new Document(
                                                        "$subtract",
                                                        List.of(
                                                            "$resolutionCycles.resolvedAt",
                                                            "$resolutionCycles.startedAt"
                                                        )
                                                    ),
                                                    60000
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                )
            )
        );
        Document result = counts.get(0);
        return new Metrics(
            open,
            count(result, "unassigned"),
            count(result, "waiting"),
            count(result, "resolved"),
            average(result),
            groups,
            repository.count(new Filter(null, null, null, null, null, null, null, null, null, "AT_RISK")),
            repository.count(new Filter(null, null, null, null, null, null, null, null, null, "BREACHED")),
            sla.policy().timezone()
        );
    }

    private long count(Document d, String key) {
        var list = d.getList(key, Document.class);
        return list.isEmpty() ? 0 : ((Number) list.get(0).get("value")).longValue();
    }

    private Double average(Document d) {
        var list = d.getList("average", Document.class);
        return list.isEmpty() || list.get(0).get("value") == null
            ? null
            : ((Number) list.get(0).get("value")).doubleValue();
    }
}
