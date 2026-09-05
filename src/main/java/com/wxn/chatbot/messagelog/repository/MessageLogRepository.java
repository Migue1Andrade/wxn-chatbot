package com.wxn.chatbot.messagelog.repository;

import com.wxn.chatbot.messagelog.document.MessageLog;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageLogRepository extends MongoRepository<MessageLog, ObjectId> {
}
