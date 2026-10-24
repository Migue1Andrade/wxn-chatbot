package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.document.ConversationActivity;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import java.time.OffsetDateTime;
import java.util.*;
import org.bson.types.ObjectId;

public interface ConversationOperations {
    void initializeRevision(ObjectId id, Long companyId);
    Optional<Conversation> updateDisplayName(ObjectId id, Long companyId, String displayName);
    Optional<Conversation> updateActivity(
        ObjectId id,
        Long companyId,
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
    void acknowledgeActivity(ObjectId id, Long companyId, ObjectId activityId);
    List<Conversation> search(
        Long companyId,
        ConversationStatus status,
        Long assignedMemberId,
        boolean unassigned,
        int page,
        int size,
        String search,
        boolean probeNext
    );
}
