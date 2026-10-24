package com.wxn.chatbot.whatsapp.mapper;

import com.wxn.chatbot.whatsapp.dto.ChannelDtos;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;

public final class ChannelMapper {

    private ChannelMapper() {}

    public static ChannelDtos.Response response(WhatsAppChannel c) {
        return new ChannelDtos.Response(
            c.getId(),
            c.getCompanyId(),
            c.getName(),
            c.getProvider(),
            c.getPhoneNumber(),
            c.getExternalChannelId(),
            c.getStatus()
        );
    }
}
