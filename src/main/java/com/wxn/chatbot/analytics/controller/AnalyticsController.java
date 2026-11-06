package com.wxn.chatbot.analytics.controller;

import com.wxn.chatbot.analytics.service.ConversationReportService;
import com.wxn.chatbot.kanban.dto.KanbanDtos.Metrics;
import com.wxn.chatbot.kanban.service.KanbanService;
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
    public Metrics overview() {
        return kanban.metrics();
    }

    @GetMapping("/conversations.csv")
    public ResponseEntity<StreamingResponseBody> export() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=atendimentos.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(reports::export);
    }
}
