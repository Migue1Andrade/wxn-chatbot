package com.wxn.chatbot.whatsapp.model;

public record SendMessageResult(
    String externalMessageId,
    NormalizedMessageStatus status,
    boolean simulated,
    String providerTrackingId,
    String providerResponseId
) {
    public SendMessageResult(String externalMessageId, NormalizedMessageStatus status, boolean simulated) {
        this(externalMessageId, status, simulated, null, null);
    }
}
