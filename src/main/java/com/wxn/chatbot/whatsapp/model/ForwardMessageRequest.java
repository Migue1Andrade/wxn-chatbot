package com.wxn.chatbot.whatsapp.model;

public record ForwardMessageRequest(
    SendMessageRequest destination,
    String externalMessageId,
    String sourcePhone
) {}
