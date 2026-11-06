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
    private final com.wxn.chatbot.conversation.repository.ConversationRepository conversations;

    public StompConversationEventBridge(
        SimpMessagingTemplate messaging,
        DevelopmentAccess access,
        com.wxn.chatbot.conversation.repository.ConversationRepository conversations
    ) {
        this.conversations = conversations;
        this.messaging = messaging;
        this.access = access;
    }

    public record NotificationFrame(java.util.UUID eventId, String type, Object payload) {}

    @EventListener
    public void notification(com.wxn.chatbot.notification.service.NotificationService.Created event) {
        if (access.enabled()) {
            try {
                messaging.convertAndSend(
                    "/topic/users/" + event.userId() + "/notifications",
                    new NotificationFrame(
                        java.util.UUID.randomUUID(),
                        "notification.created",
                        java.util.Map.of("id", event.notificationId())
                    )
                );
            } catch (org.springframework.messaging.MessagingException unavailable) {
                LOGGER.warn("Notificação STOMP indisponível: notificationId={}", event.notificationId());
            }
        }
    }

    @EventListener
    public void connection(com.wxn.chatbot.whatsapp.operations.ChannelOperationsService.Changed event) {
        if (access.enabled()) {
            try {
                messaging.convertAndSend(
                    "/topic/admin/channels",
                    new NotificationFrame(java.util.UUID.randomUUID(), "channel.connection.changed", event)
                );
            } catch (org.springframework.messaging.MessagingException unavailable) {
                LOGGER.warn("Evento de conexão indisponível: channelId={}", event.channelId());
            }
        }
    }

    @EventListener
    public void publish(ConversationEvent event) {
        if (!access.enabled()) {
            return;
        }
        try {
            if (event.payload() instanceof com.wxn.chatbot.conversation.event.AccessRevoked revoked) {
                messaging.convertAndSend("/topic/members/" + revoked.memberId() + "/conversations", event);
                return;
            }
            messaging.convertAndSend(StompDestinations.conversations(), event);
            if (event.conversationId() != null && org.bson.types.ObjectId.isValid(event.conversationId())) {
                conversations
                    .findById(new org.bson.types.ObjectId(event.conversationId()))
                    .ifPresent(conversation -> {
                        if (conversation.getAssignedMemberId() != null) {
                            messaging.convertAndSend(
                                "/topic/members/" + conversation.getAssignedMemberId() + "/conversations",
                                event
                            );
                        }
                    });
            }
        } catch (org.springframework.messaging.MessagingException unavailable) {
            LOGGER.warn("Notificação STOMP indisponível: eventId={}", event.eventId());
        }
    }
}
