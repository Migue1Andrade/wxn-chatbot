package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.ConversationRead;
import com.wxn.chatbot.message.model.MessageCursor;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;

public class ConversationReadOperationsImpl implements ConversationReadOperations {

    private final MongoTemplate mongo;

    public ConversationReadOperationsImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Optional<ConversationRead> advance(ObjectId conversationId, Long memberId, MessageCursor cursor) {
        Query query = Query.query(
            Criteria.where("conversationId").is(conversationId).and("memberId").is(memberId)
        ).addCriteria(
            new Criteria().orOperator(
                Criteria.where("lastReadAt").lt(cursor.timestamp()),
                Criteria.where("lastReadAt").is(null),
                new Criteria().andOperator(
                    Criteria.where("lastReadAt").is(cursor.timestamp()),
                    Criteria.where("throughMessageId").lt(cursor.id())
                )
            )
        );
        try {
            return Optional.ofNullable(
                mongo.findAndModify(
                    query,
                    new Update()
                        .set("lastReadAt", cursor.timestamp())
                        .set("throughMessageId", cursor.id())
                        .setOnInsert("conversationId", conversationId)
                        .setOnInsert("memberId", memberId),
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    ConversationRead.class
                )
            );
        } catch (DuplicateKeyException alreadyAdvanced) {
            return Optional.empty();
        }
    }

    public Map<ObjectId, Long> unreadCounts(List<ObjectId> ids, List<ConversationRead> reads) {
        Map<ObjectId, ConversationRead> map = new HashMap<>();
        reads.forEach(r -> map.put(r.getConversationId(), r));
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Document> conditions = new ArrayList<>();
        for (ObjectId id : ids) {
            var read = map.get(id);
            Document condition = new Document("conversationId", id);
            if (read != null && read.getLastReadAt() != null) {
                var after = new ArrayList<Document>();
                ObjectId boundary =
                    read.getThroughMessageId() == null
                        ? new ObjectId("000000000000000000000000")
                        : read.getThroughMessageId();
                after.add(
                    new Document(
                        "createdAt",
                        new Document("$gt", Date.from(read.getLastReadAt().toInstant()))
                    )
                );
                after.add(
                    new Document("createdAt", Date.from(read.getLastReadAt().toInstant())).append(
                        "_id",
                        new Document("$gt", boundary)
                    )
                );
                if (read.getLastReadAt().toInstant().equals(Instant.EPOCH)) {
                    after.add(new Document("createdAt", null).append("_id", new Document("$gt", boundary)));
                }
                condition.append("$or", after);
            }
            conditions.add(condition);
        }
        var match = new Document("direction", new Document("$in", List.of("INBOUND", "inbound"))).append(
            "$or",
            conditions
        );
        Map<ObjectId, Long> counts = new HashMap<>();
        mongo
            .getCollection("messages")
            .aggregate(
                List.of(
                    new Document("$match", match),
                    new Document(
                        "$group",
                        new Document("_id", "$conversationId").append("count", new Document("$sum", 1))
                    )
                )
            )
            .forEach(row -> counts.put(row.getObjectId("_id"), row.get("count", Number.class).longValue()));
        return counts;
    }
}
