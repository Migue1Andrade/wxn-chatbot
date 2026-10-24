package com.wxn.chatbot.whatsapp.model;

public record SendMessageResult(
    String externalMessageId,
    NormalizedMessageStatus status,
    boolean simulated
) {}
