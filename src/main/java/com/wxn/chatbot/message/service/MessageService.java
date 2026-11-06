package com.wxn.chatbot.message.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.ConversationEventPublisher;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.dto.MessagePage;
import com.wxn.chatbot.message.mapper.MessageMapper;
import com.wxn.chatbot.message.model.MessageCursor;
import com.wxn.chatbot.message.model.MessageStatusTransitions;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.messagelog.model.MessageLogEvent;
import com.wxn.chatbot.messagelog.service.MessageLogService;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final java.util.concurrent.ConcurrentMap<
        Long,
        java.util.concurrent.locks.ReentrantLock
    > channelLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final MessageStatusService statuses;
    private final MessageRepository repository;
    private final ConversationService conversations;
    private final ChannelService channels;
    private final MemberService members;
    private final WhatsAppProviderResolver providers;
    private final MessageLogService logs;
    private final ConversationEventPublisher events;

    public MessageService(
        MessageRepository repository,
        ConversationService conversations,
        ChannelService channels,
        MemberService members,
        WhatsAppProviderResolver providers,
        MessageLogService logs,
        ConversationEventPublisher events,
        MessageStatusService statuses
    ) {
        this.statuses = statuses;
        this.repository = repository;
        this.conversations = conversations;
        this.channels = channels;
        this.members = members;
        this.providers = providers;
        this.logs = logs;
        this.events = events;
    }

    public List<NormalizedMessage> history(String conversationId, int page, int size) {
        Conversation c = conversations.require(conversationId);
        return repository
            .findByConversationIdOrderByCreatedAtAsc(c.getId(), ConversationService.page(page, size))
            .stream()
            .map(this::response)
            .toList();
    }

    public MessagePage page(String conversationId, int limit, String before) {
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("limit deve estar entre 1 e 100");
        }
        Conversation conversation = conversations.require(conversationId);
        var cursor = before == null ? null : MessageCursor.decode(before);
        var rows = new ArrayList<>(repository.before(conversation.getId(), cursor, limit + 1));
        boolean more = rows.size() > limit;
        if (more) {
            rows.remove(rows.size() - 1);
        }
        String next = more && !rows.isEmpty() ? MessageCursor.of(rows.get(rows.size() - 1)).encode() : null;
        Collections.reverse(rows);
        return new MessagePage(rows.stream().map(this::response).toList(), next, more);
    }

    public NormalizedMessage receive(
        WhatsAppChannel channel,
        NormalizedInboundMessage incoming,
        boolean simulated
    ) {
        // The channel is resolved and authenticated by the ingress service before normalization.
        channels.requireActive(channel.getId());
        if (!Objects.equals(channel.getPhoneNumber(), incoming.recipientPhone())) {
            throw ApiException.badRequest("Destinatário não corresponde ao canal");
        }
        Optional<Message> existing = repository.findByChannelIdAndExternalMessageId(
            channel.getId(),
            incoming.externalMessageId()
        );
        if (existing.isPresent()) {
            return existingInbound(channel, existing.get(), incoming);
        }
        Conversation c = conversations.individual(channel, incoming.senderPhone());
        Message m = MessageMapper.inbound(channel, c, incoming, simulated);
        resolveReply(m);
        try {
            m = repository.insert(m);
        } catch (DuplicateKeyException concurrent) {
            return existingInbound(
                channel,
                repository
                    .findByChannelIdAndExternalMessageId(channel.getId(), incoming.externalMessageId())
                    .orElseThrow(() -> concurrent),
                incoming
            );
        }
        return finishInbound(channel, m, incoming);
    }

    private NormalizedMessage existingInbound(
        WhatsAppChannel channel,
        Message message,
        NormalizedInboundMessage incoming
    ) {
        if (
            Direction.OUTBOUND.name().equals(message.getDirection()) ||
            (message.getSenderPhone() != null && !message.getSenderPhone().equals(incoming.senderPhone()))
        ) {
            throw ApiException.conflict("Identificador externo já vinculado a outra mensagem");
        }
        return Boolean.FALSE.equals(message.getInboundCompleted())
            ? finishInbound(channel, message, incoming)
            : response(message);
    }

    private NormalizedMessage finishInbound(
        WhatsAppChannel channel,
        Message message,
        NormalizedInboundMessage incoming
    ) {
        Conversation conversation = conversations.require(message.getConversationId().toHexString());
        conversations.updateContact(conversation, incoming.senderDisplayName());
        conversations.touch(conversation, message);
        logs.record(message, MessageLogEvent.RECEIVED);
        if (repository.completeInbound(message)) {
            events.publish(ConversationEventTypes.MESSAGE_RECEIVED, response(message));
        }
        return response(message);
    }

    public NormalizedMessage send(String conversationId, MessageDtos.Send request) {
        return sendOperation(conversationId, request, null, null);
    }

    public NormalizedMessage sendOperation(
        String conversationId,
        MessageDtos.Send request,
        Message reply,
        Message forwarded
    ) {
        var lock = acquireChannel(conversations.require(conversationId).getChannelId());
        try {
            if (forwarded != null) {
                return conversations.withWriteLock(forwarded.getConversationId().toHexString(), () ->
                    conversations.withWriteLock(conversationId, () ->
                        performSend(conversationId, request, reply, forwarded)
                    )
                );
            }
            return conversations.withWriteLock(conversationId, () ->
                performSend(conversationId, request, reply, forwarded)
            );
        } finally {
            lock.unlock();
        }
    }

    private NormalizedMessage performSend(
        String conversationId,
        MessageDtos.Send request,
        Message reply,
        Message forwarded
    ) {
        Conversation c = conversations.require(conversationId);
        if (c.operationalStatus() == ConversationStatus.CLOSED) {
            throw ApiException.conflict("Reabra o atendimento antes de enviar mensagens");
        }
        WhatsAppChannel channel = channels.requireActive(request.channelId());
        if (!Objects.equals(c.getChannelId(), channel.getId()) || c.getProvider() != channel.getProvider()) {
            throw ApiException.badRequest("Conversa não pertence ao canal informado");
        }
        if (request.memberId() != null) {
            members.requireActive(request.memberId());
        }
        String recipient = PhoneNumbers.required(c.getContactPhone());
        if (request.content() == null || request.content().isBlank() || request.content().length() > 1600) {
            throw ApiException.badRequest("Texto obrigatório, até 1600 caracteres");
        }
        if (c.getConversationType() != ConversationType.INDIVIDUAL) {
            throw ApiException.badRequest("Envio disponível apenas em conversas individuais");
        }
        WhatsAppProvider adapter = providers.forSend(channel, c.getConversationType(), MessageType.TEXT);
        if (request.operationId() != null) {
            var existing = repository.findByChannelIdAndOperationId(channel.getId(), request.operationId());
            if (existing.isPresent()) {
                return repeated(existing.get(), c, request, reply, forwarded);
            }
        }
        if (request.actorUserId() != null) {
            members.requireMessageActor(request.actorUserId(), request.memberId(), c.getAssignedMemberId());
        }
        Message m = MessageMapper.outbound(
            channel,
            c,
            request,
            recipient,
            adapter.capabilities().simulated()
        );
        if (reply != null) {
            m.setReplyToMessageId(reply.getId().toHexString());
            m.setReplyToExternalMessageId(reply.getExternalMessageId());
            m.setReplyPreview(reply.getContent().getString("text"));
        }
        if (forwarded != null) {
            m.setForwardedFromMessageId(forwarded.getId().toHexString());
        }
        try {
            m = repository.insert(m);
        } catch (DuplicateKeyException duplicate) {
            if (request.operationId() == null) {
                throw duplicate;
            }
            return repeated(
                repository
                    .findByChannelIdAndOperationId(channel.getId(), request.operationId())
                    .orElseThrow(() -> duplicate),
                c,
                request,
                reply,
                forwarded
            );
        }
        conversations.touch(c, m);
        logs.record(m, MessageLogEvent.SEND_ATTEMPTED);
        if (request.actorUserId() != null) {
            members.requireMessageActor(
                request.actorUserId(),
                request.memberId(),
                conversations.require(conversationId).getAssignedMemberId()
            );
        }
        SendMessageResult result;
        try {
            SendMessageRequest command = new SendMessageRequest(
                m.getId().toHexString(),
                channel.getId(),
                recipient,
                request.content(),
                c.getConversationType(),
                MessageType.TEXT
            );
            if (reply != null) {
                result = adapter.reply(channel, command, reply.getExternalMessageId());
            } else if (forwarded != null) {
                Conversation source = conversations.require(forwarded.getConversationId().toHexString());
                if (request.actorUserId() != null) {
                    members.requireMessageActor(
                        request.actorUserId(),
                        request.memberId(),
                        source.getAssignedMemberId()
                    );
                }
                result = adapter.forward(
                    channel,
                    new ForwardMessageRequest(
                        command,
                        forwarded.getExternalMessageId(),
                        source.getContactPhone()
                    )
                );
            } else {
                result = adapter.send(channel, command);
            }
        } catch (RuntimeException error) {
            String code =
                error instanceof ProviderSendException failure
                    ? failure.code()
                    : "PROVIDER_CONFIGURATION_OR_SEND_FAILURE";
            boolean uncertain = code.endsWith("UNKNOWN");
            m = changeStatus(
                m,
                uncertain ? NormalizedMessageStatus.UNKNOWN : NormalizedMessageStatus.FAILED,
                code
            );
            logs.record(m, uncertain ? MessageLogEvent.SEND_UNKNOWN : MessageLogEvent.SEND_FAILED);
            return response(m);
        }
        // Persist SID before replaying callbacks that may have arrived during the HTTP send.
        repository.providerIds(m, result.providerTrackingId(), result.providerResponseId());
        repository.associateExternalId(m, result.externalMessageId(), result.simulated());
        m.setExternalMessageId(result.externalMessageId());
        m.setSimulated(result.simulated());
        m.setProviderTrackingId(result.providerTrackingId());
        for (var delivery : logs.tracked(m)) {
            repository.associateExternalId(m, delivery.getExternalMessageId(), result.simulated());
            m.setExternalMessageId(delivery.getExternalMessageId());
        }
        m = changeStatus(m, result.status(), null);
        for (var log : logs.callbacks(m)) {
            m = changeStatus(m, log.getStatus(), log.getErrorCode());
        }
        m = repository.findById(m.getId()).orElseThrow();
        logs.record(
            m,
            result.status() == NormalizedMessageStatus.ACCEPTED
                ? MessageLogEvent.SEND_ACCEPTED
                : result.simulated()
                  ? MessageLogEvent.SEND_SIMULATED
                  : MessageLogEvent.SENT
        );
        events.publish(ConversationEventTypes.MESSAGE_SENT, response(m));
        return response(m);
    }

    private NormalizedMessage repeated(
        Message existing,
        Conversation conversation,
        MessageDtos.Send request,
        Message reply,
        Message forwarded
    ) {
        if (
            !Objects.equals(existing.getConversationId(), conversation.getId()) ||
            !Objects.equals(existing.getMemberId(), request.memberId()) ||
            !Objects.equals(existing.getActorUserId(), request.actorUserId()) ||
            !Objects.equals(existing.getContent().getString("text"), request.content()) ||
            !Objects.equals(
                existing.getReplyToMessageId(),
                reply == null ? null : reply.getId().toHexString()
            ) ||
            !Objects.equals(
                existing.getForwardedFromMessageId(),
                forwarded == null ? null : forwarded.getId().toHexString()
            )
        ) {
            throw ApiException.conflict("operationId já utilizado em outra operação");
        }
        return response(existing);
    }

    public void status(WhatsAppChannel channel, NormalizedStatusEvent status) {
        // Persist first, including callbacks that precede SID association; send reconciles them.
        logs.callback(channel, status);
        repository
            .findByChannelIdAndExternalMessageId(channel.getId(), status.externalMessageId())
            .filter(m -> Direction.OUTBOUND.name().equals(m.getDirection()))
            .ifPresent(m -> changeStatus(m, status.status(), status.errorCode()));
    }

    public void echo(WhatsAppChannel channel, NormalizedInboundMessage incoming, Boolean fromApi) {
        logs.echo(channel, incoming);
        var lock = acquireChannel(channel.getId());
        try {
            applyEcho(channel, incoming, fromApi);
        } finally {
            lock.unlock();
        }
    }

    private java.util.concurrent.locks.ReentrantLock acquireChannel(Long id) {
        var lock = channelLocks.computeIfAbsent(id, ignored ->
            new java.util.concurrent.locks.ReentrantLock()
        );
        try {
            if (!lock.tryLock(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw ApiException.conflict(
                    "Canal com outra operação em andamento; consulte o estado antes de repetir"
                );
            }
            return lock;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("Operação interrompida antes do envio");
        }
    }

    private void applyEcho(WhatsAppChannel channel, NormalizedInboundMessage incoming, Boolean fromApi) {
        if (
            repository
                .findByChannelIdAndExternalMessageId(channel.getId(), incoming.externalMessageId())
                .isPresent()
        ) {
            status(
                channel,
                new NormalizedStatusEvent(incoming.externalMessageId(), NormalizedMessageStatus.SENT, null)
            );
        } else if (
            Boolean.TRUE.equals(fromApi) ||
            (fromApi == null &&
                !repository
                    .findByChannelIdAndDirectionAndStatusIn(
                        channel.getId(),
                        Direction.OUTBOUND.name(),
                        List.of(NormalizedMessageStatus.PENDING, NormalizedMessageStatus.UNKNOWN)
                    )
                    .isEmpty())
        ) {
            // Preserve the envelope for bounded retry until an explicit remote identity can be correlated.
            status(
                channel,
                new NormalizedStatusEvent(incoming.externalMessageId(), NormalizedMessageStatus.SENT, null)
            );
            throw ApiException.conflict("Correlação da mensagem externa pendente");
        } else {
            externalOutbound(channel, incoming, fromApi == null ? "WHATSAPP_EXTERNAL" : "WHATSAPP_APP");
        }
    }

    public void delivery(WhatsAppChannel channel, String tracking, NormalizedStatusEvent event) {
        logs.delivery(channel, tracking, event);
        if (tracking != null) {
            repository.findByChannelIdAndProviderTrackingId(channel.getId(), tracking).ifPresent(m -> {
                repository.associateExternalId(m, event.externalMessageId(), false);
            });
        }
        status(channel, event);
    }

    private NormalizedMessage externalOutbound(
        WhatsAppChannel channel,
        NormalizedInboundMessage incoming,
        String origin
    ) {
        var existing = repository.findByChannelIdAndExternalMessageId(
            channel.getId(),
            incoming.externalMessageId()
        );
        if (existing.isPresent()) {
            return response(existing.get());
        }
        Conversation c = conversations.individual(channel, incoming.senderPhone());
        Message message = MessageMapper.inbound(
            channel,
            c,
            incoming,
            providers.resolve(channel).capabilities().simulated()
        );
        message.setDirection(Direction.OUTBOUND.name());
        message.setSenderPhone(channel.getPhoneNumber());
        message.setRecipientPhone(incoming.senderPhone());
        message.setOrigin(origin);
        resolveReply(message);
        message.setStatus(NormalizedMessageStatus.SENT);
        message.setInboundCompleted(true);
        try {
            message = repository.insert(message);
        } catch (DuplicateKeyException duplicate) {
            return response(
                repository
                    .findByChannelIdAndExternalMessageId(channel.getId(), incoming.externalMessageId())
                    .orElseThrow(() -> duplicate)
            );
        }
        conversations.touch(c, message);
        logs.record(message, MessageLogEvent.SENT);
        for (var log : logs.callbacks(message)) {
            message = changeStatus(message, log.getStatus(), log.getErrorCode());
        }
        events.publish(ConversationEventTypes.MESSAGE_SENT, response(message));
        return response(message);
    }

    private void resolveReply(Message message) {
        if (message.getReplyToExternalMessageId() == null) {
            return;
        }
        repository
            .findByChannelIdAndExternalMessageId(
                message.getChannelId(),
                message.getReplyToExternalMessageId()
            )
            .filter(original -> Objects.equals(original.getConversationId(), message.getConversationId()))
            .ifPresent(original -> {
                message.setReplyToMessageId(original.getId().toHexString());
                if (original.getDeletedAt() == null && original.getContent() != null) {
                    message.setReplyPreview(original.getContent().getString("text"));
                }
            });
    }

    private Message changeStatus(Message message, NormalizedMessageStatus next, String errorCode) {
        return statuses.apply(message, next, errorCode);
    }

    private NormalizedMessage response(Message message) {
        return MessageMapper.response(message, providers.messageCapabilities(message.getProvider()));
    }

    public static List<NormalizedMessageStatus> allowedPrevious(NormalizedMessageStatus next) {
        return MessageStatusTransitions.allowedPrevious(next);
    }
}
