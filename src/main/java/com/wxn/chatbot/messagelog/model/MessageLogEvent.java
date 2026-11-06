package com.wxn.chatbot.messagelog.model;

public enum MessageLogEvent {
    DELETE_ATTEMPTED("message.delete.attempted"),
    DELETE_CONFIRMED("message.delete.confirmed"),
    DELETE_FAILED("message.delete.failed"),
    DELETE_UNKNOWN("message.delete.unknown"),
    RECEIVED("message.received"),
    SEND_ACCEPTED("message.send.accepted"),
    SEND_SIMULATED("message.send.simulated"),
    SEND_ATTEMPTED("message.send.attempted"),
    SENT("message.sent"),
    SEND_UNKNOWN("message.send.unknown"),
    SEND_FAILED("message.send.failed");

    private final String value;

    MessageLogEvent(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
