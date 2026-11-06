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
    String senderDisplayName,
    String replyToExternalMessageId,
    String externalContactIdentity
) {
    public NormalizedInboundMessage(
        String externalMessageId,
        String senderPhone,
        String recipientPhone,
        MessageType messageType,
        Map<String, Object> content,
        Instant occurredAt,
        String senderDisplayName,
        String replyToExternalMessageId
    ) {
        this(
            externalMessageId,
            senderPhone,
            recipientPhone,
            messageType,
            content,
            occurredAt,
            senderDisplayName,
            replyToExternalMessageId,
            null
        );
    }

    public NormalizedInboundMessage(
        String externalMessageId,
        String senderPhone,
        String recipientPhone,
        MessageType messageType,
        Map<String, Object> content,
        Instant occurredAt,
        String senderDisplayName
    ) {
        this(
            externalMessageId,
            senderPhone,
            recipientPhone,
            messageType,
            content,
            occurredAt,
            senderDisplayName,
            null
        );
    }

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
