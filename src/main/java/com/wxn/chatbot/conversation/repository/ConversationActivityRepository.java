package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.ConversationActivity;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationActivityRepository extends MongoRepository<ConversationActivity, ObjectId> {
    List<ConversationActivity> findByConversationIdOrderByCreatedAtDescIdDesc(
        ObjectId conversationId,
        Pageable pageable
    );
    Optional<ConversationActivity> findByEventKey(String eventKey);
    List<ConversationActivity> findByConversationIdOrderByCreatedAtAscIdAsc(
        ObjectId conversationId,
        Pageable pageable
    );
}
