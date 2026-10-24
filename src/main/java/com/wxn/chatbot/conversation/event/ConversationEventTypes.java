package com.wxn.chatbot.conversation.event;

import java.util.Set;

public final class ConversationEventTypes {

    public static final String CONVERSATION_CREATED = "conversation.created";
    public static final String CONVERSATION_UPDATED = "conversation.updated";
    public static final String MESSAGE_RECEIVED = "message.received";
    public static final String MESSAGE_SENT = "message.sent";
    public static final String MESSAGE_STATUS_UPDATED = "message.status.updated";
    public static final String CONVERSATION_ASSIGNED = "conversation.assigned";
    public static final String CONVERSATION_TRANSFERRED = "conversation.transferred";
    public static final String CONVERSATION_CLOSED = "conversation.closed";
    public static final String CONVERSATION_REOPENED = "conversation.reopened";
    public static final String CONVERSATION_READ_UPDATED = "conversation.read.updated";
    public static final Set<String> ALL = Set.of(
        CONVERSATION_CREATED,
        CONVERSATION_UPDATED,
        MESSAGE_RECEIVED,
        MESSAGE_SENT,
        MESSAGE_STATUS_UPDATED,
        CONVERSATION_ASSIGNED,
        CONVERSATION_TRANSFERRED,
        CONVERSATION_CLOSED,
        CONVERSATION_REOPENED,
        CONVERSATION_READ_UPDATED
    );

    private ConversationEventTypes() {}
}
