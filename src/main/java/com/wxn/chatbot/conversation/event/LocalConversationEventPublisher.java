package com.wxn.chatbot.conversation.event;

import java.time.Instant;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class LocalConversationEventPublisher implements ConversationEventPublisher {

    private final ApplicationEventPublisher publisher;

    public LocalConversationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(String type, Long companyId, CanonicalEventPayload payload) {
        if (!ConversationEventTypes.ALL.contains(type)) throw new IllegalArgumentException(
            "Tipo de evento inválido"
        );
        if (!Objects.equals(companyId, payload.companyId())) throw new IllegalArgumentException(
            "Tenant do evento inválido"
        );
        publisher.publishEvent(
            new ConversationEvent(
                UUID.randomUUID(),
                type,
                companyId,
                payload.conversationId(),
                Instant.now(),
                payload
            )
        );
    }
}
