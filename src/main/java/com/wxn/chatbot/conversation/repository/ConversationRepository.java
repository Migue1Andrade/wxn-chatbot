package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.Conversation;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository extends MongoRepository<Conversation, ObjectId> {
}
