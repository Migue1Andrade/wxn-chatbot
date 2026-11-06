package com.wxn.chatbot.message.repository;

import com.wxn.chatbot.message.document.Message;
import java.time.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class MessageMutationRepository {

    private final MongoTemplate mongo;

    public MessageMutationRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public Document operation(Long channel, String key) {
        return mongo.findOne(
            Query.query(Criteria.where("channelId").is(channel).and("operationId").is(key)),
            Document.class,
            "message_operations"
        );
    }

    public void intent(Message message, String operation, String key, String value, Long actor) {
        mongo.insert(
            new Document("channelId", message.getChannelId())
                .append("messageId", message.getId())
                .append("operation", operation)
                .append("operationId", key)
                .append("value", value)
                .append("actorUserId", actor)
                .append("previousContent", message.getContent())
                .append("outcome", "PENDING")
                .append("createdAt", new java.util.Date()),
            "message_operations"
        );
    }

    public boolean claim(Message message, String operation, String key, String value) {
        return (
            mongo
                .updateFirst(
                    Query.query(
                        Criteria.where("_id")
                            .is(message.getId())
                            .and("deletedAt")
                            .is(null)
                            .and("deleteState")
                            .is(null)
                            .and("pendingMutation.state")
                            .nin("PENDING", "ACCEPTED", "UNKNOWN")
                    ),
                    new Update()
                        .inc("version", 1)
                        .set(
                            "pendingMutation",
                            new Document("operation", operation)
                                .append("operationId", key)
                                .append("value", value)
                                .append("state", "PENDING")
                                .append("requestedAt", new java.util.Date())
                        ),
                    Message.class
                )
                .getModifiedCount() == 1
        );
    }

    public Message finish(Message message, String operation, String key, String value, String state) {
        mongo.updateFirst(
            Query.query(
                Criteria.where("channelId")
                    .is(message.getChannelId())
                    .and("operationId")
                    .is(key)
                    .and("outcome")
                    .ne("CONFIRMED")
            ),
            new Update().set("outcome", state).set("updatedAt", new java.util.Date()),
            "message_operations"
        );
        Update update = new Update()
            .inc("version", 1)
            .set("pendingMutation.state", state)
            .set("updatedAt", OffsetDateTime.now());
        if ("CONFIRMED".equals(state)) {
            if ("EDIT_TEXT".equals(operation)) {
                update.set("content.text", value).set("editedAt", OffsetDateTime.now());
            }
            if ("SEND_REACTION".equals(operation)) {
                update.set("reaction", value);
            }
            if ("REMOVE_REACTION".equals(operation)) {
                update.unset("reaction");
            }
            if ("MARK_READ".equals(operation)) {
                update.set("remoteReadAt", OffsetDateTime.now());
            }
        }
        var updated = mongo.findAndModify(
            Query.query(
                Criteria.where("_id")
                    .is(message.getId())
                    .and("pendingMutation.operationId")
                    .is(key)
                    .and("pendingMutation.state")
                    .ne("CONFIRMED")
            ),
            update,
            FindAndModifyOptions.options().returnNew(true),
            Message.class
        );
        return updated == null ? mongo.findById(message.getId(), Message.class) : updated;
    }

    public Message observe(Message message, String operation, String value, boolean own, Instant at) {
        String timestamp = "EDIT_TEXT".equals(operation)
            ? "editedAt"
            : own
              ? "reactionAt"
              : "contactReactionAt";
        Update update = new Update()
            .inc("version", 1)
            .set(timestamp, at.atOffset(ZoneOffset.UTC))
            .set("updatedAt", OffsetDateTime.now());
        if ("EDIT_TEXT".equals(operation)) {
            update
                .set("content.text", value)
                .push(
                    "editHistory",
                    new Document("previousContent", message.getContent()).append(
                        "at",
                        java.util.Date.from(at)
                    )
                );
        } else {
            update.set(own ? "reaction" : "contactReaction", value);
        }
        return mongo.findAndModify(
            Query.query(Criteria.where("_id").is(message.getId()).and("deletedAt").is(null)).addCriteria(
                new Criteria().orOperator(
                    Criteria.where(timestamp).lt(at.atOffset(ZoneOffset.UTC)),
                    Criteria.where(timestamp).is(null)
                )
            ),
            update,
            FindAndModifyOptions.options().returnNew(true),
            Message.class
        );
    }
}
