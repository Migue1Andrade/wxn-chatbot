package com.wxn.chatbot.whatsapp.model;

import com.wxn.chatbot.conversation.event.CanonicalEventPayload;
import java.time.Instant;
import java.util.Map;

public record NormalizedMessage(
    String id,
    Long channelId,
    String conversationId,
    ProviderType provider,
    String externalMessageId,
    Direction direction,
    String senderPhone,
    String recipientPhone,
    MessageType messageType,
    Map<String, Object> content,
    NormalizedMessageStatus status,
    Long memberId,
    boolean simulated,
    String errorCode,
    Instant createdAt,
    Instant updatedAt,
    String operationId,
    String replyToMessageId,
    String replyToExternalMessageId,
    String replyPreview,
    String forwardedFromMessageId,
    String origin,
    Instant deletedAt,
    String deletionIntent,
    String deleteState,
    Map<String, Object> pendingMutation,
    String reaction,
    String contactReaction,
    Instant editedAt,
    long version,
    java.util.Set<MessageCapability> capabilities
) implements CanonicalEventPayload {}
