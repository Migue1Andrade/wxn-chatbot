package com.wxn.chatbot.whatsapp.model;

import java.time.Instant;
import java.util.Map;

public record NormalizedInboundMessage(
    String externalMessageId,
    String senderPhone,
    String recipientPhone,
    MessageType messageType,
    Map<String, Object> content,
    Instant occurredAt,
    String senderDisplayName
) {
    public NormalizedInboundMessage(
        String externalMessageId,
        String senderPhone,
        String recipientPhone,
        MessageType messageType,
        Map<String, Object> content,
        Instant occurredAt
    ) {
        this(externalMessageId, senderPhone, recipientPhone, messageType, content, occurredAt, null);
    }
}
