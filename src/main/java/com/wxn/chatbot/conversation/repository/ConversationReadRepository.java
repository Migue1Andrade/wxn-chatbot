package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.ConversationRead;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationReadRepository
    extends MongoRepository<ConversationRead, ObjectId>, ConversationReadOperations
{
    List<ConversationRead> findByMemberIdAndConversationIdIn(Long memberId, List<ObjectId> ids);
}
