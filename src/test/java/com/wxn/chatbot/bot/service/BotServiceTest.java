package com.wxn.chatbot.bot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.bot.client.BotClient;
import com.wxn.chatbot.bot.client.BotUnavailableException;
import com.wxn.chatbot.bot.config.BotConfiguration;
import com.wxn.chatbot.bot.document.BotRun;
import com.wxn.chatbot.bot.dto.BotDtos;
import com.wxn.chatbot.bot.repository.BotRunRepository;
import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.model.ConversationTransition;
import com.wxn.chatbot.conversation.service.ConversationActivityService;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.conversation.service.ConversationTransitionService;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.department.service.DepartmentService;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.dto.MessagePage;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.model.Direction;
import com.wxn.chatbot.whatsapp.model.MessageType;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class BotServiceTest {

    private static final long BOT_MEMBER_ID = 7L;
    private static final long HUMAN_MEMBER_ID = 99L;
    private static final String API_KEY = "test-key-with-at-least-32-characters";
    private static final ObjectId CONVERSATION_ID = new ObjectId();
    private static final String CONVERSATION = CONVERSATION_ID.toHexString();
    private static final ObjectId INBOUND_ID = new ObjectId();

    private BotClient client;
    private BotRunRepository runs;
    private ConversationService conversations;
    private ConversationTransitionService transitions;
    private ConversationActivityService activities;
    private DepartmentService departments;
    private MessageService messages;
    private BotService service;

    @BeforeEach
    void setup() {
        client = mock(BotClient.class);
        runs = mock(BotRunRepository.class);
        conversations = mock(ConversationService.class);
        transitions = mock(ConversationTransitionService.class);
        activities = mock(ConversationActivityService.class);
        departments = mock(DepartmentService.class);
        messages = mock(MessageService.class);
        service = service(true);
        when(departments.list()).thenReturn(
            List.of(
                department(3L, "Financeiro", ActiveStatus.ACTIVE),
                department(4L, "Arquivado", ActiveStatus.INACTIVE)
            )
        );
        var sent = mock(NormalizedMessage.class);
        when(sent.id()).thenReturn("reply-message-id");
        when(messages.send(anyString(), any())).thenReturn(sent);
        when(transitions.returnToQueue(any(), anyLong(), any(), any(), any(), any())).thenReturn(
            Optional.of(conversation(null))
        );
        when(messages.page(anyString(), anyInt(), isNull())).thenReturn(
            new MessagePage(List.of(), null, false)
        );
    }

    @Test
    void historySendsEarlierTextMessagesInOrderWithoutTheCurrentOne() {
        ownedByBot();
        when(client.reply(any())).thenReturn(reply(false, null, null));
        var now = Instant.parse("2026-11-21T10:05:00Z");
        var current = inbound("E o boleto vence quando?");
        when(current.createdAt()).thenReturn(now);
        var deleted = stored(Direction.OUTBOUND, "apagada", now.minusSeconds(50));
        when(deleted.deletedAt()).thenReturn(now.minusSeconds(40));
        var media = stored(Direction.INBOUND, "foto", now.minusSeconds(30));
        when(media.messageType()).thenReturn(MessageType.MEDIA);
        var page = List.of(
            stored(Direction.INBOUND, "Meu nome é Ana", now.minusSeconds(120)),
            stored(Direction.OUTBOUND, "Olá, Ana!", now.minusSeconds(100)),
            deleted,
            media,
            current,
            stored(Direction.INBOUND, "mensagem mais nova", now.plusSeconds(5))
        );
        when(messages.page(CONVERSATION, 50, null)).thenReturn(new MessagePage(page, null, false));

        service.handleInbound(current);

        var request = ArgumentCaptor.forClass(BotDtos.ReplyRequest.class);
        verify(client).reply(request.capture());
        assertThat(request.getValue().history()).containsExactly(
            new BotDtos.HistoryMessage("INBOUND", "Meu nome é Ana", now.minusSeconds(120)),
            new BotDtos.HistoryMessage("OUTBOUND", "Olá, Ana!", now.minusSeconds(100))
        );
        assertThat(recordedRun().historyMessages()).isEqualTo(2);
    }

    @Test
    void pendingConversationIsAssignedToBotAndReplyIsSent() {
        var pending = conversation(null);
        var claimed = conversation(BOT_MEMBER_ID);
        when(conversations.require(CONVERSATION)).thenReturn(pending, claimed);
        when(transitions.attempt(eq(pending), eq(0L), any())).thenReturn(Optional.of(claimed));
        when(client.reply(any())).thenReturn(reply(false, null, null));

        service.handleInbound(inbound("Qual o horário?"));

        var transition = ArgumentCaptor.forClass(ConversationTransition.class);
        verify(transitions).attempt(eq(pending), eq(0L), transition.capture());
        assertThat(transition.getValue().status()).isEqualTo(ConversationStatus.IN_PROGRESS);
        assertThat(transition.getValue().memberId()).isEqualTo(BOT_MEMBER_ID);

        var request = ArgumentCaptor.forClass(BotDtos.ReplyRequest.class);
        verify(client).reply(request.capture());
        assertThat(request.getValue().message()).isEqualTo("Qual o horário?");
        assertThat(request.getValue().inboundMessageId()).isEqualTo(INBOUND_ID.toHexString());
        assertThat(request.getValue().departments()).containsExactly(
            new BotDtos.Department(3L, "Financeiro", "Setor Financeiro")
        );

        var send = ArgumentCaptor.forClass(MessageDtos.Send.class);
        verify(messages).send(eq(CONVERSATION), send.capture());
        assertThat(send.getValue().memberId()).isEqualTo(BOT_MEMBER_ID);
        assertThat(send.getValue().content()).isEqualTo("Funcionamos das 8h às 18h.");
        assertThat(send.getValue().operationId()).isEqualTo("bot-reply-" + INBOUND_ID.toHexString());
        verify(transitions, never()).returnToQueue(any(), anyLong(), any(), any(), any(), any());

        var run = recordedRun();
        assertThat(run.outcome()).isEqualTo("REPLIED");
        assertThat(run.replyMessageId()).isEqualTo("reply-message-id");
        assertThat(run.model()).isEqualTo("claude-haiku-5-5");
        assertThat(run.requestId()).isEqualTo("req_test123");
        assertThat(run.toolCalls()).containsExactly("buscar_conhecimento");
        assertThat(run.retrieved()).containsExactly("entregas.md | Entregas > Dias e horários");
        assertThat(run.inputTokens()).isEqualTo(1000);
    }

    @Test
    void conversationWithHumanIsIgnored() {
        when(conversations.require(CONVERSATION)).thenReturn(conversation(HUMAN_MEMBER_ID));

        service.handleInbound(inbound("Olá"));

        verifyNoInteractions(client, messages, runs);
        verify(transitions, never()).attempt(any(), anyLong(), any());
    }

    @Test
    void disabledBotDoesNothing() {
        service(false).handleInbound(inbound("Olá"));

        verifyNoInteractions(client, messages, runs, conversations);
    }

    @Test
    void nonTextMessageIsIgnored() {
        var media = inbound("legenda");
        when(media.messageType()).thenReturn(MessageType.MEDIA);

        service.handleInbound(media);

        verifyNoInteractions(client, messages, runs, conversations);
    }

    @Test
    void handoffRepliesAndThenReturnsConversationToQueueWithDepartment() {
        ownedByBot();
        when(client.reply(any())).thenReturn(reply(true, 3L, "Cobrança em duplicidade"));

        service.handleInbound(inbound("Fui cobrado duas vezes"));

        var order = inOrder(messages, transitions);
        order.verify(messages).send(eq(CONVERSATION), any());
        order
            .verify(transitions)
            .returnToQueue(
                any(Conversation.class),
                eq(0L),
                eq(3L),
                eq(ConversationActivityType.BOT_HANDOFF),
                eq(BOT_MEMBER_ID),
                eq("Setor Financeiro: Cobrança em duplicidade")
            );
        var run = recordedRun();
        assertThat(run.outcome()).isEqualTo("HANDED_OFF");
        assertThat(run.departmentId()).isEqualTo(3L);
    }

    @Test
    void guardrailHandoffWithoutDepartmentIsAcceptedEvenWithDepartmentsAvailable() {
        ownedByBot();
        when(client.reply(any())).thenReturn(
            new BotDtos.ReplyResponse(
                BotService.FALLBACK_MESSAGE,
                "encaminhamento_automatico",
                true,
                null,
                "Mensagem longa demais para o atendimento automático.",
                new BotDtos.Run(
                    "nenhum",
                    0,
                    0,
                    0,
                    0,
                    0,
                    3,
                    null,
                    "blocked",
                    List.of("input_too_long"),
                    List.of(),
                    List.of(),
                    List.of()
                )
            )
        );

        service.handleInbound(inbound("Texto muito longo..."));

        verify(transitions).returnToQueue(
            any(),
            anyLong(),
            isNull(),
            eq(ConversationActivityType.BOT_HANDOFF),
            eq(BOT_MEMBER_ID),
            eq("Mensagem longa demais para o atendimento automático.")
        );
        var run = recordedRun();
        assertThat(run.outcome()).isEqualTo("HANDED_OFF");
        assertThat(run.fallbackStage()).isEqualTo("blocked");
        assertThat(run.guardrails()).containsExactly("input_too_long");
    }

    @Test
    void handoffWithoutDepartmentIsAcceptedWhenNoneIsAvailable() {
        ownedByBot();
        when(departments.list()).thenReturn(List.of());
        when(client.reply(any())).thenReturn(reply(true, null, "Cliente pediu um atendente"));

        service.handleInbound(inbound("Quero falar com uma pessoa"));

        verify(transitions).returnToQueue(
            any(),
            anyLong(),
            isNull(),
            eq(ConversationActivityType.BOT_HANDOFF),
            eq(BOT_MEMBER_ID),
            eq("Cliente pediu um atendente")
        );
        assertThat(recordedRun().outcome()).isEqualTo("HANDED_OFF");
    }

    @Test
    void unavailableBotSendsFixedMessageAndReturnsConversationToQueue() {
        ownedByBot();
        when(client.reply(any())).thenThrow(new BotUnavailableException("BOT_TIMEOUT"));

        service.handleInbound(inbound("Olá"));

        var send = ArgumentCaptor.forClass(MessageDtos.Send.class);
        verify(messages).send(eq(CONVERSATION), send.capture());
        assertThat(send.getValue().content()).isEqualTo(BotService.FALLBACK_MESSAGE);
        verify(transitions).returnToQueue(
            any(),
            anyLong(),
            isNull(),
            eq(ConversationActivityType.BOT_HANDOFF),
            eq(BOT_MEMBER_ID),
            eq("Bot indisponível (BOT_TIMEOUT)")
        );
        var run = recordedRun();
        assertThat(run.outcome()).isEqualTo("FALLBACK_HANDOFF");
        assertThat(run.errorCode()).isEqualTo("BOT_TIMEOUT");
    }

    @Test
    void handoffToUnlistedDepartmentFallsBackToFixedMessage() {
        ownedByBot();
        when(client.reply(any())).thenReturn(reply(true, 4L, "Setor inativo"));

        service.handleInbound(inbound("Quero falar com alguém"));

        var send = ArgumentCaptor.forClass(MessageDtos.Send.class);
        verify(messages).send(eq(CONVERSATION), send.capture());
        assertThat(send.getValue().content()).isEqualTo(BotService.FALLBACK_MESSAGE);
        var run = recordedRun();
        assertThat(run.outcome()).isEqualTo("FALLBACK_HANDOFF");
        assertThat(run.errorCode()).isEqualTo("BOT_INVALID_RESPONSE");
        assertThat(run.intent()).isEqualTo("horario_atendimento");
    }

    @Test
    void handoffRetriesWhenTheConversationVersionChanged() {
        ownedByBot();
        when(client.reply(any())).thenReturn(reply(true, 3L, "Cobrança em duplicidade"));
        when(transitions.returnToQueue(any(), anyLong(), any(), any(), any(), any())).thenReturn(
            Optional.empty(),
            Optional.of(conversation(null))
        );

        service.handleInbound(inbound("Fui cobrado duas vezes"));

        verify(transitions, times(2)).returnToQueue(any(), anyLong(), any(), any(), any(), any());
        assertThat(recordedRun().outcome()).isEqualTo("HANDED_OFF");
    }

    @Test
    void conversationAlreadyHandedOffInThisCycleIsNotReclaimed() {
        var pending = conversation(null);
        pending.setCycleStartedAt(OffsetDateTime.now().minusHours(1));
        when(conversations.require(CONVERSATION)).thenReturn(pending);
        when(
            activities.recordedSince(
                pending,
                ConversationActivityType.BOT_HANDOFF,
                pending.getCycleStartedAt()
            )
        ).thenReturn(true);

        service.handleInbound(inbound("Ainda estou esperando"));

        verify(transitions, never()).attempt(any(), anyLong(), any());
        verifyNoInteractions(client, messages, runs);
    }

    @Test
    void replyIsDiscardedWhenHumanTakesOverDuringTheCall() {
        when(conversations.require(CONVERSATION)).thenReturn(
            conversation(BOT_MEMBER_ID),
            conversation(HUMAN_MEMBER_ID)
        );
        when(client.reply(any())).thenReturn(reply(true, 3L, "Cobrança em duplicidade"));

        service.handleInbound(inbound("Olá"));

        verify(messages, never()).send(anyString(), any());
        verify(transitions, never()).returnToQueue(any(), anyLong(), any(), any(), any(), any());
        assertThat(recordedRun().outcome()).isEqualTo("DISCARDED");
    }

    @Test
    void rejectedSendIsRecorded() {
        ownedByBot();
        when(client.reply(any())).thenReturn(reply(false, null, null));
        when(messages.send(anyString(), any())).thenThrow(ApiException.conflict("Atendimento encerrado"));

        service.handleInbound(inbound("Olá"));

        assertThat(recordedRun().errorCode()).isEqualTo("BOT_SEND_REJECTED");
    }

    @Test
    void recordingFailureDoesNotInterruptTheReply() {
        ownedByBot();
        when(client.reply(any())).thenReturn(reply(false, null, null));
        when(runs.insert(any(BotRun.class))).thenThrow(new DataAccessResourceFailureException("mongo"));

        assertThatCode(() -> service.handleInbound(inbound("Olá"))).doesNotThrowAnyException();
        verify(messages).send(anyString(), any());
    }

    private BotService service(boolean enabled) {
        var config = new BotConfiguration(enabled, "http://bot:8000", API_KEY, BOT_MEMBER_ID, 45);
        return new BotService(
            config,
            client,
            runs,
            conversations,
            transitions,
            activities,
            departments,
            messages
        );
    }

    private void ownedByBot() {
        when(conversations.require(CONVERSATION)).thenReturn(conversation(BOT_MEMBER_ID));
    }

    private BotRun recordedRun() {
        var run = ArgumentCaptor.forClass(BotRun.class);
        verify(runs).insert(run.capture());
        return run.getValue();
    }

    private static Conversation conversation(Long assignedMemberId) {
        var conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setStatus(ConversationStatus.PENDING.name());
        conversation.setAssignedMemberId(assignedMemberId);
        conversation.setVersion(0L);
        return conversation;
    }

    private static NormalizedMessage inbound(String text) {
        var message = mock(NormalizedMessage.class);
        when(message.id()).thenReturn(INBOUND_ID.toHexString());
        when(message.conversationId()).thenReturn(CONVERSATION);
        when(message.channelId()).thenReturn(1L);
        when(message.direction()).thenReturn(Direction.INBOUND);
        when(message.messageType()).thenReturn(MessageType.TEXT);
        when(message.content()).thenReturn(Map.of("text", text));
        return message;
    }

    private static NormalizedMessage stored(Direction direction, String text, Instant createdAt) {
        var message = mock(NormalizedMessage.class);
        when(message.id()).thenReturn(new ObjectId().toHexString());
        when(message.direction()).thenReturn(direction);
        when(message.messageType()).thenReturn(MessageType.TEXT);
        when(message.status()).thenReturn(NormalizedMessageStatus.SIMULATED);
        when(message.content()).thenReturn(Map.of("text", text));
        when(message.createdAt()).thenReturn(createdAt);
        return message;
    }

    private static DepartmentDtos.Response department(Long id, String name, ActiveStatus status) {
        return new DepartmentDtos.Response(id, name, "#000000", 1, "Setor " + name, status, null, null, 0L);
    }

    private static BotDtos.ReplyResponse reply(boolean needsHuman, Long departmentId, String reason) {
        return new BotDtos.ReplyResponse(
            "Funcionamos das 8h às 18h.",
            "horario_atendimento",
            needsHuman,
            departmentId,
            reason,
            new BotDtos.Run(
                "claude-haiku-5-5",
                1000,
                80,
                0,
                900,
                2,
                1900,
                "req_test123",
                "primary",
                List.of(),
                List.of(),
                List.of("buscar_conhecimento"),
                List.of("entregas.md | Entregas > Dias e horários")
            )
        );
    }
}
