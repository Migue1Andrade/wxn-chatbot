package com.wxn.chatbot.whatsapp.operations;

import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelOperationsRepository {

    private final MongoTemplate mongo;

    public ChannelOperationsRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public java.util.List<Long> assignedMembers(Long id) {
        var members = new java.util.ArrayList<Long>();
        mongo
            .getCollection("conversations")
            .distinct(
                "assignedMemberId",
                new Document("channelId", id)
                    .append("assignedMemberId", new Document("$ne", null))
                    .append("status", new Document("$nin", java.util.List.of("CLOSED", "RESOLVED"))),
                Long.class
            )
            .forEach(members::add);
        return members;
    }

    public Document runtime(Long id) {
        var row = mongo.getCollection("channel_runtime").find(new Document("_id", id)).first();
        return row == null ? new Document() : row;
    }

    public void observe(Long id, String state, String kind) {
        Document update = new Document("lastCallbackAt", Date.from(Instant.now())).append(
            "lastCallbackKind",
            kind
        );
        if (state != null) {
            update.append("connectionState", state);
        }
        mongo
            .getCollection("channel_runtime")
            .updateOne(
                new Document("_id", id),
                new Document("$set", update),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
            );
    }

    public void remoteRead(Long id, boolean enabled) {
        mongo
            .getCollection("channel_runtime")
            .updateOne(
                new Document("_id", id),
                new Document("$set", new Document("remoteReadEnabled", enabled)),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
            );
    }

    public long count(String collection, Document filter) {
        return mongo.getCollection(collection).countDocuments(filter);
    }

    public Document latestError(Long id) {
        return mongo
            .getCollection("messages")
            .find(new Document("channelId", id).append("status", "FAILED"))
            .projection(new Document("errorCode", 1).append("updatedAt", 1))
            .sort(new Document("updatedAt", -1))
            .first();
    }

    public List<Document> unresolved(Long id) {
        List<Document> rows = new ArrayList<>();
        mongo
            .getCollection("messages")
            .find(
                new Document("channelId", id).append(
                    "status",
                    new Document("$in", List.of("UNKNOWN", "PENDING"))
                )
            )
            .projection(
                new Document("_id", 1).append("status", 1).append("errorCode", 1).append("createdAt", 1)
            )
            .sort(new Document("createdAt", -1))
            .limit(100)
            .forEach(rows::add);
        rows.forEach(row -> row.put("id", row.remove("_id").toString()));
        return rows;
    }

    public List<Document> pendingMutations(Long id) {
        var rows = new ArrayList<Document>();
        mongo
            .getCollection("message_operations")
            .find(
                new Document("channelId", id).append(
                    "outcome",
                    new Document("$in", List.of("PENDING", "ACCEPTED", "UNKNOWN"))
                )
            )
            .projection(
                new Document("operation", 1)
                    .append("outcome", 1)
                    .append("createdAt", 1)
                    .append("messageId", 1)
            )
            .sort(new Document("createdAt", -1))
            .limit(50)
            .forEach(row -> {
                row.put("id", row.remove("_id").toString());
                row.put("messageId", row.get("messageId").toString());
                rows.add(row);
            });
        return rows;
    }

    public Document audit(Long id, Long actor, String operation, String operationId) {
        Document query = new Document("channelId", id).append("operationId", operationId);
        var existing = mongo.getCollection("channel_operations").find(query).first();
        if (existing != null) {
            return existing;
        }
        Document row = new Document(query)
            .append("actorUserId", actor)
            .append("operation", operation)
            .append("outcome", "PENDING")
            .append("createdAt", Date.from(Instant.now()));
        try {
            mongo.getCollection("channel_operations").insertOne(row);
        } catch (com.mongodb.MongoWriteException duplicate) {
            if (duplicate.getError().getCode() != 11000) {
                throw duplicate;
            }
            return mongo.getCollection("channel_operations").find(query).first();
        }
        return null;
    }

    public void outcome(Long id, String operationId, String outcome) {
        mongo
            .getCollection("channel_operations")
            .updateOne(
                new Document("channelId", id).append("operationId", operationId),
                new Document(
                    "$set",
                    new Document("outcome", outcome).append("completedAt", Date.from(Instant.now()))
                )
            );
    }
}
