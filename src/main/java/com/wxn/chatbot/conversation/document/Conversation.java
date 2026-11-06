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
    private Long version = 0L;

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

    private String priority;

    public String getPriority() {
        return priority;
    }

    public void setPriority(String value) {
        priority = value;
    }

    private Long departmentId;

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long value) {
        departmentId = value;
    }

    private OffsetDateTime cycleStartedAt;

    public OffsetDateTime getCycleStartedAt() {
        return cycleStartedAt;
    }

    public void setCycleStartedAt(OffsetDateTime value) {
        cycleStartedAt = value;
    }

    private OffsetDateTime resolvedAt;

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime value) {
        resolvedAt = value;
    }

    private OffsetDateTime waitingForAgentSince;

    public OffsetDateTime getWaitingForAgentSince() {
        return waitingForAgentSince;
    }

    public void setWaitingForAgentSince(OffsetDateTime value) {
        waitingForAgentSince = value;
    }

    private OffsetDateTime lastInboundAt;

    public OffsetDateTime getLastInboundAt() {
        return lastInboundAt;
    }

    public void setLastInboundAt(OffsetDateTime value) {
        lastInboundAt = value;
    }

    private OffsetDateTime lastOutboundAt;

    public OffsetDateTime getLastOutboundAt() {
        return lastOutboundAt;
    }

    public void setLastOutboundAt(OffsetDateTime value) {
        lastOutboundAt = value;
    }

    private OffsetDateTime reopenedAt;

    public OffsetDateTime getReopenedAt() {
        return reopenedAt;
    }

    public void setReopenedAt(OffsetDateTime value) {
        reopenedAt = value;
    }

    private Integer reopenCount;

    public Integer getReopenCount() {
        return reopenCount;
    }

    public void setReopenCount(Integer value) {
        reopenCount = value;
    }

    private List<org.bson.Document> resolutionCycles;

    public List<org.bson.Document> getResolutionCycles() {
        return resolutionCycles == null ? List.of() : List.copyOf(resolutionCycles);
    }

    public void setResolutionCycles(List<org.bson.Document> value) {
        resolutionCycles = value;
    }

    public Conversation() {}

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId value) {
        id = value;
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

    private ObjectId contactId;

    public ObjectId getContactId() {
        return contactId;
    }

    public void setContactId(ObjectId value) {
        contactId = value;
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
