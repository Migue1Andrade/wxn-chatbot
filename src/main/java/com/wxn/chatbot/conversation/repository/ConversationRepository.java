package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.Conversation;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository
    extends MongoRepository<Conversation, ObjectId>, ConversationOperations
{
    Optional<Conversation> findByIdAndCompanyId(ObjectId id, Long companyId);
    Optional<Conversation> findByCompanyIdAndChannelIdAndConversationKey(
        Long companyId,
        Long channelId,
        String key
    );
    List<Conversation> findByCompanyIdOrderByUpdatedAtDesc(
        Long companyId,
        org.springframework.data.domain.Pageable pageable
    );
}
