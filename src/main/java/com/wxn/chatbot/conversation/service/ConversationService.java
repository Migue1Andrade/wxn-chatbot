package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.conversation.model.*;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private static final int MAX_REOPEN_ATTEMPTS = 8;
    private static final int PREVIEW_LENGTH = 160;
    private final ConversationWriteCoordinator writes;
    private final com.wxn.chatbot.contact.ContactRegistry contacts;
    private final ConversationRepository repository;
    private final ConversationEventPublisher events;
    private final ConversationActivityService activities;
    private final ConversationTransitionService transitions;

    public ConversationService(
        ConversationRepository repository,
        ConversationEventPublisher events,
        ConversationActivityService activities,
        ConversationTransitionService transitions,
        com.wxn.chatbot.contact.ContactRegistry contacts,
        ConversationWriteCoordinator writes
    ) {
        this.writes = writes;
        this.contacts = contacts;
        this.repository = repository;

        this.events = events;
        this.activities = activities;
        this.transitions = transitions;
    }

    public <T> T withWriteLock(String id, java.util.function.Supplier<T> operation) {
        return writes.execute(id, operation);
    }

    public Conversation require(String id) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.badRequest("conversationId inválido");
        }
        Conversation conversation = repository
            .findById(new ObjectId(id))
            .orElseThrow(() -> ApiException.notFound("Conversa não encontrada"));
        activities.flush(conversation);
        return conversation;
    }

    public NormalizedConversation get(String id) {
        return ConversationMapper.response(require(id));
    }

    public Conversation individual(WhatsAppChannel channel, String phone) {
        String key = "individual:" + phone;
        Conversation conversation = repository
            .findByChannelIdAndConversationKey(channel.getId(), key)
            .orElseGet(() -> create(channel, phone, key));
        if (conversation.getContactId() == null) {
            var contactId = contacts.resolve(channel.getId(), phone);
            repository.linkContact(conversation.getId(), contactId);
            conversation.setContactId(contactId);
        }
        return conversation;
    }

    private Conversation create(WhatsAppChannel channel, String phone, String key) {
        Conversation conversation = ConversationMapper.individual(channel, phone, key);
        conversation.setId(new ObjectId());
        conversation.setPendingActivities(
            List.of(
                activities.prepare(
                    conversation,
                    ConversationActivityType.CONVERSATION_CREATED,
                    null,
                    null,
                    null,
                    0,
                    conversation.getCreatedAt()
                )
            )
        );
        try {
            conversation = repository.insert(conversation);
        } catch (DuplicateKeyException concurrent) {
            return repository
                .findByChannelIdAndConversationKey(channel.getId(), key)
                .orElseThrow(() -> concurrent);
        }
        activities.flush(conversation);
        events.publish(
            ConversationEventTypes.CONVERSATION_CREATED,
            ConversationMapper.response(conversation)
        );
        return conversation;
    }

    public void updateContact(Conversation conversation, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        contacts.observe(conversation.getContactId(), name);
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            trimmed = trimmed.substring(0, 200);
        }
        repository
            .updateDisplayName(conversation.getId(), trimmed)
            .ifPresent(updated ->
                events.publish(
                    ConversationEventTypes.CONVERSATION_UPDATED,
                    ConversationMapper.response(updated)
                )
            );
    }

    public void refreshPreview(Message message) {
        String preview =
            message.getDeletedAt() != null
                ? "Mensagem apagada"
                : message.getContent() == null
                  ? ""
                  : message.getContent().getString("text");
        if (preview == null) {
            preview = "";
        }
        repository
            .refreshPreview(
                message.getConversationId(),
                message.getId(),
                preview.substring(0, Math.min(PREVIEW_LENGTH, preview.length()))
            )
            .ifPresent(updated ->
                events.publish(
                    ConversationEventTypes.CONVERSATION_UPDATED,
                    ConversationMapper.response(updated)
                )
            );
    }

    public void touch(Conversation conversation, Message message) {
        if (Direction.INBOUND.name().equals(message.getDirection())) {
            Conversation current = repository.findById(conversation.getId()).orElseThrow();
            if (
                current.operationalStatus() == ConversationStatus.WAITING_CUSTOMER &&
                (current.getLastOutboundAt() == null ||
                    !message.getCreatedAt().isBefore(current.getLastOutboundAt()))
            ) {
                transitions.attempt(
                    current,
                    current.revision(),
                    new ConversationTransition(
                        ConversationStatus.IN_PROGRESS,
                        current.getAssignedMemberId(),
                        null,
                        ConversationActivityType.CONVERSATION_STATUS_CHANGED,
                        null,
                        "Cliente retornou",
                        ConversationEventTypes.CONVERSATION_UPDATED
                    )
                );
            }
        }
        if (
            Direction.INBOUND.name().equals(message.getDirection()) ||
            java.util.Set.of(
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.DELIVERED,
                NormalizedMessageStatus.READ,
                NormalizedMessageStatus.SIMULATED
            ).contains(message.getStatus())
        ) {
            repository.recordDirection(
                conversation.getId(),
                message.getCreatedAt(),
                Direction.INBOUND.name().equals(message.getDirection())
            );
        }
        if (Direction.INBOUND.name().equals(message.getDirection())) {
            reopenForInbound(conversation, message);
        }
        String preview =
            message.getDeletedAt() != null
                ? "Mensagem apagada"
                : message.getContent() == null
                  ? null
                  : message.getContent().getString("text");
        if (preview == null || preview.isBlank()) {
            preview = "Anexo recebido";
        }
        if (preview.length() > PREVIEW_LENGTH) {
            preview = preview.substring(0, PREVIEW_LENGTH);
        }
        repository
            .updateActivity(conversation.getId(), message.getCreatedAt(), message.getId(), preview)
            .ifPresent(updated ->
                events.publish(
                    ConversationEventTypes.CONVERSATION_UPDATED,
                    ConversationMapper.response(updated)
                )
            );
    }

    private void reopenForInbound(Conversation conversation, Message message) {
        for (int attempt = 0; attempt < MAX_REOPEN_ATTEMPTS; attempt++) {
            Conversation current = repository.findById(conversation.getId()).orElseThrow();
            if (
                current.operationalStatus() != ConversationStatus.CLOSED &&
                current.operationalStatus() != ConversationStatus.RESOLVED
            ) {
                return;
            }
            var completedAt = current.getClosedAt() != null ? current.getClosedAt() : current.getResolvedAt();
            if (
                completedAt != null &&
                message.getCreatedAt() != null &&
                message.getCreatedAt().isBefore(completedAt)
            ) {
                return;
            }
            var transition = new ConversationTransition(
                ConversationStatus.PENDING,
                null,
                null,
                ConversationActivityType.CONVERSATION_AUTO_REOPENED,
                null,
                null,
                ConversationEventTypes.CONVERSATION_REOPENED
            );
            if (transitions.attempt(current, current.revision(), transition).isPresent()) {
                return;
            }
        }
        throw ApiException.conflict("Conversa alterada simultaneamente; não foi possível reabri-la");
    }

    public static PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw ApiException.badRequest("page >= 0; size entre 1 e 200");
        }
        return PageRequest.of(page, size);
    }
}
