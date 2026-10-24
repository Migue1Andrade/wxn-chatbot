package com.wxn.chatbot.whatsapp.dto;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.whatsapp.model.ProviderType;
import jakarta.validation.constraints.*;

public final class ChannelDtos {

    private ChannelDtos() {}

    public record Create(
        @NotBlank @Size(max = 200) String name,
        @NotNull ProviderType provider,
        @Size(max = 40) String phoneNumber,
        @Size(max = 255) String externalChannelId,
        ActiveStatus status
    ) {}

    public record Response(
        Long id,
        Long companyId,
        String name,
        ProviderType provider,
        String phoneNumber,
        String externalChannelId,
        ActiveStatus status
    ) {}
}
