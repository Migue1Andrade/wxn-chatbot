package com.wxn.chatbot.conversation.repository;

import java.time.Instant;
import java.util.Date;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationWriteLeaseRepository {

    private final MongoTemplate mongo;

    public ConversationWriteLeaseRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void acquire(String id, String token) {
        mongo.upsert(
            Query.query(Criteria.where("_id").is(id).and("expiresAt").lt(new Date())),
            new Update().set("token", token).set("expiresAt", Date.from(Instant.now().plusSeconds(120))),
            "conversation_write_leases"
        );
    }

    public void release(String id, String token) {
        mongo.remove(
            Query.query(Criteria.where("_id").is(id).and("token").is(token)),
            "conversation_write_leases"
        );
    }
}
