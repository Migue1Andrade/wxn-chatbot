package com.wxn.chatbot.message.mapper;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.bson.Document;

public final class MessageMapper {

    private MessageMapper() {}

    public static Message inbound(
        WhatsAppChannel channel,
        Conversation conversation,
        NormalizedInboundMessage incoming,
        boolean simulated
    ) {
        Message message = base(channel, conversation, Direction.INBOUND);
        message.setInboundCompleted(false);
        message.setExternalMessageId(incoming.externalMessageId());
        message.setSenderPhone(incoming.senderPhone());
        message.setRecipientPhone(incoming.recipientPhone());
        message.setType(incoming.messageType().name());
        message.setContent(new Document(incoming.content()));
        message.setStatus(NormalizedMessageStatus.RECEIVED);
        message.setSimulated(simulated);
        message.setCreatedAt(incoming.occurredAt().atOffset(ZoneOffset.UTC));
        return message;
    }

    public static Message outbound(
        WhatsAppChannel channel,
        Conversation conversation,
        MessageDtos.Send request,
        String recipient,
        boolean simulated
    ) {
        Message message = base(channel, conversation, Direction.OUTBOUND);
        message.setSenderPhone(channel.getPhoneNumber());
        message.setRecipientPhone(recipient);
        message.setMemberId(request.memberId());
        message.setType(MessageType.TEXT.name());
        message.setContent(new Document("text", request.content()));
        message.setStatus(NormalizedMessageStatus.PENDING);
        message.setSimulated(simulated);
        return message;
    }

    private static Message base(WhatsAppChannel channel, Conversation conversation, Direction direction) {
        Message message = new Message();
        message.setCompanyId(channel.getCompanyId());
        message.setChannelId(channel.getId());
        message.setConversationId(conversation.getId());
        message.setProvider(channel.getProvider());
        message.setDirection(direction.name());
        message.setCreatedAt(OffsetDateTime.now());
        message.setUpdatedAt(message.getCreatedAt());
        return message;
    }

    public static NormalizedMessage response(Message m) {
        return new NormalizedMessage(
            m.getId().toHexString(),
            m.getCompanyId(),
            m.getChannelId(),
            m.getConversationId() == null ? null : m.getConversationId().toHexString(),
            m.getProvider(),
            m.getExternalMessageId(),
            direction(m.getDirection()),
            m.getSenderPhone(),
            m.getRecipientPhone(),
            type(m.getType()),
            m.getContent() == null ? null : new LinkedHashMap<>(m.getContent()),
            m.getStatus(),
            m.getMemberId(),
            m.getSimulated(),
            m.getErrorCode(),
            m.getCreatedAt() == null ? null : m.getCreatedAt().toInstant(),
            m.getUpdatedAt() == null ? null : m.getUpdatedAt().toInstant()
        );
    }

    private static Direction direction(String value) {
        if (value == null) return null;
        try {
            return Direction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static MessageType type(String value) {
        if (value == null) return null;
        try {
            return MessageType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return MessageType.UNKNOWN;
        }
    }
}
