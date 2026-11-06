package com.wxn.chatbot.message.service;

import com.wxn.chatbot.auth.service.AccountSessions.Identity;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.mapper.MessageMapper;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.messagelog.model.MessageLogEvent;
import com.wxn.chatbot.messagelog.service.MessageLogService;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.model.DeleteMessageRequest.DeleteIntent;
import com.wxn.chatbot.whatsapp.provider.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MessageActionsService {

    private final com.wxn.chatbot.whatsapp.operations.ChannelOperationsRepository policies;
    private final MessageRepository repository;
    private final ConversationService conversations;
    private final ChannelService channels;
    private final WhatsAppProviderResolver providers;
    private final MessageService messages;
    private final MessageLogService logs;
    private final ConversationEventPublisher events;

    public MessageActionsService(
        MessageRepository repository,
        ConversationService conversations,
        ChannelService channels,
        WhatsAppProviderResolver providers,
        MessageService messages,
        MessageLogService logs,
        ConversationEventPublisher events,
        com.wxn.chatbot.whatsapp.operations.ChannelOperationsRepository policies
    ) {
        this.policies = policies;
        this.repository = repository;
        this.conversations = conversations;
        this.channels = channels;
        this.providers = providers;
        this.messages = messages;
        this.logs = logs;
        this.events = events;
    }

    private Conversation writable(String id, Identity actor) {
        Conversation c = conversations.require(id);
        if (
            c.operationalStatus() == ConversationStatus.CLOSED ||
            c.operationalStatus() == ConversationStatus.RESOLVED
        ) {
            throw ApiException.conflict("Reabra o atendimento para executar esta operação");
        }
        if (
            actor.role() != UserRole.ADMIN &&
            (actor.memberId() == null || !Objects.equals(c.getAssignedMemberId(), actor.memberId()))
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assuma o atendimento para operar suas mensagens");
        }
        var channel = channels.requireActive(c.getChannelId());
        if (channel.getProvider() != c.getProvider()) {
            throw ApiException.conflict("Provider do canal foi alterado");
        }
        return c;
    }

    public record Capabilities(
        Set<MessageCapability> provider,
        boolean writable,
        String unavailableReason,
        ChannelConnection connection
    ) {}

    public Capabilities capabilities(String id, Identity actor) {
        Conversation c = conversations.require(id);
        var channel = channels.require(c.getChannelId());
        boolean writable;
        String unavailableReason = null;
        var provider = providers.resolve(channel);
        try {
            provider.validateChannel(channel);
            writable(id, actor);
            writable = true;
        } catch (ApiException forbidden) {
            writable = false;
            unavailableReason = forbidden.getMessage();
        }
        var capabilities = new java.util.HashSet<>(provider.messageCapabilities());
        capabilities.removeAll(
            Set.of(
                MessageCapability.CONFIGURE_WEBHOOKS,
                MessageCapability.QR_CODE,
                MessageCapability.RESTART_INSTANCE,
                MessageCapability.DISCONNECT_INSTANCE,
                MessageCapability.CONNECTION_STATUS
            )
        );
        if (!Boolean.TRUE.equals(policies.runtime(channel.getId()).getBoolean("remoteReadEnabled"))) {
            capabilities.remove(MessageCapability.MARK_READ);
        }
        var connection = provider.connection(channel);
        if ("DISCONNECTED".equals(connection.state())) {
            writable = false;
            unavailableReason = "Canal desconectado. Preserve o rascunho e consulte o administrador.";
        }
        return new Capabilities(Set.copyOf(capabilities), writable, unavailableReason, connection);
    }

    private Message require(String id) {
        if (!ObjectId.isValid(id)) {
            throw ApiException.badRequest("ID de mensagem inválido");
        }
        return repository
            .findById(new ObjectId(id))
            .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada"));
    }

    private void usable(Message message) {
        if (
            !"TEXT".equals(message.getType()) ||
            message.getExternalMessageId() == null ||
            message.getDeletedAt() != null ||
            message.getStatus() == NormalizedMessageStatus.FAILED ||
            message.getStatus() == NormalizedMessageStatus.UNKNOWN ||
            message.getStatus() == NormalizedMessageStatus.PENDING
        ) {
            throw ApiException.conflict("Mensagem não está disponível para esta operação");
        }
    }

    private WhatsAppProvider capable(Conversation c, MessageCapability capability) {
        var provider = providers.resolve(channels.requireActive(c.getChannelId()));
        if (!provider.messageCapabilities().contains(capability)) {
            throw ApiException.conflict("Operação não suportada pelo provider do canal");
        }
        return provider;
    }

    public NormalizedMessage send(String id, MessageDtos.Text request, Identity actor) {
        if (request.content().matches("(?s).*@@[a-zA-Z0-9_]+.*")) {
            throw ApiException.badRequest("Resolva as variáveis @@ antes de enviar");
        }
        Conversation c = writable(id, actor);
        capable(c, MessageCapability.SEND_TEXT);
        return messages.send(
            id,
            new MessageDtos.Send(
                c.getChannelId(),
                actor.memberId(),
                request.content(),
                request.operationId(),
                actor.id()
            )
        );
    }

    public NormalizedMessage reply(String id, String messageId, MessageDtos.Text request, Identity actor) {
        Conversation c = writable(id, actor);
        if (request.content().matches("(?s).*@@[a-zA-Z0-9_]+.*")) {
            throw ApiException.badRequest("Resolva as variáveis @@ antes de enviar");
        }
        Message source = require(messageId);
        usable(source);
        if (
            !source.getConversationId().equals(c.getId()) ||
            !Objects.equals(source.getChannelId(), c.getChannelId()) ||
            source.getProvider() != c.getProvider()
        ) {
            throw ApiException.badRequest("Citação não pertence à conversa/canal");
        }
        capable(c, MessageCapability.REPLY_TEXT);
        return messages.sendOperation(
            id,
            new MessageDtos.Send(
                c.getChannelId(),
                actor.memberId(),
                request.content(),
                request.operationId(),
                actor.id()
            ),
            source,
            null
        );
    }

    public NormalizedMessage forward(String messageId, MessageDtos.Forward request, Identity actor) {
        Message source = require(messageId);
        usable(source);
        Conversation origin = writable(source.getConversationId().toHexString(), actor);
        Conversation destination = writable(request.destinationConversationId(), actor);
        if (
            !Objects.equals(origin.getChannelId(), destination.getChannelId()) ||
            origin.getProvider() != destination.getProvider() ||
            source.getProvider() != origin.getProvider() ||
            !Objects.equals(source.getChannelId(), origin.getChannelId())
        ) {
            throw ApiException.conflict("Encaminhamento nativo exige o mesmo canal e instância");
        }
        if (origin.getConversationType() != ConversationType.INDIVIDUAL) {
            throw ApiException.conflict("Encaminhamento disponível somente em chats individuais");
        }
        capable(destination, MessageCapability.FORWARD_TEXT);
        return messages.sendOperation(
            request.destinationConversationId(),
            new MessageDtos.Send(
                destination.getChannelId(),
                actor.memberId(),
                source.getContent().getString("text"),
                request.operationId(),
                actor.id()
            ),
            null,
            source
        );
    }

    public NormalizedMessage delete(String id, DeleteIntent intent, String operationId, Identity actor) {
        if (intent == DeleteIntent.DELETE_FOR_ME) {
            throw ApiException.conflict(
                "Exclusão na instância compartilhada não está disponível nesta versão"
            );
        }
        Message message = require(id);
        return conversations.withWriteLock(message.getConversationId().toHexString(), () ->
            deleteConfirmed(message, intent, operationId, actor)
        );
    }

    private NormalizedMessage deleteConfirmed(
        Message message,
        DeleteIntent intent,
        String operationId,
        Identity actor
    ) {
        String id = message.getId().toHexString();
        Conversation c = writable(message.getConversationId().toHexString(), actor);
        if (
            message.getDeleteState() != null &&
            (!"FAILED".equals(message.getDeleteState()) ||
                Objects.equals(message.getDeleteOperationId(), operationId))
        ) {
            if (
                !Objects.equals(message.getDeleteOperationId(), operationId) ||
                !Objects.equals(message.getDeletionIntent(), intent.name())
            ) {
                throw ApiException.conflict("Exclusão já solicitada; consulte seu estado antes de repetir");
            }
            return MessageMapper.response(message, providers.messageCapabilities(message.getProvider()));
        }
        usable(message);
        boolean owner = "OUTBOUND".equals(message.getDirection());
        if (intent == DeleteIntent.DELETE_FOR_EVERYONE && !owner) {
            throw ApiException.conflict("Apagar para todos exige mensagem enviada pelo número conectado");
        }
        if (
            !Objects.equals(message.getChannelId(), c.getChannelId()) ||
            message.getProvider() != c.getProvider()
        ) {
            throw ApiException.conflict("Mensagem e canal incompatíveis");
        }
        var provider = capable(
            c,
            intent == DeleteIntent.DELETE_FOR_ME
                ? MessageCapability.DELETE_FOR_ME
                : MessageCapability.DELETE_FOR_EVERYONE
        );
        if (!repository.claimDeletion(message, operationId, intent.name(), actor.id())) {
            throw ApiException.conflict("Exclusão em andamento; atualize o histórico");
        }
        message = require(id);
        logs.record(message, MessageLogEvent.DELETE_ATTEMPTED);
        try {
            provider.delete(
                channels.requireActive(c.getChannelId()),
                new DeleteMessageRequest(message.getExternalMessageId(), c.getContactPhone(), owner, intent)
            );
        } catch (RuntimeException error) {
            boolean unknown =
                error instanceof ProviderSendException failure && failure.code().endsWith("UNKNOWN");
            message = repository.finishDeletion(message, unknown ? "UNKNOWN" : "FAILED");
            logs.record(message, unknown ? MessageLogEvent.DELETE_UNKNOWN : MessageLogEvent.DELETE_FAILED);
            events.publish(
                ConversationEventTypes.MESSAGE_STATUS_UPDATED,
                MessageMapper.response(message, providers.messageCapabilities(message.getProvider()))
            );
            return MessageMapper.response(message, providers.messageCapabilities(message.getProvider()));
        }
        message = repository.finishDeletion(message, "CONFIRMED");
        conversations.refreshPreview(message);
        logs.record(message, MessageLogEvent.DELETE_CONFIRMED);
        events.publish(
            ConversationEventTypes.MESSAGE_STATUS_UPDATED,
            MessageMapper.response(message, providers.messageCapabilities(message.getProvider()))
        );
        return MessageMapper.response(message, providers.messageCapabilities(message.getProvider()));
    }
}
