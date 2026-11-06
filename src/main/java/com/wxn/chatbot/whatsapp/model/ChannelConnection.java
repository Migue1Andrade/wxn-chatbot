package com.wxn.chatbot.whatsapp.model;

import java.time.Instant;

public record ChannelConnection(String state, boolean available, Instant checkedAt, String errorCode) {
    public static ChannelConnection of(String state, boolean available, String error) {
        return new ChannelConnection(state, available, Instant.now(), error);
    }
}
