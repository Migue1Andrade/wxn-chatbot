package com.wxn.chatbot.websocket;

import com.wxn.chatbot.config.DevelopmentAccess;
import com.wxn.chatbot.conversation.event.ConversationEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
public class StompConversationEventBridge {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
        StompConversationEventBridge.class
    );
    private final SimpMessagingTemplate messaging;
    private final DevelopmentAccess access;

    public StompConversationEventBridge(SimpMessagingTemplate messaging, DevelopmentAccess access) {
        this.messaging = messaging;
        this.access = access;
    }

    @EventListener
    public void publish(ConversationEvent event) {
        if (!access.allows(event.companyId())) return;
        try {
            messaging.convertAndSend(StompDestinations.conversations(event.companyId()), event);
        } catch (org.springframework.messaging.MessagingException unavailable) {
            LOGGER.warn(
                "Notificação STOMP indisponível: eventId={}, companyId={}",
                event.eventId(),
                event.companyId()
            );
        }
    }
}
