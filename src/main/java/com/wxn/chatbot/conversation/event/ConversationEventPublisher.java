package com.wxn.chatbot.conversation.event;

public interface ConversationEventPublisher {
    void publish(String event, CanonicalEventPayload payload);

    default void publish(String event, Object payload) {
        if (!(payload instanceof CanonicalEventPayload canonical)) {
            throw new IllegalArgumentException("Evento exige payload canônico");
        }
        publish(event, canonical);
    }
}
