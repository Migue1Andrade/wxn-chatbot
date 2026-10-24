package com.wxn.chatbot.messagelog.model;

public enum MessageLogEvent {
    RECEIVED("message.received"),
    SEND_ATTEMPTED("message.send.attempted"),
    SENT("message.sent"),
    SEND_FAILED("message.send.failed");

    private final String value;

    MessageLogEvent(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
