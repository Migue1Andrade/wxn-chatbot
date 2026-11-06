package com.wxn.chatbot.sla.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.sla.repository.SlaPolicyRepository;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaService {

    public record Policy(
        boolean enabled,
        @Min(1) @Max(10080) int firstResponseMinutes,
        @Min(1) @Max(10080) int nextResponseMinutes,
        @Min(1) @Max(525600) int resolutionMinutes,
        @Min(1) @Max(99) int warningThresholdPercent,
        @NotBlank String timezone,
        Long version
    ) {}

    public record Result(String status, Instant dueAt, List<String> reasons) {}

    private final SlaPolicyRepository repository;
    private final com.wxn.chatbot.conversation.event.ConversationEventPublisher events;

    public SlaService(
        SlaPolicyRepository repository,
        com.wxn.chatbot.conversation.event.ConversationEventPublisher events
    ) {
        this.events = events;
        this.repository = repository;
    }

    public Policy policy() {
        var p = repository
            .findById(1L)
            .orElseThrow(() -> ApiException.conflict("Migration da política SLA pendente"));
        return new Policy(
            p.isEnabled(),
            p.getFirstResponseMinutes(),
            p.getNextResponseMinutes(),
            p.getResolutionMinutes(),
            p.getWarningThresholdPercent(),
            p.getTimezone(),
            p.getVersion()
        );
    }

    @Transactional
    public Policy save(Policy input) {
        try {
            ZoneId.of(input.timezone());
        } catch (DateTimeException invalid) {
            throw ApiException.badRequest("Timezone inválido");
        }
        var p = repository.findById(1L).orElseThrow();
        if (!Objects.equals(input.version(), p.getVersion())) {
            throw ApiException.conflict("Política alterada; atualize");
        }
        p.update(
            input.enabled(),
            input.firstResponseMinutes(),
            input.nextResponseMinutes(),
            input.resolutionMinutes(),
            input.warningThresholdPercent(),
            input.timezone()
        );
        repository.saveAndFlush(p);
        return policy();
    }

    public record MetricsChanged(
        String conversationId
    ) implements com.wxn.chatbot.conversation.event.CanonicalEventPayload {}

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void refreshClock() {
        if (policy().enabled()) {
            events.publish("kanban.metrics.updated", new MetricsChanged(null));
        }
    }

    public Result evaluate(Conversation c, Policy p, Instant now) {
        if (!p.enabled()) {
            return new Result("NOT_CONFIGURED", null, List.of());
        }
        if (Set.of("CLOSED", "RESOLVED").contains(c.operationalStatus().name())) {
            return new Result("COMPLETED", null, List.of());
        }
        List<Instant> deadlines = new ArrayList<>();
        List<Instant> warnings = new ArrayList<>();
        Instant cycle = c.getCycleStartedAt() == null ? null : c.getCycleStartedAt().toInstant();
        if (cycle != null) {
            deadlines.add(cycle.plusSeconds(p.resolutionMinutes() * 60L));
            warnings.add(
                cycle.plusMillis((p.resolutionMinutes() * 60000L * p.warningThresholdPercent()) / 100)
            );
        }
        if (c.getWaitingForAgentSince() != null && !"WAITING_CUSTOMER".equals(c.operationalStatus().name())) {
            Instant start = c.getWaitingForAgentSince().toInstant();
            int minutes = c.getLastOutboundAt() == null ? p.firstResponseMinutes() : p.nextResponseMinutes();
            deadlines.add(start.plusSeconds(minutes * 60L));
            warnings.add(start.plusMillis((minutes * 60000L * p.warningThresholdPercent()) / 100));
        }
        if (deadlines.isEmpty()) {
            return new Result("NOT_APPLICABLE", null, List.of());
        }
        Instant due = deadlines.stream().min(Comparator.naturalOrder()).orElseThrow();
        boolean breached = !now.isBefore(due);
        boolean risk = warnings.stream().anyMatch(w -> !now.isBefore(w));
        String status = breached ? "BREACHED" : risk ? "AT_RISK" : "ON_TRACK";
        return new Result(
            status,
            due,
            breached ? List.of("SLA violado") : risk ? List.of("SLA em risco") : List.of()
        );
    }
}
