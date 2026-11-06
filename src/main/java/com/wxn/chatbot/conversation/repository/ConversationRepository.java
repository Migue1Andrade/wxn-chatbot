package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.Conversation;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository
    extends MongoRepository<Conversation, ObjectId>, ConversationOperations
{
    @org.springframework.data.mongodb.repository.Query("{'pendingActivities.0':{'$exists':true}}")
    java.util.List<Conversation> pendingNotifications(org.springframework.data.domain.Pageable pageable);

    boolean existsByDepartmentId(Long departmentId);
    boolean existsByDepartmentIdAndStatusNotIn(Long departmentId, java.util.Collection<String> statuses);
    Optional<Conversation> findByChannelIdAndConversationKey(Long channelId, String key);
}
