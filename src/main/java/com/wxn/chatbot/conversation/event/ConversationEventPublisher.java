package com.wxn.chatbot.conversation.event;

public interface ConversationEventPublisher {
    void publish(String event, Long companyId, CanonicalEventPayload payload);

    default void publish(String event, Long companyId, Object payload) {
        if (!(payload instanceof CanonicalEventPayload canonical)) throw new IllegalArgumentException(
            "Evento exige payload canônico"
        );
        publish(event, companyId, canonical);
    }
}
