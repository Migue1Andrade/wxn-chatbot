package com.wxn.chatbot.whatsapp.model;

public record SendMessageRequest(
    String internalMessageId,
    Long channelId,
    String recipientPhone,
    String content,
    ConversationType conversationType,
    MessageType messageType
) {}
