package com.wxn.chatbot.conversation.model;

import java.time.OffsetDateTime;

public record ConversationTransition(
    ConversationStatus status,
    Long memberId,
    OffsetDateTime closedAt,
    ConversationActivityType activity,
    Long actorMemberId,
    String reason,
    String eventType
) {}
