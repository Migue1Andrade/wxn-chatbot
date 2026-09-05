package com.wxn.chatbot.message.repository;

import com.wxn.chatbot.message.document.Message;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, ObjectId> {
}
