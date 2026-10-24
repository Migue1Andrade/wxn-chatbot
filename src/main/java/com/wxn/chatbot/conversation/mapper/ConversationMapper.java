package com.wxn.chatbot.conversation.mapper;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class ConversationMapper {

    private ConversationMapper() {}

    public static Conversation individual(WhatsAppChannel channel, String phone, String key) {
        Conversation conversation = new Conversation();
        conversation.setCompanyId(channel.getCompanyId());
        conversation.setChannelId(channel.getId());
        conversation.setProvider(channel.getProvider());
        conversation.setConversationKey(key);
        conversation.setConversationType(ConversationType.INDIVIDUAL);
        conversation.setContactPhone(phone);
        conversation.setParticipants(List.of(phone));
        conversation.setStatus(ConversationStatus.PENDING.name());
        conversation.setCreatedAt(OffsetDateTime.now());
        conversation.setUpdatedAt(conversation.getCreatedAt());
        conversation.setVersion(0L);
        return conversation;
    }

    public static NormalizedConversation response(Conversation c) {
        return response(c, null);
    }

    public static NormalizedConversation response(Conversation c, Long unreadCount) {
        return new NormalizedConversation(
            c.getId().toHexString(),
            c.getCompanyId(),
            c.getChannelId(),
            c.getProvider(),
            c.getExternalChatId(),
            c.getConversationType(),
            c.getContactPhone(),
            c.getParticipants(),
            c.operationalStatus(),
            c.getAssignedMemberId(),
            instant(c.getCreatedAt()),
            instant(c.getUpdatedAt()),
            c.getLastMessagePreview(),
            instant(c.getLastMessageAt()),
            instant(c.getClosedAt()),
            c.revision(),
            unreadCount,
            c.getDisplayName() == null || c.getDisplayName().isBlank()
                ? c.getContactPhone()
                : c.getDisplayName()
        );
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
