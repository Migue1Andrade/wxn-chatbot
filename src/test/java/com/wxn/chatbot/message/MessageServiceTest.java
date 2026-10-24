package com.wxn.chatbot.message;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.conversation.event.ConversationEventPublisher;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import com.wxn.chatbot.whatsapp.provider.*;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.messagelog.service.MessageLogService;
import com.wxn.chatbot.common.ApiException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MessageServiceTest {
    @Test void duplicateInboundReturnsExistingMessageWithoutAnotherConversationOrLog() {
        var repository = mock(MessageRepository.class); var conversations = mock(ConversationService.class);
        var channels = mock(ChannelService.class); var logs = mock(MessageLogService.class); var events = mock(ConversationEventPublisher.class);
        var channel = mock(WhatsAppChannel.class); when(channel.getCompanyId()).thenReturn(1L); when(channel.getId()).thenReturn(2L); when(channel.getPhoneNumber()).thenReturn("+14155238886");
        var existing = new Message(); existing.setId(new ObjectId()); existing.setCompanyId(1L); existing.setExternalMessageId("SMtest");
        when(repository.findByCompanyIdAndChannelIdAndExternalMessageId(1L, 2L, "SMtest")).thenReturn(Optional.of(existing));
        var service = new MessageService(repository, conversations, channels, mock(MemberService.class), mock(WhatsAppProviderResolver.class), logs, events);
        var result = service.receive(channel, new NormalizedInboundMessage("SMtest", "+5581999999999", "+14155238886", MessageType.TEXT, Map.of("text", "Olá"), Instant.now()), false);
        assertThat(result.id()).isEqualTo(existing.getId().toHexString()); verify(repository, never()).insert(any(Message.class));
        verifyNoInteractions(conversations, logs, events);
    }
    @Test void rejectsConversationChannelMismatchBeforeProviderOrPersistence() {
        var repository = mock(MessageRepository.class); var conversations = mock(ConversationService.class); var channels = mock(ChannelService.class);
        var providers = mock(WhatsAppProviderResolver.class); var members = mock(MemberService.class);
        var conversation = new Conversation(); conversation.setChannelId(3L); conversation.setProvider(ProviderType.MOCK);
        when(conversations.require(1L, "conversation")).thenReturn(conversation);
        var channel = mock(WhatsAppChannel.class); when(channel.getId()).thenReturn(2L); when(channel.getProvider()).thenReturn(ProviderType.MOCK);
        when(channels.requireActive(1L, 2L)).thenReturn(channel);
        var service = new MessageService(repository, conversations, channels, members, providers, mock(MessageLogService.class), mock(ConversationEventPublisher.class));
        assertThatThrownBy(() -> service.send(1L, "conversation", new MessageDtos.Send(2L, 4L, "Olá"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository, providers, members);
    }
}
