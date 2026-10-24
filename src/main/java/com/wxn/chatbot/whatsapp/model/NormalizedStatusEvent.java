package com.wxn.chatbot.whatsapp.model;

public record NormalizedStatusEvent(
    String externalMessageId,
    NormalizedMessageStatus status,
    String errorCode
) {}
