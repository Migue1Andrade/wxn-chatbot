package com.wxn.chatbot.conversation.event;

import java.time.Instant;
import java.util.UUID;

public record ConversationEvent(
    UUID eventId,
    String type,
    String conversationId,
    Instant occurredAt,
    CanonicalEventPayload payload
) {}
