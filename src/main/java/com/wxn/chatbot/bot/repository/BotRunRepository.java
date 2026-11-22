package com.wxn.chatbot.bot.repository;

import com.wxn.chatbot.bot.document.BotRun;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BotRunRepository extends MongoRepository<BotRun, ObjectId> {}
