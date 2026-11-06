package com.wxn.chatbot.whatsapp.model;

public record DeleteMessageRequest(
    String externalMessageId,
    String phone,
    boolean owner,
    DeleteIntent intent
) {
    public enum DeleteIntent {
        DELETE_FOR_EVERYONE,
        DELETE_FOR_ME,
    }
}
