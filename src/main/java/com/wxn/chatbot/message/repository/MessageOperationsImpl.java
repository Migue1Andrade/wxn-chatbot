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

    private Query scope(Message message) {
        return Query.query(
            Criteria.where("_id")
                .is(message.getId())

                .and("channelId")
                .is(message.getChannelId())
        );
    }
}
