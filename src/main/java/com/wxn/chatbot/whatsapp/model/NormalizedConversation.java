package com.wxn.chatbot.whatsapp.model;

import com.wxn.chatbot.conversation.event.CanonicalEventPayload;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import java.time.Instant;
import java.util.List;

public record NormalizedConversation(
    String id,
    Long channelId,
    ProviderType provider,
    String externalChatId,
    ConversationType conversationType,
    String contactPhone,
    List<String> participants,
    ConversationStatus status,
    Long assignedMemberId,
    Instant createdAt,
    Instant updatedAt,
    String lastMessagePreview,
    Instant lastMessageAt,
    Instant closedAt,
    long version,
    Long unreadCount,
    String displayName,
    String contactId
) implements CanonicalEventPayload {
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String conversationId() {
        return id;
    }
}
