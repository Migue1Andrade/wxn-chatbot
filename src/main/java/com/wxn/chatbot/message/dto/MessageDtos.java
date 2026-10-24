package com.wxn.chatbot.message.dto;

import jakarta.validation.constraints.*;

public final class MessageDtos {

    private MessageDtos() {}

    public record Send(
        @NotNull @Positive Long channelId,
        @NotNull @Positive Long memberId,
        @NotBlank @Size(max = 1600) String content
    ) {}
}
