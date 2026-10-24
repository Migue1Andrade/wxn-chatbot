package com.wxn.chatbot.conversation.document;

import com.wxn.chatbot.conversation.model.ConversationActivityType;
import java.time.OffsetDateTime;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("conversation_events")
public class ConversationActivity {

    @Id
    private ObjectId id;

    private Long companyId;
    private ObjectId conversationId;
    private String eventKey;
    private ConversationActivityType event;
    private Long actorMemberId;
    private Long previousMemberId;
    private Long newMemberId;
    private String reason;
    private OffsetDateTime createdAt;

    protected ConversationActivity() {}

    public ConversationActivity(
        Long companyId,
        ObjectId conversationId,
        String eventKey,
        ConversationActivityType event,
        Long actorMemberId,
        Long previousMemberId,
        Long newMemberId,
        String reason,
        OffsetDateTime createdAt
    ) {
        this.id = new ObjectId();
        this.companyId = companyId;
        this.conversationId = conversationId;
        this.eventKey = eventKey;
        this.event = event;
        this.actorMemberId = actorMemberId;
        this.previousMemberId = previousMemberId;
        this.newMemberId = newMemberId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public String getEventKey() {
        return eventKey;
    }

    public ObjectId getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public ObjectId getConversationId() {
        return conversationId;
    }

    public ConversationActivityType getEvent() {
        return event;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public Long getPreviousMemberId() {
        return previousMemberId;
    }

    public Long getNewMemberId() {
        return newMemberId;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
