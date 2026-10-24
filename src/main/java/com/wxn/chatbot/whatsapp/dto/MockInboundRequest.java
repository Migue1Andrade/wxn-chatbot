package com.wxn.chatbot.whatsapp.dto;

import jakarta.validation.constraints.*;

public record MockInboundRequest(
    @NotNull @Positive Long companyId,
    @NotNull @Positive Long channelId,
    @NotBlank @Size(max = 255) String externalMessageId,
    @NotBlank @Size(max = 40) String senderPhone,
    @NotBlank @Size(max = 1600) String content,
    @Size(max = 200) String senderName
) {
    public MockInboundRequest(
        Long companyId,
        Long channelId,
        String externalMessageId,
        String senderPhone,
        String content
    ) {
        this(companyId, channelId, externalMessageId, senderPhone, content, null);
    }
}
