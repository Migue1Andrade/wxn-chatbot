package com.wxn.chatbot.message.service;

import com.wxn.chatbot.auth.service.*;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.mapper.MessageMapper;
import com.wxn.chatbot.message.repository.*;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.operations.ChannelOperationsRepository;
import com.wxn.chatbot.whatsapp.provider.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.time.Instant;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class MessageMutationService {

    private final MessageRepository messages;
    private final MessageMutationRepository operations;
    private final ConversationAuthorization authorization;
    private final ConversationService conversations;
    private final ChannelService channels;
    private final WhatsAppProviderResolver providers;
    private final ChannelOperationsRepository policies;
    private final ConversationEventPublisher events;

    public MessageMutationService(
        MessageRepository messages,
        MessageMutationRepository operations,
        ConversationAuthorization authorization,
        ConversationService conversations,
        ChannelService channels,
        WhatsAppProviderResolver providers,
        ChannelOperationsRepository policies,
        ConversationEventPublisher events
    ) {
        this.messages = messages;
        this.operations = operations;
        this.authorization = authorization;
        this.conversations = conversations;
        this.channels = channels;
        this.providers = providers;
        this.policies = policies;
        this.events = events;
    }

    public NormalizedMessage mutate(
        String id,
        String operation,
        String key,
        String value,
        AccountSessions.Identity actor
    ) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.badRequest("Mensagem inválida");
        }
        Message message = messages
            .findById(new ObjectId(id))
            .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada"));
        final Message candidate = message;
        return conversations.withWriteLock(message.getConversationId().toHexString(), () ->
            performMutation(candidate, operation, key, value, actor)
        );
    }

    private NormalizedMessage performMutation(
        Message message,
        String operation,
        String key,
        String value,
        AccountSessions.Identity actor
    ) {
        authorization.require(message.getConversationId().toHexString(), actor);
        var conversation = conversations.require(message.getConversationId().toHexString());
        if (Set.of("CLOSED", "RESOLVED").contains(conversation.operationalStatus().name())) {
            throw ApiException.conflict("Reabra o atendimento");
        }
        var channel = channels.requireActive(message.getChannelId());
        var adapter = providers.resolve(channel);
        if (!adapter.messageCapabilities().contains(MessageCapability.valueOf(operation))) {
            throw ApiException.conflict("Operação não suportada");
        }
        if (
            !Objects.equals(conversation.getChannelId(), channel.getId()) ||
            message.getProvider() != channel.getProvider()
        ) {
            throw ApiException.conflict("Canal incompatível");
        }
        var existing = operations.operation(channel.getId(), key);
        if (existing != null) {
            if (
                !Objects.equals(existing.get("messageId"), message.getId()) ||
                !Objects.equals(existing.getString("operation"), operation) ||
                !Objects.equals(existing.getString("value"), value) ||
                !Objects.equals(existing.getLong("actorUserId"), actor.id())
            ) {
                throw ApiException.conflict("Identificador já utilizado");
            }
            return response(message);
        }
        if (
            message.getDeletedAt() != null ||
            message.getExternalMessageId() == null ||
            message.getDeleteState() != null ||
            Set.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.UNKNOWN,
                NormalizedMessageStatus.FAILED
            ).contains(message.getStatus())
        ) {
            throw ApiException.conflict("Mensagem não elegível");
        }
        if (
            "EDIT_TEXT".equals(operation) &&
            (!"OUTBOUND".equals(message.getDirection()) ||
                value == null ||
                value.isBlank() ||
                value.length() > 1600)
        ) {
            throw ApiException.badRequest("Apenas texto enviado pode ser editado, até 1600 caracteres");
        }
        if (
            "SEND_REACTION".equals(operation) && !Set.of("👍", "❤️", "😂", "😮", "😢", "🙏").contains(value)
        ) {
            throw ApiException.badRequest("Reação inválida");
        }
        if (
            "MARK_READ".equals(operation) &&
            (!"INBOUND".equals(message.getDirection()) ||
                !Boolean.TRUE.equals(policies.runtime(channel.getId()).getBoolean("remoteReadEnabled")))
        ) {
            throw ApiException.conflict("Leitura remota desabilitada ou mensagem enviada");
        }
        try {
            operations.intent(message, operation, key, value, actor.id());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw ApiException.conflict("Operação concorrente; consulte o histórico");
        }
        if (!operations.claim(message, operation, key, value)) {
            operations.finish(message, operation, key, value, "REJECTED");
            throw ApiException.conflict("Outra operação aguarda confirmação");
        }
        String state;
        try {
            authorization.require(conversation.getId().toHexString(), actor);
            state = adapter.mutate(
                channel,
                operation,
                conversation.getContactPhone(),
                message.getExternalMessageId(),
                value
            );
        } catch (RuntimeException error) {
            state =
                error instanceof ProviderSendException failure && failure.code().endsWith("UNKNOWN")
                    ? "UNKNOWN"
                    : "FAILED";
        }
        message = operations.finish(message, operation, key, value, state);
        publish(message);
        return response(message);
    }

    public void observed(
        Long channel,
        String external,
        String operation,
        String value,
        boolean own,
        Instant at,
        String phone
    ) {
        Message message = messages
            .findByChannelIdAndExternalMessageId(channel, external)
            .orElseThrow(() -> ApiException.conflict("Mensagem ainda não correlacionada"));
        String contact = "OUTBOUND".equals(message.getDirection())
            ? message.getRecipientPhone()
            : message.getSenderPhone();
        if (!Objects.equals(phone, contact)) {
            throw ApiException.conflict("Identidade remota incompatível");
        }
        var updated = operations.observe(message, operation, value, own, at);
        boolean newlyObserved = updated != null;
        if (updated == null) {
            updated = message;
        }
        var pending = message.getPendingMutation();
        if (
            pending != null &&
            (pending.getDate("requestedAt") == null ||
                !at.isBefore(pending.getDate("requestedAt").toInstant())) &&
            (newlyObserved ||
                Objects.equals(
                    "EDIT_TEXT".equals(operation)
                        ? message.getContent().getString("text")
                        : own
                          ? message.getReaction()
                          : message.getContactReaction(),
                    value
                )) &&
            (own || "EDIT_TEXT".equals(operation)) &&
            ((Objects.equals(pending.getString("operation"), operation) &&
                Objects.equals(pending.getString("value"), value)) ||
                ("REMOVE_REACTION".equals(pending.getString("operation")) && value.isEmpty()))
        ) {
            newlyObserved = true;
            updated = operations.finish(
                updated,
                pending.getString("operation"),
                pending.getString("operationId"),
                value,
                "CONFIRMED"
            );
        }
        if (newlyObserved) {
            publish(updated);
        }
    }

    private NormalizedMessage response(Message message) {
        return MessageMapper.response(message, providers.messageCapabilities(message.getProvider()));
    }

    private void publish(Message message) {
        conversations.refreshPreview(message);
        events.publish(ConversationEventTypes.MESSAGE_STATUS_UPDATED, response(message));
    }
}
