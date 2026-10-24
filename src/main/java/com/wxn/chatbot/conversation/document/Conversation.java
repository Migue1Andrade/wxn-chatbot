package com.wxn.chatbot.conversation.document;

import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "conversations")
public class Conversation {

    @Id
    private ObjectId id;

    @Field("companyId")
    private Long companyId;

    @Field("status")
    private String status;

    @Field("createdAt")
    private OffsetDateTime createdAt;

    @Field("updatedAt")
    private OffsetDateTime updatedAt;

    @Field("channelId")
    private Long channelId;

    @Field("provider")
    private ProviderType provider;

    @Field("externalChatId")
    private String externalChatId;

    @Field("conversationKey")
    private String conversationKey;

    @Field("conversationType")
    private ConversationType conversationType;

    @Field("contactPhone")
    private String contactPhone;

    @Field("participants")
    private List<String> participants;

    @Field("assignedMemberId")
    private Long assignedMemberId;

    @Field("lastMessagePreview")
    private String lastMessagePreview;

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public void setLastMessagePreview(String value) {
        lastMessagePreview = value;
    }

    @Field("lastMessageAt")
    private OffsetDateTime lastMessageAt;

    public OffsetDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(OffsetDateTime value) {
        lastMessageAt = value;
    }

    @Field("closedAt")
    private OffsetDateTime closedAt;

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime value) {
        closedAt = value;
    }

    @Field("version")
    private Long version;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        version = value;
    }

    @Field("displayName")
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        displayName = value;
    }

    @Field("pendingActivities")
    private List<ConversationActivity> pendingActivities;

    @Field("lastMessageId")
    private ObjectId lastMessageId;

    public List<ConversationActivity> getPendingActivities() {
        return pendingActivities == null ? List.of() : List.copyOf(pendingActivities);
    }

    public void setPendingActivities(List<ConversationActivity> value) {
        pendingActivities = value;
    }

    public ObjectId getLastMessageId() {
        return lastMessageId;
    }

    public ConversationStatus operationalStatus() {
        return ConversationStatus.effective(status, assignedMemberId);
    }

    public long revision() {
        return version == null ? 0 : version;
    }

    public Conversation() {}

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        status = value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime value) {
        createdAt = value;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime value) {
        updatedAt = value;
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

    public String getExternalChatId() {
        return externalChatId;
    }

    public void setExternalChatId(String value) {
        externalChatId = value;
    }

    public String getConversationKey() {
        return conversationKey;
    }

    public void setConversationKey(String value) {
        conversationKey = value;
    }

    public ConversationType getConversationType() {
        return conversationType;
    }

    public void setConversationType(ConversationType value) {
        conversationType = value;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String value) {
        contactPhone = value;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> value) {
        participants = value;
    }

    public Long getAssignedMemberId() {
        return assignedMemberId;
    }

    public void setAssignedMemberId(Long value) {
        assignedMemberId = value;
    }
}
