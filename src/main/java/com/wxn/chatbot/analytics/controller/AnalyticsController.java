package com.wxn.chatbot.analytics.controller;

import com.wxn.chatbot.analytics.service.ConversationReportService;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.kanban.dto.KanbanDtos.Metrics;
import com.wxn.chatbot.kanban.service.KanbanService;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/admin/analytics")
public class AnalyticsController {

    private final KanbanService kanban;
    private final ConversationReportService reports;

    public AnalyticsController(KanbanService kanban, ConversationReportService reports) {
        this.kanban = kanban;
        this.reports = reports;
    }

    @GetMapping("/overview")
    public Metrics overview(
        @RequestParam(required = false) java.time.LocalDate from,
        @RequestParam(required = false) java.time.LocalDate to,
        @RequestParam(required = false) @jakarta.validation.constraints.Positive Long channelId
    ) {
        if ((from == null) != (to == null) || (from != null && to.isBefore(from))) {
            throw ApiException.badRequest("Informe um intervalo de datas válido.");
        }
        if (from != null && java.time.temporal.ChronoUnit.DAYS.between(from, to) > 365) {
            throw ApiException.badRequest("O período máximo é de 366 dias.");
        }
        return kanban.metrics(from, to, channelId);
    }

    @GetMapping("/conversations.csv")
    public ResponseEntity<StreamingResponseBody> export(
        @RequestParam(required = false) java.time.LocalDate from,
        @RequestParam(required = false) java.time.LocalDate to,
        @RequestParam(required = false) String status
    ) {
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest("A data final deve ser igual ou posterior à inicial.");
        }
        if (
            status != null &&
            !status.isBlank() &&
            !Set.of("PENDING", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CLOSED", "CANCELLED").contains(
                status
            )
        ) {
            throw ApiException.badRequest("Status inválido para exportação.");
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=atendimentos.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(output -> reports.export(output, from, to, status));
    }
}
