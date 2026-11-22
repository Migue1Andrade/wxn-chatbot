package com.wxn.chatbot.bot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Contrato HTTP com o serviço Python do bot (ver bot/README.md). */
public final class BotDtos {

    private BotDtos() {}

    public record Department(Long id, String name, String description) {}

    // Data em ISO-8601, como o contrato pede, independentemente da configuração do ObjectMapper.
    public record HistoryMessage(
        String direction,
        String text,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant sentAt
    ) {}

    public record ReplyRequest(
        String conversationId,
        Long channelId,
        String inboundMessageId,
        String message,
        List<HistoryMessage> history,
        List<Department> departments
    ) {}

    public record Run(
        String model,
        int inputTokens,
        int outputTokens,
        int cachedInputTokens,
        int cacheWriteInputTokens,
        int historyMessages,
        long latencyMs,
        String requestId,
        String fallbackStage,
        List<String> guardrails,
        List<String> failures,
        List<String> toolCalls,
        List<String> retrieved
    ) {}

    public record ReplyResponse(
        String reply,
        String intent,
        boolean needsHuman,
        Long departmentId,
        String handoffReason,
        Run run
    ) {}
}
