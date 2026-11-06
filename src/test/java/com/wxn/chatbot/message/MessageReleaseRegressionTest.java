package com.wxn.chatbot.message;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.auth.service.*;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.ConversationEventPublisher;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.member.service.MemberService;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.repository.*;
import com.wxn.chatbot.message.service.*;
import com.wxn.chatbot.messagelog.service.MessageLogService;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.operations.ChannelOperationsRepository;
import com.wxn.chatbot.whatsapp.provider.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;

class MessageReleaseRegressionTest {

    private final MessageRepository repository = mock(MessageRepository.class);
    private final ConversationService conversations = mock(ConversationService.class);
    private final ChannelService channels = mock(ChannelService.class);
    private final WhatsAppProviderResolver providers = mock(WhatsAppProviderResolver.class);
    private final WhatsAppProvider adapter = mock(WhatsAppProvider.class);
    private final ConversationEventPublisher events = mock(ConversationEventPublisher.class);
    private final MessageLogService logs = mock(MessageLogService.class);
    private final Conversation conversation = new Conversation();
    private final WhatsAppChannel channel = mock(WhatsAppChannel.class);
    private final AtomicReference<Message> stored = new AtomicReference<>();
    private MessageService service;

    @BeforeEach
    void setup() {
        conversation.setId(new ObjectId());
        conversation.setChannelId(2L);
        conversation.setProvider(ProviderType.MOCK);
        conversation.setStatus("IN_PROGRESS");
        conversation.setConversationType(ConversationType.INDIVIDUAL);
        conversation.setContactPhone("+12025550100");
        when(conversations.withWriteLock(anyString(), any())).thenAnswer(call ->
            ((java.util.function.Supplier<?>) call.getArgument(1)).get()
        );
        when(conversations.require(conversation.getId().toHexString())).thenReturn(conversation);
        when(channel.getId()).thenReturn(2L);
        when(channel.getPhoneNumber()).thenReturn("+12025550190");
        when(channel.getProvider()).thenReturn(ProviderType.MOCK);
        when(channels.requireActive(2L)).thenReturn(channel);
        when(providers.forSend(channel, ConversationType.INDIVIDUAL, MessageType.TEXT)).thenReturn(adapter);
        when(adapter.send(eq(channel), any())).thenReturn(
            new SendMessageResult("mock-remote", NormalizedMessageStatus.SIMULATED, true)
        );
        when(adapter.capabilities()).thenReturn(new ProviderCapabilities(true, false, false, false, true));
        when(repository.insert(any(Message.class))).thenAnswer(call -> {
            Message m = call.getArgument(0);
            m.setId(new ObjectId());
            stored.set(m);
            return m;
        });
        when(repository.findById(any(ObjectId.class))).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(repository.updateStatus(any(), any(), any(), any())).thenAnswer(call -> {
            Message m = call.getArgument(0);
            NormalizedMessageStatus next = call.getArgument(1);
            Collection<?> prior = call.getArgument(3);
            if (!prior.contains(m.getStatus())) {
                return Optional.empty();
            }
            m.setStatus(next);
            m.setErrorCode(call.getArgument(2));
            return Optional.of(m);
        });
        service = new MessageService(
            repository,
            conversations,
            channels,
            mock(MemberService.class),
            providers,
            logs,
            events,
            new com.wxn.chatbot.message.service.MessageStatusService(
                repository,
                conversations,
                providers,
                events
            )
        );
    }

    @Test
    void repeatedClientOperationNeverDispatchesTwice() {
        var request = new MessageDtos.Send(2L, null, "Olá", "same-operation-00001");
        service.send(conversation.getId().toHexString(), request);
        // Simulate the response lost before reaching the browser: the attempt remains durable.
        when(repository.findByChannelIdAndOperationId(2L, request.operationId())).thenReturn(
            Optional.of(stored.get())
        );
        service.send(conversation.getId().toHexString(), request);
        verify(adapter, times(1)).send(eq(channel), any());
        verify(repository, times(1)).insert(any(Message.class));
    }

    @Test
    void uncertainTimeoutPreservesAttemptAndDoesNotRetry() {
        when(adapter.send(eq(channel), any())).thenThrow(
            new ProviderSendException("PROVIDER_CONFIRMATION_UNKNOWN")
        );
        var result = service.send(
            conversation.getId().toHexString(),
            new MessageDtos.Send(2L, null, "Olá", "timeout-operation-001")
        );
        assertThat(result.status()).isEqualTo(NormalizedMessageStatus.UNKNOWN);
        assertThat(stored.get().getContent().getString("text")).isEqualTo("Olá");
        var order = inOrder(repository, adapter);
        order.verify(repository).insert(any(Message.class));
        order.verify(adapter).send(eq(channel), any());
        verify(adapter, times(1)).send(eq(channel), any());
    }

    @Test
    void lateDeliveredCallbackCannotRegressRead() {
        var message = new Message();
        message.setId(new ObjectId());
        message.setConversationId(conversation.getId());
        message.setChannelId(2L);
        message.setDirection("OUTBOUND");
        message.setStatus(NormalizedMessageStatus.READ);
        stored.set(message);
        when(repository.findByChannelIdAndExternalMessageId(2L, "remote-id")).thenReturn(
            Optional.of(message)
        );
        service.status(
            channel,
            new NormalizedStatusEvent("remote-id", NormalizedMessageStatus.DELIVERED, null)
        );
        assertThat(message.getStatus()).isEqualTo(NormalizedMessageStatus.READ);
        verifyNoInteractions(events);
    }

    @Test
    void revokedAttendantCannotReachMutationProvider() {
        var access = mock(ConversationAuthorization.class);
        var operations = mock(MessageMutationRepository.class);
        var message = new Message();
        message.setId(new ObjectId());
        message.setConversationId(conversation.getId());
        when(repository.findById(message.getId())).thenReturn(Optional.of(message));
        var actor = new AccountSessions.Identity(5L, "fixture", "Fixture", UserRole.ATTENDANT, 8L);
        doThrow(
            new com.wxn.chatbot.common.ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Acesso revogado"
            )
        )
            .when(access)
            .require(conversation.getId().toHexString(), actor);
        var mutations = new MessageMutationService(
            repository,
            operations,
            access,
            conversations,
            channels,
            providers,
            mock(ChannelOperationsRepository.class),
            events
        );
        assertThatThrownBy(() ->
            mutations.mutate(message.getId().toHexString(), "EDIT_TEXT", "operation-12345678", "Novo", actor)
        ).isInstanceOf(com.wxn.chatbot.common.ApiException.class);
        verifyNoInteractions(operations);
        verify(adapter, never()).mutate(any(), any(), any(), any(), any());
    }

    @Test
    void acceptedMutationDoesNotReplaceConfirmedContent() {
        var mongo = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        var mutations = new MessageMutationRepository(mongo);
        var message = new Message();
        message.setId(new ObjectId());
        message.setChannelId(2L);
        message.setContent(new Document("text", "Anterior"));
        mutations.finish(message, "EDIT_TEXT", "operation-12345678", "Novo", "ACCEPTED");
        var update = org.mockito.ArgumentCaptor.forClass(
            org.springframework.data.mongodb.core.query.Update.class
        );
        verify(mongo).findAndModify(any(), update.capture(), any(), eq(Message.class));
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class)).doesNotContainKey(
            "content.text"
        );
        assertThat(message.getContent().getString("text")).isEqualTo("Anterior");
    }
}
