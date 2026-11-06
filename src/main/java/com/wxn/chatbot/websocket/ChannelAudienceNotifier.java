package com.wxn.chatbot.websocket;

import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.whatsapp.operations.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
public class ChannelAudienceNotifier {

    private final ChannelOperationsRepository channels;
    private final MemberRepository members;
    private final SimpMessagingTemplate messaging;

    public ChannelAudienceNotifier(
        ChannelOperationsRepository channels,
        MemberRepository members,
        SimpMessagingTemplate messaging
    ) {
        this.channels = channels;
        this.members = members;
        this.messaging = messaging;
    }

    @EventListener
    public void changed(ChannelOperationsService.Changed event) {
        var users = new java.util.HashSet<Long>();
        for (var member : members.findAllById(channels.assignedMembers(event.channelId()))) {
            if (member.getUserId() != null) {
                users.add(member.getUserId());
            }
        }
        for (Long user : users) {
            try {
                messaging.convertAndSend(
                    "/topic/users/" + user + "/notifications",
                    new StompConversationEventBridge.NotificationFrame(
                        UUID.randomUUID(),
                        "channel.connection.changed",
                        event
                    )
                );
            } catch (org.springframework.messaging.MessagingException unavailable) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "channel_event outcome=UNAVAILABLE channelId={}",
                    event.channelId()
                );
            }
        }
    }
}
