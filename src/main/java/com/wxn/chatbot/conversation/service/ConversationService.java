package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.company.service.CompanyService;
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
    private final ConversationRepository repository;
    private final CompanyService companies;
    private final ConversationEventPublisher events;
    private final ConversationActivityService activities;
    private final ConversationTransitionService transitions;

    public ConversationService(
        ConversationRepository repository,
        CompanyService companies,
        ConversationEventPublisher events,
        ConversationActivityService activities,
        ConversationTransitionService transitions
    ) {
        this.repository = repository;
        this.companies = companies;
        this.events = events;
        this.activities = activities;
        this.transitions = transitions;
    }

    public Conversation require(Long companyId, String id) {
        companies.require(companyId);
        if (!ObjectId.isValid(id)) throw ApiException.badRequest("conversationId inválido");
        Conversation conversation = repository
            .findByIdAndCompanyId(new ObjectId(id), companyId)
            .orElseThrow(() -> ApiException.notFound("Conversa não encontrada na empresa"));
        normalizeRevision(conversation);
        activities.flush(conversation);
        return conversation;
    }

    public NormalizedConversation get(Long companyId, String id) {
        return ConversationMapper.response(require(companyId, id));
    }

    public List<NormalizedConversation> list(Long companyId, int page, int size) {
        companies.require(companyId);
        return repository
            .findByCompanyIdOrderByUpdatedAtDesc(companyId, page(page, size))
            .stream()
            .map(ConversationMapper::response)
            .toList();
    }

    public Conversation individual(WhatsAppChannel channel, String phone) {
        String key = "individual:" + phone;
        Conversation conversation = repository
            .findByCompanyIdAndChannelIdAndConversationKey(channel.getCompanyId(), channel.getId(), key)
            .orElseGet(() -> create(channel, phone, key));
        normalizeRevision(conversation);
        return conversation;
    }

    private void normalizeRevision(Conversation conversation) {
        if (conversation.getVersion() == null) {
            repository.initializeRevision(conversation.getId(), conversation.getCompanyId());
            conversation.setVersion(0L);
        }
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
                .findByCompanyIdAndChannelIdAndConversationKey(channel.getCompanyId(), channel.getId(), key)
                .orElseThrow(() -> concurrent);
        }
        activities.flush(conversation);
        events.publish(
            ConversationEventTypes.CONVERSATION_CREATED,
            conversation.getCompanyId(),
            ConversationMapper.response(conversation)
        );
        return conversation;
    }

    public void updateContact(Conversation conversation, String name) {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        if (trimmed.length() > 200) trimmed = trimmed.substring(0, 200);
        repository
            .updateDisplayName(conversation.getId(), conversation.getCompanyId(), trimmed)
            .ifPresent(updated ->
                events.publish(
                    ConversationEventTypes.CONVERSATION_UPDATED,
                    updated.getCompanyId(),
                    ConversationMapper.response(updated)
                )
            );
    }

    public void touch(Conversation conversation, Message message) {
        if (Direction.INBOUND.name().equals(message.getDirection())) reopenForInbound(conversation, message);
        String preview = message.getContent() == null ? null : message.getContent().getString("text");
        if (preview == null || preview.isBlank()) preview = "Anexo recebido";
        if (preview.length() > PREVIEW_LENGTH) preview = preview.substring(0, PREVIEW_LENGTH);
        repository
            .updateActivity(
                conversation.getId(),
                conversation.getCompanyId(),
                message.getCreatedAt(),
                message.getId(),
                preview
            )
            .ifPresent(updated ->
                events.publish(
                    ConversationEventTypes.CONVERSATION_UPDATED,
                    updated.getCompanyId(),
                    ConversationMapper.response(updated)
                )
            );
    }

    private void reopenForInbound(Conversation conversation, Message message) {
        for (int attempt = 0; attempt < MAX_REOPEN_ATTEMPTS; attempt++) {
            Conversation current = repository
                .findByIdAndCompanyId(conversation.getId(), conversation.getCompanyId())
                .orElseThrow();
            if (current.operationalStatus() != ConversationStatus.CLOSED) return;
            if (
                current.getClosedAt() != null &&
                message.getCreatedAt() != null &&
                message.getCreatedAt().isBefore(current.getClosedAt())
            ) return;
            var transition = new ConversationTransition(
                ConversationStatus.PENDING,
                null,
                null,
                ConversationActivityType.CONVERSATION_AUTO_REOPENED,
                null,
                null,
                ConversationEventTypes.CONVERSATION_REOPENED
            );
            if (transitions.attempt(current, current.revision(), transition).isPresent()) return;
        }
        throw ApiException.conflict("Conversa alterada simultaneamente; não foi possível reabri-la");
    }

    public static PageRequest page(int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw ApiException.badRequest(
            "page >= 0; size entre 1 e 200"
        );
        return PageRequest.of(page, size);
    }
}
