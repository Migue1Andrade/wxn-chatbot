package com.wxn.chatbot.conversation.model;

public enum ConversationStatus {
    PENDING,
    IN_PROGRESS,
    WAITING_CUSTOMER,
    RESOLVED,
    CLOSED;

    public static ConversationStatus effective(String stored, Long assignedMemberId) {
        if ("CLOSED".equals(stored) || "RESOLVED".equals(stored) || "WAITING_CUSTOMER".equals(stored)) {
            return valueOf(stored);
        }
        return assignedMemberId == null ? PENDING : IN_PROGRESS;
    }
}
