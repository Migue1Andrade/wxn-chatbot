package com.wxn.chatbot.openbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.service.ConversationManagementService;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OpenBotInactivityTest {

    private static final Long BOT_ID = 77L;
    private static final Long CHANNEL_ID = 23L;

    @Test
    void controlledTimeWarnsAtTwentyMinutesOnlyOnce() {
        var serviceFixture = fixture();
        var lastInbound = Instant.parse("2025-03-01T12:00:00Z");
        var now = lastInbound.plusSeconds(20 * 60L);
        when(serviceFixture.conversations.updateBotWarning(any(), any(), eq(BOT_ID))).thenReturn(1, 0);

        serviceFixture.service.processInactivityAt(now);
        serviceFixture.service.processInactivityAt(now.plusSeconds(1));

        verify(serviceFixture.messages).sendAsAssignedBot(
            eq(serviceFixture.conversation.getId().toHexString()),
            argThat(request -> request.content().contains("em 10 minutos"))
        );
        verify(serviceFixture.management, never()).cancelBotForInactivity(anyString(), any());
    }

    @Test
    void controlledTimeEndsBotSessionAtThirtyMinutes() {
        var fixture = fixture();
        var lastInbound = Instant.parse("2025-03-01T12:00:00Z");

        fixture.service.processInactivityAt(lastInbound.plusSeconds(30 * 60L));

        verify(fixture.messages).sendAsAssignedBot(
            eq(fixture.conversation.getId().toHexString()),
            argThat(request -> request.content().contains("encerrado por inatividade"))
        );
        verify(fixture.management).cancelBotForInactivity(
            eq(fixture.conversation.getId().toHexString()),
            any()
        );
    }

    @Test
    void onlyClearPositiveAnswersConfirmResolution() {
        assertThat(OpenBotSupportService.isPositiveConfirmation("Sim, obrigado!")).isTrue();
        assertThat(OpenBotSupportService.isPositiveConfirmation("NÃO, ainda tenho dúvida")).isFalse();
        assertThat(OpenBotSupportService.isPositiveConfirmation("talvez")).isFalse();
    }

    private Fixture fixture() {
        var properties = new SupportProperties(true, "", "", "", BOT_ID, CHANNEL_ID);
        var members = mock(MemberRepository.class);
        var conversations = mock(ConversationService.class);
        var messageRepository = mock(MessageRepository.class);
        var activityRepository = mock(
            com.wxn.chatbot.conversation.repository.ConversationActivityRepository.class
        );
        var management = mock(ConversationManagementService.class);
        var messages = mock(MessageService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MessageService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(messages);
        when(messages.withChannelWriteLock(eq(CHANNEL_ID), any())).thenAnswer(call ->
            ((java.util.function.Supplier<?>) call.getArgument(1)).get()
        );
        var conversation = new Conversation();
        conversation.setId(new ObjectId());
        conversation.setChannelId(CHANNEL_ID);
        conversation.setAssignedMemberId(BOT_ID);
        conversation.setStatus("WAITING_CUSTOMER");
        conversation.setVersion(4L);
        conversation.setLastInboundAt(
            OffsetDateTime.ofInstant(Instant.parse("2025-03-01T12:00:00Z"), ZoneOffset.UTC)
        );
        when(conversations.activeBotSessions(BOT_ID)).thenReturn(List.of(conversation));
        when(conversations.withWriteLock(anyString(), any())).thenAnswer(call ->
            ((java.util.function.Supplier<?>) call.getArgument(1)).get()
        );
        when(conversations.require(conversation.getId().toHexString())).thenReturn(conversation);
        when(conversations.updateBotWarning(any(), any(), eq(BOT_ID))).thenReturn(1);
        var sent = mock(NormalizedMessage.class);
        when(sent.status()).thenReturn(NormalizedMessageStatus.SIMULATED);
        when(messages.sendAsAssignedBot(anyString(), any())).thenReturn(sent);
        var service = new OpenBotSupportService(
            properties,
            members,
            conversations,
            provider,
            messageRepository,
            activityRepository,
            management
        );
        return new Fixture(service, conversations, management, messages, conversation);
    }

    private record Fixture(
        OpenBotSupportService service,
        ConversationService conversations,
        ConversationManagementService management,
        MessageService messages,
        Conversation conversation
    ) {}
}
