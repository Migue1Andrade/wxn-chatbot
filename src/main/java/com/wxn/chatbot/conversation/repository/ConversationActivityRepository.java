package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.ConversationActivity;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import java.time.OffsetDateTime;
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
    boolean existsByConversationIdAndEventAndCreatedAtGreaterThanEqual(
        ObjectId conversationId,
        ConversationActivityType event,
        OffsetDateTime since
    );
    List<ConversationActivity> findByConversationIdOrderByCreatedAtAscIdAsc(
        ObjectId conversationId,
        Pageable pageable
    );
}
