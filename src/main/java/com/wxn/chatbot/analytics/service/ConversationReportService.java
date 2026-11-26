package com.wxn.chatbot.analytics.service;

import com.wxn.chatbot.kanban.dto.KanbanDtos.Filter;
import com.wxn.chatbot.kanban.service.KanbanService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ConversationReportService {

    private final KanbanService kanban;

    public ConversationReportService(KanbanService kanban) {
        this.kanban = kanban;
    }

    private String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.stripLeading().isEmpty() && "=+@-".indexOf(text.stripLeading().charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public void export(OutputStream output, LocalDate from, LocalDate to, String status) throws IOException {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
        }
        if (
            status != null &&
            !status.isBlank() &&
            !Set.of("PENDING", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CLOSED", "CANCELLED").contains(
                status
            )
        ) {
            throw new IllegalArgumentException("Status inválido para exportação.");
        }
        var writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writer.write(
            "\ufeffprotocolo,cliente,status,prioridade,responsavel,canal,abertura,resolucao,sla\r\n"
        );
        int page = 0;
        boolean more;
        Date fromDate =
            from == null ? null : Date.from(from.atStartOfDay(ZoneId.of("America/Recife")).toInstant());
        Date toDate =
            to == null
                ? null
                : Date.from(to.plusDays(1).atStartOfDay(ZoneId.of("America/Recife")).toInstant());
        do {
            var result = kanban.page(
                new Filter(page++, 100, null, status, null, null, null, null, "OLDEST", null)
            );
            for (var card : result.items()) {
                var c = card.conversation();
                Instant opened = c.createdAt();
                if (
                    (fromDate != null && (opened == null || opened.isBefore(fromDate.toInstant()))) ||
                    (toDate != null && (opened == null || !opened.isBefore(toDate.toInstant())))
                ) {
                    continue;
                }
                Object[] values = {
                    c.id(),
                    c.displayName(),
                    c.status(),
                    card.priority(),
                    c.assignedMemberId(),
                    c.channelId(),
                    c.createdAt(),
                    card.resolvedAt(),
                    card.sla(),
                };
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        writer.write(',');
                    }
                    writer.write(cell(values[i]));
                }
                writer.write("\r\n");
            }
            more = result.hasMore();
            writer.flush();
        } while (more);
    }
}
