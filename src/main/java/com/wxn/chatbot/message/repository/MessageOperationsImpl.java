package com.wxn.chatbot.message.repository;

import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.model.MessageCursor;
import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;

/** Custom Spring Data operations for atomic updates; basic CRUD stays in MongoRepository. */
public class MessageOperationsImpl implements MessageOperations {

    private final MongoTemplate mongo;

    public MessageOperationsImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void reconciliationChecked(Message message) {
        mongo.updateFirst(
            scope(message),
            new Update().set("lastReconciledAt", OffsetDateTime.now()),
            Message.class
        );
    }

    public List<Message> before(org.bson.types.ObjectId conversationId, MessageCursor cursor, int limit) {
        Query query = Query.query(Criteria.where("conversationId").is(conversationId));
        if (cursor != null) {
            var equalTime = cursor.timestamp().toInstant().equals(Instant.EPOCH)
                ? new Criteria().orOperator(
                      Criteria.where("createdAt").is(cursor.timestamp()),
                      Criteria.where("createdAt").is(null)
                  )
                : Criteria.where("createdAt").is(cursor.timestamp());
            query.addCriteria(
                new Criteria().orOperator(
                    Criteria.where("createdAt").lt(cursor.timestamp()),
                    new Criteria().andOperator(equalTime, Criteria.where("_id").lt(cursor.id()))
                )
            );
        }
        return mongo.find(
            query
                .with(
                    org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.desc("_id")
                    )
                )
                .limit(limit),
            Message.class
        );
    }

    public boolean completeInbound(Message message) {
        return (
            mongo
                .updateFirst(
                    scope(message).addCriteria(Criteria.where("inboundCompleted").is(false)),
                    new Update().set("inboundCompleted", true),
                    Message.class
                )
                .getModifiedCount() == 1
        );
    }

    public void associateExternalId(Message message, String externalId, boolean simulated) {
        mongo.updateFirst(
            scope(message),
            new Update().inc("version", 1).set("externalMessageId", externalId).set("simulated", simulated),
            Message.class
        );
    }

    public Optional<Message> updateStatus(
        Message message,
        NormalizedMessageStatus status,
        String errorCode,
        Collection<NormalizedMessageStatus> allowedPrevious
    ) {
        Query query = scope(message).addCriteria(Criteria.where("status").in(allowedPrevious));
        return Optional.ofNullable(
            mongo.findAndModify(
                query,
                new Update()
                    .inc("version", 1)
                    .set("status", status)
                    .set("errorCode", errorCode)
                    .set("updatedAt", OffsetDateTime.now()),
                FindAndModifyOptions.options().returnNew(true),
                Message.class
            )
        );
    }

    public void providerIds(Message message, String trackingId, String responseId) {
        mongo.updateFirst(
            scope(message),
            new Update()
                .inc("version", 1)
                .set("providerTrackingId", trackingId)
                .set("providerResponseId", responseId),
            Message.class
        );
    }

    public boolean claimDeletion(Message message, String operationId, String intent, Long userId) {
        return (
            mongo
                .updateFirst(
                    scope(message).addCriteria(
                        Criteria.where("deleteState")
                            .in(java.util.Arrays.asList(null, "FAILED"))
                            .and("pendingMutation.state")
                            .nin("PENDING", "ACCEPTED", "UNKNOWN")
                    ),
                    new Update()
                        .inc("version", 1)
                        .set("deleteState", "PENDING")
                        .set("deleteOperationId", operationId)
                        .set("deletionIntent", intent)
                        .set("deletedByUserId", userId),
                    Message.class
                )
                .getModifiedCount() == 1
        );
    }

    public Message finishDeletion(Message message, String state) {
        Update update = new Update()
            .inc("version", 1)
            .set("deleteState", state)
            .set("updatedAt", OffsetDateTime.now());
        if ("CONFIRMED".equals(state)) {
            update.set("deletedAt", OffsetDateTime.now());
        }
        return mongo.findAndModify(
            scope(message),
            update,
            FindAndModifyOptions.options().returnNew(true),
            Message.class
        );
    }

    public Optional<Message> applyRemoteEdit(Message message, String value, Instant at) {
        if (value == null || value.isBlank() || message.getDeletedAt() != null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(message)
                    .addCriteria(Criteria.where("deletedAt").is(null))
                    .addCriteria(
                        new Criteria().orOperator(
                            Criteria.where("editedAt").lt(at.atOffset(java.time.ZoneOffset.UTC)),
                            Criteria.where("editedAt").is(null)
                        )
                    ),
                new Update()
                    .inc("version", 1)
                    .set("content.text", value)
                    .set("editedAt", at.atOffset(java.time.ZoneOffset.UTC))
                    .set("updatedAt", OffsetDateTime.now())
                    .push(
                        "editHistory",
                        new org.bson.Document("previousContent", message.getContent()).append(
                            "at",
                            java.util.Date.from(at)
                        )
                    ),
                FindAndModifyOptions.options().returnNew(true),
                Message.class
            )
        );
    }

    public Optional<Message> confirmPendingEdit(Message message, String value) {
        String operationId =
            message.getPendingMutation() == null
                ? null
                : message.getPendingMutation().getString("operationId");
        if (operationId == null) {
            return Optional.empty();
        }
        var confirmed = mongo.findAndModify(
            scope(message)
                .addCriteria(Criteria.where("pendingMutation.operation").is("EDIT_TEXT"))
                .addCriteria(Criteria.where("pendingMutation.value").is(value))
                .addCriteria(Criteria.where("pendingMutation.state").ne("CONFIRMED")),
            new Update()
                .inc("version", 1)
                .set("pendingMutation.state", "CONFIRMED")
                .set("content.text", value)
                .set("editedAt", java.time.OffsetDateTime.now())
                .set("updatedAt", java.time.OffsetDateTime.now()),
            FindAndModifyOptions.options().returnNew(true),
            Message.class
        );
        if (confirmed != null) {
            mongo.updateFirst(
                Query.query(
                    Criteria.where("channelId").is(message.getChannelId()).and("operationId").is(operationId)
                ),
                new Update().set("outcome", "CONFIRMED").set("updatedAt", new java.util.Date()),
                "message_operations"
            );
        }
        return Optional.ofNullable(confirmed);
    }

    public Optional<Message> applyRemoteDeletion(Message message, Instant at) {
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(message).addCriteria(Criteria.where("deletedAt").is(null)),
                new Update()
                    .inc("version", 1)
                    .set("deleteState", "CONFIRMED")
                    .set("deletionIntent", "DELETE_FOR_EVERYONE")
                    .set("deletedAt", at.atOffset(java.time.ZoneOffset.UTC))
                    .set("updatedAt", OffsetDateTime.now()),
                FindAndModifyOptions.options().returnNew(true),
                Message.class
            )
        );
    }

    private Query scope(Message message) {
        return Query.query(
            Criteria.where("_id")
                .is(message.getId())

                .and("channelId")
                .is(message.getChannelId())
        );
    }
}
