package com.wxn.chatbot.message.document;

import com.wxn.chatbot.whatsapp.model.*;
import java.time.OffsetDateTime;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

@org.springframework.data.mongodb.core.mapping.Document(collection = "messages")
public class Message {

    @Id
    private ObjectId id;

    @Field("companyId")
    private Long companyId;

    @Field("conversationId")
    private ObjectId conversationId;

    @Field("direction")
    private String direction;

    @Field("type")
    private String type;

    @Field("content")
    private Document content;

    @Field("createdAt")
    private OffsetDateTime createdAt;

    @Field("channelId")
    private Long channelId;

    @Field("provider")
    private ProviderType provider;

    @Field("externalMessageId")
    private String externalMessageId;

    @Field("senderPhone")
    private String senderPhone;

    @Field("recipientPhone")
    private String recipientPhone;

    @Field("status")
    private NormalizedMessageStatus status;

    @Field("memberId")
    private Long memberId;

    @Field("simulated")
    private boolean simulated;

    @Field("errorCode")
    private String errorCode;

    @Field("updatedAt")
    private OffsetDateTime updatedAt;

    @Field("inboundCompleted")
    private Boolean inboundCompleted;

    public Boolean getInboundCompleted() {
        return inboundCompleted;
    }

    public void setInboundCompleted(Boolean value) {
        inboundCompleted = value;
    }

    public Message() {}

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId value) {
        id = value;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long value) {
        companyId = value;
    }

    public ObjectId getConversationId() {
        return conversationId;
    }

    public void setConversationId(ObjectId value) {
        conversationId = value;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String value) {
        direction = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String value) {
        type = value;
    }

    public Document getContent() {
        return content;
    }

    public void setContent(Document value) {
        content = value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime value) {
        createdAt = value;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long value) {
        channelId = value;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public void setProvider(ProviderType value) {
        provider = value;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String value) {
        externalMessageId = value;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public void setSenderPhone(String value) {
        senderPhone = value;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String value) {
        recipientPhone = value;
    }

    public NormalizedMessageStatus getStatus() {
        return status;
    }

    public void setStatus(NormalizedMessageStatus value) {
        status = value;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long value) {
        memberId = value;
    }

    public boolean getSimulated() {
        return simulated;
    }

    public void setSimulated(boolean value) {
        simulated = value;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String value) {
        errorCode = value;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime value) {
        updatedAt = value;
    }
}
