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
        return metrics(null, null, null);
    }

    public Metrics metrics(LocalDate from, LocalDate to, Long channelId) {
        var policy = sla.policy();
        String timezone = policy.timezone();
        ZoneId zone = ZoneId.of(timezone);
        String mongoTimezone =
            zone.normalized() instanceof ZoneOffset offset
                ? offset.equals(ZoneOffset.UTC)
                    ? "UTC"
                    : offset.getId()
                : timezone;
        Instant now = Instant.now();
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate firstDay = from == null ? today.minusDays(6) : from;
        LocalDate lastDay = to == null ? today : to;
        Date start = Date.from(firstDay.atStartOfDay(zone).toInstant());
        Date end = Date.from(lastDay.plusDays(1).atStartOfDay(zone).toInstant());
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
            .filter(g -> !Set.of("CLOSED", "RESOLVED", "CANCELLED").contains(g.status()))
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
                                        new Document("$nin", List.of("CLOSED", "RESOLVED", "CANCELLED"))
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
                                        new Document("$nin", List.of("CLOSED", "RESOLVED", "CANCELLED"))
                                    )
                                ),
                                new Document("$count", "value")
                            )
                        )
                        .append("openedDaily", dailyCounts("createdAt", mongoTimezone, start, end, channelId))
                        .append("resolvedDaily", resolvedCounts(mongoTimezone, start, end, channelId))
                        .append(
                            "resolvedToday",
                            resolvedCounts(mongoTimezone, todayStart(zone), todayEnd(zone), null)
                        )
                        .append("replyAging", replyAging(now))
                        .append("average", averageCycles(firstDay, lastDay, zone, channelId))
                )
            )
        );
        Document result = counts.get(0);
        Map<String, Long> opened = readCounts(result, "openedDaily");
        Map<String, Long> resolved = readCounts(result, "resolvedDaily");
        Map<String, Long> resolvedToday = readCounts(result, "resolvedToday");
        List<DayVolume> dailyVolume = firstDay
            .datesUntil(lastDay.plusDays(1))
            .map(day ->
                new DayVolume(
                    day.toString(),
                    opened.getOrDefault(day.toString(), 0L),
                    resolved.getOrDefault(day.toString(), 0L)
                )
            )
            .toList();
        Map<String, Long> aging = readCounts(result, "replyAging");
        return new Metrics(
            open,
            count(result, "unassigned"),
            count(result, "waiting"),
            resolvedToday.getOrDefault(today.toString(), 0L),
            average(result),
            groups,
            repository.count(new Filter(null, null, null, null, null, null, null, null, null, "AT_RISK")),
            repository.count(new Filter(null, null, null, null, null, null, null, null, null, "BREACHED")),
            timezone,
            dailyVolume,
            new ReplyAging(
                aging.getOrDefault("under15", 0L),
                aging.getOrDefault("between15And60", 0L),
                aging.getOrDefault("over60", 0L)
            ),
            repository
                .longestWaiting(now)
                .stream()
                .map(conversation -> card(conversation, policy))
                .toList()
        );
    }

    private Date todayStart(ZoneId zone) {
        return Date.from(LocalDate.now(zone).atStartOfDay(zone).toInstant());
    }

    private Date todayEnd(ZoneId zone) {
        return Date.from(LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant());
    }

    private List<Document> averageCycles(LocalDate from, LocalDate to, ZoneId zone, Long channelId) {
        var match = new Document();
        if (channelId != null) {
            match.append("channelId", channelId);
        }
        var stages = new ArrayList<Document>();
        if (!match.isEmpty()) {
            stages.add(new Document("$match", match));
        }
        stages.add(new Document("$unwind", "$resolutionCycles"));
        var start = Date.from(from.atStartOfDay(zone).toInstant());
        var end = Date.from(to.plusDays(1).atStartOfDay(zone).toInstant());
        stages.add(
            new Document(
                "$match",
                new Document("resolutionCycles.resolvedAt", new Document("$gte", start).append("$lt", end))
            )
        );
        stages.add(
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
                                    List.of("$resolutionCycles.resolvedAt", "$resolutionCycles.startedAt")
                                ),
                                60000
                            )
                        )
                    )
                )
            )
        );
        return stages;
    }

    private List<Document> dailyCounts(String field, String timezone, Date start, Date end, Long channelId) {
        var day = new Document(
            "$dateToString",
            new Document("format", "%Y-%m-%d").append("date", "$" + field).append("timezone", timezone)
        );
        var match = new Document(field, new Document("$gte", start).append("$lt", end));
        if (channelId != null) {
            match.append("channelId", channelId);
        }
        return List.of(
            new Document("$match", match),
            new Document("$group", new Document("_id", day).append("count", new Document("$sum", 1)))
        );
    }

    private List<Document> resolvedCounts(String timezone, Date start, Date end, Long channelId) {
        var cycles = new Document("$ifNull", List.of("$resolutionCycles", List.of()));
        var dates = new Document(
            "$cond",
            List.of(
                new Document("$gt", List.of(new Document("$size", cycles), 0)),
                "$resolutionCycles.resolvedAt",
                List.of("$resolvedAt")
            )
        );
        var pipeline = new ArrayList<Document>();
        var project = new Document("dayDates", dates);
        if (channelId != null) {
            pipeline.add(new Document("$match", new Document("channelId", channelId)));
        }
        pipeline.add(new Document("$project", project));
        pipeline.add(new Document("$unwind", "$dayDates"));
        pipeline.addAll(dailyCounts("dayDates", timezone, start, end, null));
        return pipeline;
    }

    private List<Document> replyAging(Instant now) {
        var waitingAt = "$waitingForAgentSince";
        var bucket = new Document(
            "$switch",
            new Document(
                "branches",
                List.of(
                    new Document(
                        "case",
                        new Document("$gt", List.of(waitingAt, Date.from(now.minusSeconds(900))))
                    ).append("then", "under15"),
                    new Document(
                        "case",
                        new Document("$gt", List.of(waitingAt, Date.from(now.minusSeconds(3600))))
                    ).append("then", "between15And60")
                )
            ).append("default", "over60")
        );
        return List.of(
            new Document(
                "$match",
                new Document(
                    "waitingForAgentSince",
                    new Document("$type", "date").append("$lte", Date.from(now))
                ).append("status", new Document("$nin", List.of("CLOSED", "RESOLVED", "CANCELLED")))
            ),
            new Document("$group", new Document("_id", bucket).append("count", new Document("$sum", 1)))
        );
    }

    private Map<String, Long> readCounts(Document result, String key) {
        Map<String, Long> counts = new HashMap<>();
        for (Document row : result.getList(key, Document.class)) {
            counts.put(row.getString("_id"), ((Number) row.get("count")).longValue());
        }
        return counts;
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
