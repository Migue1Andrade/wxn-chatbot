package com.wxn.chatbot.conversation.dto;

import com.wxn.chatbot.conversation.event.CanonicalEventPayload;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ConversationManagementDtos {

    private ConversationManagementDtos() {}

    public record Assign(
        @NotNull @Positive Long memberId,
        @NotNull @Positive Long actorMemberId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 500) String reason
    ) {}

    public record Action(
        @NotNull @Positive Long actorMemberId,
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 500) String reason
    ) {}

    public record Activity(
        String id,
        Long companyId,
        String conversationId,
        ConversationActivityType event,
        Long actorMemberId,
        Long previousMemberId,
        Long newMemberId,
        String reason,
        Instant createdAt
    ) {}

    public record Read(@NotNull @Positive Long memberId, @NotBlank String throughMessageId) {}

    public record ReadUpdate(
        Long companyId,
        String conversationId,
        Long memberId,
        String throughMessageId,
        Instant lastReadAt,
        long unreadCount
    ) implements CanonicalEventPayload {}
}
