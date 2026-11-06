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
        message.setOrigin("CONTACT");
        message.setExternalContactIdentity(incoming.externalContactIdentity());
        message.setReplyToExternalMessageId(incoming.replyToExternalMessageId());
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
        message.setActorUserId(request.actorUserId());
        message.setOrigin("WXN");
        message.setOperationId(request.operationId());
        message.setType(MessageType.TEXT.name());
        message.setContent(new Document("text", request.content()));
        message.setStatus(NormalizedMessageStatus.PENDING);
        message.setSimulated(simulated);
        return message;
    }

    private static Message base(WhatsAppChannel channel, Conversation conversation, Direction direction) {
        Message message = new Message();

        message.setChannelId(channel.getId());
        message.setConversationId(conversation.getId());
        message.setProvider(channel.getProvider());
        message.setDirection(direction.name());
        message.setCreatedAt(OffsetDateTime.now());
        message.setUpdatedAt(message.getCreatedAt());
        return message;
    }

    public static NormalizedMessage response(Message m) {
        return response(m, java.util.Set.of());
    }

    public static NormalizedMessage response(Message m, java.util.Set<MessageCapability> capabilities) {
        java.util.Set<MessageCapability> applicable = new java.util.HashSet<>(capabilities);
        applicable.remove(MessageCapability.SEND_TEXT);
        applicable.remove(MessageCapability.MESSAGE_STATUS);
        applicable.removeAll(
            java.util.Set.of(
                MessageCapability.CONFIGURE_WEBHOOKS,
                MessageCapability.CONNECTION_STATUS,
                MessageCapability.QR_CODE,
                MessageCapability.RESTART_INSTANCE,
                MessageCapability.DISCONNECT_INSTANCE
            )
        );
        if (
            !"TEXT".equals(m.getType()) ||
            m.getDeletedAt() != null ||
            (m.getDeleteState() != null && !"FAILED".equals(m.getDeleteState())) ||
            m.getExternalMessageId() == null ||
            m.getStatus() == NormalizedMessageStatus.PENDING ||
            m.getStatus() == NormalizedMessageStatus.FAILED ||
            m.getStatus() == NormalizedMessageStatus.UNKNOWN
        ) {
            applicable.clear();
        }
        if (!"OUTBOUND".equals(m.getDirection())) {
            applicable.remove(MessageCapability.DELETE_FOR_EVERYONE);
            applicable.remove(MessageCapability.EDIT_TEXT);
        }

        if ("OUTBOUND".equals(m.getDirection())) {
            applicable.remove(MessageCapability.MARK_READ);
        }
        if (
            m.getPendingMutation() != null &&
            java.util.Set.of("PENDING", "ACCEPTED", "UNKNOWN").contains(
                m.getPendingMutation().getString("state")
            )
        ) {
            applicable.clear();
        }
        return new NormalizedMessage(
            m.getId().toHexString(),
            m.getChannelId(),
            m.getConversationId() == null ? null : m.getConversationId().toHexString(),
            m.getProvider(),
            m.getExternalMessageId(),
            direction(m.getDirection()),
            m.getSenderPhone(),
            m.getRecipientPhone(),
            type(m.getType()),
            m.getContent() == null || m.getDeletedAt() != null ? null : new LinkedHashMap<>(m.getContent()),
            m.getStatus(),
            m.getMemberId(),
            m.getSimulated(),
            m.getErrorCode(),
            m.getCreatedAt() == null ? null : m.getCreatedAt().toInstant(),
            m.getUpdatedAt() == null ? null : m.getUpdatedAt().toInstant(),
            m.getOperationId(),
            m.getReplyToMessageId(),
            m.getReplyToExternalMessageId(),
            m.getReplyPreview(),
            m.getForwardedFromMessageId(),
            m.getOrigin(),
            m.getDeletedAt() == null ? null : m.getDeletedAt().toInstant(),
            m.getDeletionIntent(),
            m.getDeleteState(),
            m.getPendingMutation() == null || m.getDeletedAt() != null
                ? null
                : new LinkedHashMap<>(m.getPendingMutation()),
            m.getReaction(),
            m.getContactReaction(),
            m.getEditedAt() == null ? null : m.getEditedAt().toInstant(),
            m.getVersion(),
            java.util.Set.copyOf(applicable)
        );
    }

    private static Direction direction(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Direction.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static MessageType type(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MessageType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return MessageType.UNKNOWN;
        }
    }
}
