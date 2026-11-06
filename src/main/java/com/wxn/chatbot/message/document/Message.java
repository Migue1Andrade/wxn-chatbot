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

    private long version;

    public long getVersion() {
        return version;
    }

    private String externalContactIdentity;

    public String getExternalContactIdentity() {
        return externalContactIdentity;
    }

    public void setExternalContactIdentity(String value) {
        externalContactIdentity = value;
    }

    private Long actorUserId;

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long value) {
        actorUserId = value;
    }

    private String operationId;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String value) {
        operationId = value;
    }

    private String replyToMessageId;

    public String getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(String value) {
        replyToMessageId = value;
    }

    private String replyToExternalMessageId;

    public String getReplyToExternalMessageId() {
        return replyToExternalMessageId;
    }

    public void setReplyToExternalMessageId(String value) {
        replyToExternalMessageId = value;
    }

    private String replyPreview;

    public String getReplyPreview() {
        return replyPreview;
    }

    public void setReplyPreview(String value) {
        replyPreview = value;
    }

    private String forwardedFromMessageId;

    public String getForwardedFromMessageId() {
        return forwardedFromMessageId;
    }

    public void setForwardedFromMessageId(String value) {
        forwardedFromMessageId = value;
    }

    private String providerTrackingId;

    public String getProviderTrackingId() {
        return providerTrackingId;
    }

    public void setProviderTrackingId(String value) {
        providerTrackingId = value;
    }

    private String providerResponseId;

    public String getProviderResponseId() {
        return providerResponseId;
    }

    public void setProviderResponseId(String value) {
        providerResponseId = value;
    }

    private String origin;

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String value) {
        origin = value;
    }

    private OffsetDateTime deletedAt;

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime value) {
        deletedAt = value;
    }

    private String deletionIntent;

    public String getDeletionIntent() {
        return deletionIntent;
    }

    public void setDeletionIntent(String value) {
        deletionIntent = value;
    }

    private Long deletedByUserId;

    public Long getDeletedByUserId() {
        return deletedByUserId;
    }

    public void setDeletedByUserId(Long value) {
        deletedByUserId = value;
    }

    private String deleteOperationId;

    public String getDeleteOperationId() {
        return deleteOperationId;
    }

    public void setDeleteOperationId(String value) {
        deleteOperationId = value;
    }

    private String deleteState;

    public String getDeleteState() {
        return deleteState;
    }

    public void setDeleteState(String value) {
        deleteState = value;
    }

    public Boolean getInboundCompleted() {
        return inboundCompleted;
    }

    public void setInboundCompleted(Boolean value) {
        inboundCompleted = value;
    }

    private Document pendingMutation;
    private String reaction;
    private String contactReaction;
    private OffsetDateTime editedAt;

    public Document getPendingMutation() {
        return pendingMutation;
    }

    public String getReaction() {
        return reaction;
    }

    public String getContactReaction() {
        return contactReaction;
    }

    public OffsetDateTime getEditedAt() {
        return editedAt;
    }

    public Message() {}

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId value) {
        id = value;
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
