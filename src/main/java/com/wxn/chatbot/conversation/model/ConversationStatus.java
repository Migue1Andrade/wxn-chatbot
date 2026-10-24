package com.wxn.chatbot.conversation.model;

public enum ConversationStatus {
    PENDING,
    IN_PROGRESS,
    CLOSED;

    public static ConversationStatus effective(String stored, Long assignedMemberId) {
        if ("CLOSED".equals(stored)) return CLOSED;
        return assignedMemberId == null ? PENDING : IN_PROGRESS;
    }
}
