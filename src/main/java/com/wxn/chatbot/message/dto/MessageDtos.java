package com.wxn.chatbot.message.dto;

import jakarta.validation.constraints.*;

public final class MessageDtos {

    private MessageDtos() {}

    public record Send(
        @NotNull @Positive Long channelId,
        @Positive Long memberId,
        @NotBlank @Size(max = 1600) String content,
        @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId,
        Long actorUserId
    ) {
        public Send(Long channelId, Long memberId, String content, String operationId) {
            this(channelId, memberId, content, operationId, null);
        }

        public Send(Long channelId, Long memberId, String content) {
            this(channelId, memberId, content, null, null);
        }
    }

    public record Text(
        @NotBlank @Size(max = 1600) String content,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId
    ) {}

    public record Forward(
        @NotBlank String destinationConversationId,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId
    ) {}
}
