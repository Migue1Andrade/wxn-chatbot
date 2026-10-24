package com.wxn.chatbot.whatsapp.model;

import com.wxn.chatbot.conversation.event.CanonicalEventPayload;
import java.time.Instant;
import java.util.Map;

public record NormalizedMessage(
    String id,
    Long companyId,
    Long channelId,
    String conversationId,
    ProviderType provider,
    String externalMessageId,
    Direction direction,
    String senderPhone,
    String recipientPhone,
    MessageType messageType,
    Map<String, Object> content,
    NormalizedMessageStatus status,
    Long memberId,
    boolean simulated,
    String errorCode,
    Instant createdAt,
    Instant updatedAt
) implements CanonicalEventPayload {}
