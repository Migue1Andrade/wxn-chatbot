package com.wxn.chatbot.message.service;

import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.mapper.MessageMapper;
import com.wxn.chatbot.message.model.MessageStatusTransitions;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.WhatsAppProviderResolver;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MessageStatusService {

    private final MessageRepository repository;
    private final ConversationService conversations;
    private final WhatsAppProviderResolver providers;
    private final ConversationEventPublisher events;

    public MessageStatusService(
        MessageRepository repository,
        ConversationService conversations,
        WhatsAppProviderResolver providers,
        ConversationEventPublisher events
    ) {
        this.repository = repository;
        this.conversations = conversations;
        this.providers = providers;
        this.events = events;
    }

    public Message apply(Message m, NormalizedMessageStatus next, String errorCode) {
        List<NormalizedMessageStatus> prior = MessageStatusTransitions.allowedPrevious(next);
        if (prior.isEmpty()) {
            return repository.findById(m.getId()).orElseThrow();
        }
        Message updated = repository.updateStatus(m, next, errorCode, prior).orElse(null);
        if (updated == null) {
            return repository.findById(m.getId()).orElseThrow();
        }
        if (
            Direction.OUTBOUND.name().equals(updated.getDirection()) &&
            Set.of(
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.DELIVERED,
                NormalizedMessageStatus.READ,
                NormalizedMessageStatus.SIMULATED
            ).contains(next)
        ) {
            conversations.touch(conversations.require(updated.getConversationId().toHexString()), updated);
        }
        events.publish(
            ConversationEventTypes.MESSAGE_STATUS_UPDATED,
            MessageMapper.response(updated, providers.messageCapabilities(updated.getProvider()))
        );
        return updated;
    }
}
