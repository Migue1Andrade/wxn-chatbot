package com.wxn.chatbot.message.repository;

import com.wxn.chatbot.message.document.Message;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, ObjectId>, MessageOperations {
    Optional<Message> findByIdAndCompanyId(ObjectId id, Long companyId);
    Optional<Message> findByIdAndCompanyIdAndConversationId(
        ObjectId id,
        Long companyId,
        ObjectId conversationId
    );
    Optional<Message> findByCompanyIdAndChannelIdAndExternalMessageId(
        Long companyId,
        Long channelId,
        String externalMessageId
    );
    List<Message> findByCompanyIdAndConversationIdOrderByCreatedAtAsc(
        Long companyId,
        ObjectId conversationId,
        org.springframework.data.domain.Pageable pageable
    );
}
