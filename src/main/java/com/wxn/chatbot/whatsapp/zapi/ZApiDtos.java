package com.wxn.chatbot.whatsapp.zapi;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class ZApiDtos {

    private ZApiDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Text(String phone, String message, String messageId, String editMessageId) {
        public Text(String phone, String message, String messageId) {
            this(phone, message, messageId, null);
        }
    }

    public record Forward(String phone, String messageId, String messagePhone) {}

    public record Sent(String zaapId, String messageId, String id) {}

    public record Value(boolean value) {}

    public record Webhook(String value) {}

    public record Notify(boolean notifySentByMe) {}

    public record Connection(Boolean connected, Boolean smartphoneConnected, String error) {}
}
