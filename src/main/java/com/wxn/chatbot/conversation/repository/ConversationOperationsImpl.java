package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.*;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;

public class ConversationOperationsImpl implements ConversationOperations {

    private final MongoTemplate mongo;

    public ConversationOperationsImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private Query scope(ObjectId id, Long companyId) {
        return Query.query(Criteria.where("_id").is(id).and("companyId").is(companyId));
    }

    public void initializeRevision(ObjectId id, Long companyId) {
        mongo.updateFirst(
            scope(id, companyId).addCriteria(Criteria.where("version").is(null)),
            new Update().set("version", 0L),
            Conversation.class
        );
    }

    public Optional<Conversation> updateDisplayName(ObjectId id, Long companyId, String displayName) {
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(id, companyId).addCriteria(Criteria.where("displayName").ne(displayName)),
                new Update()
                    .set("displayName", displayName)
                    .max("updatedAt", OffsetDateTime.now())
                    .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public Optional<Conversation> updateActivity(
        ObjectId id,
        Long companyId,
        OffsetDateTime messageAt,
        ObjectId messageId,
        String preview
    ) {
        Criteria newerMessage = new Criteria().orOperator(
            Criteria.where("lastMessageAt").lt(messageAt),
            Criteria.where("lastMessageAt").is(null),
            new Criteria().andOperator(
                Criteria.where("lastMessageAt").is(messageAt),
                new Criteria().orOperator(
                    Criteria.where("lastMessageId").lt(messageId),
                    Criteria.where("lastMessageId").is(null)
                )
            )
        );
        return Optional.ofNullable(
            mongo.findAndModify(
                scope(id, companyId).addCriteria(newerMessage),
                new Update()
                    .max("updatedAt", messageAt)
                    .set("lastMessageAt", messageAt)
                    .set("lastMessageId", messageId)
                    .set("lastMessagePreview", preview)
                    .inc("version", 1),
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public Optional<Conversation> changeState(
        Conversation current,
        long expectedVersion,
        ConversationStatus status,
        Long memberId,
        OffsetDateTime closedAt,
        ConversationActivity activity
    ) {
        Query query = scope(current.getId(), current.getCompanyId());
        query.addCriteria(
            expectedVersion == 0
                ? new Criteria().orOperator(
                      Criteria.where("version").is(0L),
                      Criteria.where("version").is(null)
                  )
                : Criteria.where("version").is(expectedVersion)
        );
        Update update = new Update()
            .set("status", status.name())
            .set("assignedMemberId", memberId)
            .set("closedAt", closedAt)
            .max("updatedAt", activity.getCreatedAt())
            .set("version", expectedVersion + 1)
            .push("pendingActivities", activity);
        return Optional.ofNullable(
            mongo.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Conversation.class
            )
        );
    }

    public void acknowledgeActivity(ObjectId id, Long companyId, ObjectId activityId) {
        mongo.updateFirst(
            scope(id, companyId),
            new Update().pull("pendingActivities", new Document("_id", activityId)),
            Conversation.class
        );
    }

    public List<Conversation> search(
        Long companyId,
        ConversationStatus status,
        Long assignedMemberId,
        boolean unassigned,
        int page,
        int size,
        String search,
        boolean probeNext
    ) {
        List<Criteria> conditions = new ArrayList<>();
        conditions.add(Criteria.where("companyId").is(companyId));
        if (status == ConversationStatus.CLOSED) conditions.add(Criteria.where("status").is(status.name()));
        else if (status != null) {
            conditions.add(Criteria.where("status").ne(ConversationStatus.CLOSED.name()));
            conditions.add(
                status == ConversationStatus.PENDING
                    ? Criteria.where("assignedMemberId").is(null)
                    : Criteria.where("assignedMemberId").ne(null)
            );
        }
        if (assignedMemberId != null) conditions.add(Criteria.where("assignedMemberId").is(assignedMemberId));
        if (unassigned) conditions.add(Criteria.where("assignedMemberId").is(null));
        if (search != null && !search.isBlank()) conditions.add(searchIdentity(search.trim()));
        Query query = Query.query(new Criteria().andOperator(conditions.toArray(Criteria[]::new)));
        return mongo.find(
            query
                .with(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("_id")))
                .skip((long) page * size)
                .limit(size + (probeNext ? 1 : 0)),
            Conversation.class
        );
    }

    private Criteria searchIdentity(String term) {
        List<Criteria> identities = new ArrayList<>();
        identities.add(Criteria.where("displayName").regex(Pattern.quote(term), "i"));
        String phone = term.replaceAll("[ ()-]", "");
        if (!phone.isEmpty()) identities.add(
            Criteria.where("contactPhone").regex(Pattern.quote(phone), "i")
        );
        identities.add(Criteria.where("externalChatId").regex(Pattern.quote(term), "i"));
        if (ObjectId.isValid(term)) identities.add(Criteria.where("_id").is(new ObjectId(term)));
        return new Criteria().orOperator(identities.toArray(Criteria[]::new));
    }
}
