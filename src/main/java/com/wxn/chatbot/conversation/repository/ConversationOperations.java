package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.document.ConversationActivity;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import java.time.OffsetDateTime;
import java.util.*;
import org.bson.types.ObjectId;

public interface ConversationOperations {
    java.util.Optional<com.wxn.chatbot.conversation.document.Conversation> refreshPreview(
        org.bson.types.ObjectId conversation,
        org.bson.types.ObjectId message,
        String preview
    );
    void linkContact(org.bson.types.ObjectId conversation, org.bson.types.ObjectId contact);
    Optional<Conversation> updateDisplayName(ObjectId id, String displayName);
    void recordDirection(ObjectId id, OffsetDateTime at, boolean inbound);

    Optional<Conversation> updateActivity(
        ObjectId id,
        OffsetDateTime messageAt,
        ObjectId messageId,
        String preview
    );
    Optional<Conversation> changeState(
        Conversation current,
        long expectedVersion,
        ConversationStatus status,
        Long memberId,
        OffsetDateTime closedAt,
        ConversationActivity activity
    );
    void acknowledgeActivity(ObjectId id, ObjectId activityId);
    List<Conversation> search(
        ConversationStatus status,
        Long assignedMemberId,
        boolean unassigned,
        int page,
        int size,
        String search,
        boolean probeNext
    );
}
