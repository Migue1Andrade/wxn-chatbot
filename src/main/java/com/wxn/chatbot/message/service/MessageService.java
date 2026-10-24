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
        ConversationEventPublisher events
    ) {
        this.repository = repository;
        this.conversations = conversations;
        this.channels = channels;
        this.members = members;
        this.providers = providers;
        this.logs = logs;
        this.events = events;
    }

    public List<NormalizedMessage> history(Long companyId, String conversationId, int page, int size) {
        Conversation c = conversations.require(companyId, conversationId);
        return repository
            .findByCompanyIdAndConversationIdOrderByCreatedAtAsc(
                companyId,
                c.getId(),
                ConversationService.page(page, size)
            )
            .stream()
            .map(MessageMapper::response)
            .toList();
    }

    public MessagePage page(Long companyId, String conversationId, int limit, String before) {
        if (limit < 1 || limit > 100) throw ApiException.badRequest("limit deve estar entre 1 e 100");
        Conversation conversation = conversations.require(companyId, conversationId);
        var cursor = before == null ? null : MessageCursor.decode(before);
        var rows = new ArrayList<>(repository.before(companyId, conversation.getId(), cursor, limit + 1));
        boolean more = rows.size() > limit;
        if (more) rows.remove(rows.size() - 1);
        String next = more && !rows.isEmpty() ? MessageCursor.of(rows.get(rows.size() - 1)).encode() : null;
        Collections.reverse(rows);
        return new MessagePage(rows.stream().map(MessageMapper::response).toList(), next, more);
    }

    public NormalizedMessage receive(
        WhatsAppChannel channel,
        NormalizedInboundMessage incoming,
        boolean simulated
    ) {
        // The channel is resolved and authenticated by the ingress service before normalization.
        channels.requireActive(channel.getCompanyId(), channel.getId());
        if (
            !Objects.equals(channel.getPhoneNumber(), incoming.recipientPhone())
        ) throw ApiException.badRequest("Destinatário não corresponde ao canal");
        Optional<Message> existing = repository.findByCompanyIdAndChannelIdAndExternalMessageId(
            channel.getCompanyId(),
            channel.getId(),
            incoming.externalMessageId()
        );
        if (existing.isPresent()) return existingInbound(channel, existing.get(), incoming);
        Conversation c = conversations.individual(channel, incoming.senderPhone());
        Message m = MessageMapper.inbound(channel, c, incoming, simulated);
        try {
            m = repository.insert(m);
        } catch (DuplicateKeyException concurrent) {
            return existingInbound(
                channel,
                repository
                    .findByCompanyIdAndChannelIdAndExternalMessageId(
                        channel.getCompanyId(),
                        channel.getId(),
                        incoming.externalMessageId()
                    )
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
        ) throw ApiException.conflict("Identificador externo já vinculado a outra mensagem");
        return Boolean.FALSE.equals(message.getInboundCompleted())
            ? finishInbound(channel, message, incoming)
            : MessageMapper.response(message);
    }

    private NormalizedMessage finishInbound(
        WhatsAppChannel channel,
        Message message,
        NormalizedInboundMessage incoming
    ) {
        Conversation conversation = conversations.require(
            channel.getCompanyId(),
            message.getConversationId().toHexString()
        );
        conversations.updateContact(conversation, incoming.senderDisplayName());
        conversations.touch(conversation, message);
        logs.record(message, MessageLogEvent.RECEIVED);
        if (repository.completeInbound(message)) events.publish(
            ConversationEventTypes.MESSAGE_RECEIVED,
            message.getCompanyId(),
            MessageMapper.response(message)
        );
        return MessageMapper.response(message);
    }

    public NormalizedMessage send(Long companyId, String conversationId, MessageDtos.Send request) {
        Conversation c = conversations.require(companyId, conversationId);
        if (c.operationalStatus() == ConversationStatus.CLOSED) throw ApiException.conflict(
            "Reabra o atendimento antes de enviar mensagens"
        );
        WhatsAppChannel channel = channels.requireActive(companyId, request.channelId());
        if (
            !Objects.equals(c.getChannelId(), channel.getId()) || c.getProvider() != channel.getProvider()
        ) throw ApiException.badRequest("Conversa pertence a outro canal ou é legada sem canal");
        members.requireActive(companyId, request.memberId());
        String recipient = PhoneNumbers.required(c.getContactPhone());
        if (
            request.content() == null || request.content().isBlank() || request.content().length() > 1600
        ) throw ApiException.badRequest("Texto obrigatório, até 1600 caracteres");
        if (c.getConversationType() != ConversationType.INDIVIDUAL) throw ApiException.badRequest(
            "Envio disponível apenas em conversas individuais"
        );
        WhatsAppProvider adapter = providers.forSend(channel, c.getConversationType(), MessageType.TEXT);
        Message m = repository.insert(
            MessageMapper.outbound(channel, c, request, recipient, adapter.capabilities().simulated())
        );
        conversations.touch(c, m);
        logs.record(m, MessageLogEvent.SEND_ATTEMPTED);
        SendMessageResult result;
        try {
            result = adapter.send(
                channel,
                new SendMessageRequest(
                    m.getId().toHexString(),
                    companyId,
                    channel.getId(),
                    recipient,
                    request.content(),
                    c.getConversationType(),
                    MessageType.TEXT
                )
            );
        } catch (RuntimeException error) {
            String code =
                error instanceof ProviderSendException failure
                    ? failure.code()
                    : "PROVIDER_CONFIGURATION_OR_SEND_FAILURE";
            m = changeStatus(m, NormalizedMessageStatus.FAILED, code);
            logs.record(m, MessageLogEvent.SEND_FAILED);
            return MessageMapper.response(m);
        }
        // Persist SID before replaying callbacks that may have arrived during the HTTP send.
        repository.associateExternalId(m, result.externalMessageId(), result.simulated());
        m.setExternalMessageId(result.externalMessageId());
        m.setSimulated(result.simulated());
        m = changeStatus(m, result.status(), null);
        for (var log : logs.callbacks(m)) m = changeStatus(m, log.getStatus(), log.getErrorCode());
        m = repository.findByIdAndCompanyId(m.getId(), m.getCompanyId()).orElseThrow();
        logs.record(m, MessageLogEvent.SENT);
        events.publish(ConversationEventTypes.MESSAGE_SENT, companyId, MessageMapper.response(m));
        return MessageMapper.response(m);
    }

    public void status(WhatsAppChannel channel, NormalizedStatusEvent status) {
        // Persist first, including callbacks that precede SID association; send reconciles them.
        logs.callback(channel, status);
        repository
            .findByCompanyIdAndChannelIdAndExternalMessageId(
                channel.getCompanyId(),
                channel.getId(),
                status.externalMessageId()
            )
            .filter(m -> Direction.OUTBOUND.name().equals(m.getDirection()))
            .ifPresent(m -> changeStatus(m, status.status(), status.errorCode()));
    }

    private Message changeStatus(Message m, NormalizedMessageStatus next, String errorCode) {
        List<NormalizedMessageStatus> prior = allowedPrevious(next);
        if (prior.isEmpty()) return repository
            .findByIdAndCompanyId(m.getId(), m.getCompanyId())
            .orElseThrow();
        Message updated = repository.updateStatus(m, next, errorCode, prior).orElse(null);
        if (updated == null) return repository
            .findByIdAndCompanyId(m.getId(), m.getCompanyId())
            .orElseThrow();
        events.publish(
            ConversationEventTypes.MESSAGE_STATUS_UPDATED,
            updated.getCompanyId(),
            MessageMapper.response(updated)
        );
        return updated;
    }

    public static List<NormalizedMessageStatus> allowedPrevious(NormalizedMessageStatus next) {
        return MessageStatusTransitions.allowedPrevious(next);
    }
}
