package com.wxn.chatbot.bot.mapper;

import com.wxn.chatbot.bot.document.BotRun;
import com.wxn.chatbot.bot.dto.BotDtos;
import com.wxn.chatbot.bot.model.BotRunOutcome;
import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.whatsapp.model.MessageType;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import java.time.Instant;
import java.util.List;
import org.bson.types.ObjectId;

public final class BotMapper {

    // Limite de texto por mensagem aceito pelo contrato do serviço Python.
    private static final int MAX_HISTORY_TEXT = 4096;

    private BotMapper() {}

    public static BotDtos.ReplyRequest request(
        NormalizedMessage inbound,
        String text,
        List<NormalizedMessage> recent,
        List<DepartmentDtos.Response> departments
    ) {
        return new BotDtos.ReplyRequest(
            inbound.conversationId(),
            inbound.channelId(),
            inbound.id(),
            text,
            history(inbound, recent),
            departments
                .stream()
                .filter(department -> department.status() == ActiveStatus.ACTIVE)
                .map(department ->
                    new BotDtos.Department(department.id(), department.name(), department.description())
                )
                .toList()
        );
    }

    // Mensagens de texto entregues antes da atual, em ordem cronológica; o serviço Python corta pelo limite de tokens.
    private static List<BotDtos.HistoryMessage> history(
        NormalizedMessage inbound,
        List<NormalizedMessage> recent
    ) {
        return recent
            .stream()
            .filter(message -> !message.id().equals(inbound.id()))
            .filter(
                message -> inbound.createdAt() == null || !message.createdAt().isAfter(inbound.createdAt())
            )
            .filter(message -> message.messageType() == MessageType.TEXT && message.deletedAt() == null)
            .filter(message -> message.status() != NormalizedMessageStatus.FAILED)
            .map(message -> {
                Object text = message.content() == null ? null : message.content().get("text");
                if (!(text instanceof String value) || value.isBlank()) {
                    return null;
                }
                return new BotDtos.HistoryMessage(
                    message.direction().name(),
                    value.length() > MAX_HISTORY_TEXT ? value.substring(0, MAX_HISTORY_TEXT) : value,
                    message.createdAt()
                );
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public static BotRun run(
        NormalizedMessage inbound,
        BotRunOutcome outcome,
        BotDtos.ReplyResponse response,
        String replyMessageId,
        long totalLatencyMs,
        String errorCode
    ) {
        BotDtos.Run run = response == null ? null : response.run();
        return new BotRun(
            null,
            new ObjectId(inbound.conversationId()),
            new ObjectId(inbound.id()),
            inbound.channelId(),
            outcome.name(),
            replyMessageId,
            response == null ? null : response.intent(),
            response == null ? null : response.needsHuman(),
            response == null ? null : response.departmentId(),
            response == null ? null : response.handoffReason(),
            run == null ? null : run.model(),
            run == null ? null : run.requestId(),
            run == null ? null : run.inputTokens(),
            run == null ? null : run.outputTokens(),
            run == null ? null : run.cachedInputTokens(),
            run == null ? null : run.cacheWriteInputTokens(),
            run == null ? null : run.historyMessages(),
            run == null ? null : run.fallbackStage(),
            run == null ? null : run.guardrails(),
            run == null ? null : run.failures(),
            run == null ? null : run.toolCalls(),
            run == null ? null : run.retrieved(),
            run == null ? null : run.latencyMs(),
            totalLatencyMs,
            errorCode,
            Instant.now()
        );
    }
}
