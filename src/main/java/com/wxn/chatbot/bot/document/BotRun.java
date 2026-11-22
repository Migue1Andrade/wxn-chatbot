package com.wxn.chatbot.bot.document;

import java.time.Instant;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Registro de uma execução do bot, para observabilidade: decisão, custo, latência e falhas. */
@Document("bot_runs")
public record BotRun(
    @Id ObjectId id,
    ObjectId conversationId,
    ObjectId inboundMessageId,
    Long channelId,
    String outcome,
    String replyMessageId,
    String intent,
    Boolean needsHuman,
    Long departmentId,
    String handoffReason,
    String model,
    String requestId,
    Integer inputTokens,
    Integer outputTokens,
    Integer cachedInputTokens,
    Integer cacheWriteInputTokens,
    Integer historyMessages,
    String fallbackStage,
    List<String> guardrails,
    List<String> failures,
    List<String> toolCalls,
    List<String> retrieved,
    Long botLatencyMs,
    long totalLatencyMs,
    String errorCode,
    Instant createdAt
) {}
