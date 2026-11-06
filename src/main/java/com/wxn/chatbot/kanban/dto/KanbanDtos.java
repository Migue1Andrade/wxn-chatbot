package com.wxn.chatbot.kanban.dto;

import com.wxn.chatbot.whatsapp.model.NormalizedConversation;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class KanbanDtos {

    private KanbanDtos() {}

    public record Filter(
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(100) Integer size,
        @Size(max = 100) String search,
        @Pattern(regexp = "PENDING|IN_PROGRESS|WAITING_CUSTOMER|RESOLVED|CLOSED") String status,
        @Positive Long assignedMemberId,
        Boolean unassigned,
        @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        @Positive Long channelId,
        @Pattern(regexp = "ATTENTION|RECENT|OLDEST") String sort,
        @Pattern(regexp = "ON_TRACK|AT_RISK|BREACHED|NOT_CONFIGURED|COMPLETED|NOT_APPLICABLE") String sla
    ) {}

    public record Card(
        NormalizedConversation conversation,
        String priority,
        Long departmentId,
        Instant waitingForAgentSince,
        Instant resolvedAt,
        Instant cycleStartedAt,
        int reopenCount,
        int attentionScore,
        List<String> attentionReasons,
        String sla
    ) {}

    public record Page(List<Card> items, long total, boolean hasMore) {}

    public record Group(String status, long count) {}

    public record Metrics(
        long open,
        long unassigned,
        long waitingForAgent,
        long resolvedToday,
        Double averageResolutionMinutes,
        List<Group> groups,
        long slaAtRisk,
        long slaBreached,
        String timezone
    ) {}

    public record Priority(
        @NotBlank @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        @NotNull @PositiveOrZero Long expectedVersion
    ) {}

    public record ViewInput(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 10000) String configuration
    ) {}

    public record View(Long id, String name, String configuration) {}
}
