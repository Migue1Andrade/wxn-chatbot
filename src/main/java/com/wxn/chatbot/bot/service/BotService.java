package com.wxn.chatbot.bot.service;

import com.wxn.chatbot.bot.client.BotClient;
import com.wxn.chatbot.bot.client.BotUnavailableException;
import com.wxn.chatbot.bot.config.BotConfiguration;
import com.wxn.chatbot.bot.document.BotRun;
import com.wxn.chatbot.bot.dto.BotDtos;
import com.wxn.chatbot.bot.mapper.BotMapper;
import com.wxn.chatbot.bot.model.BotRunOutcome;
import com.wxn.chatbot.bot.repository.BotRunRepository;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.model.ConversationTransition;
import com.wxn.chatbot.conversation.service.ConversationActivityService;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.conversation.service.ConversationTransitionService;
import com.wxn.chatbot.department.service.DepartmentService;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.model.Direction;
import com.wxn.chatbot.whatsapp.model.MessageType;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** O bot decide no serviço Python; aqui o backend executa: assume, responde, encaminha e registra. */
@Service
public class BotService {

    static final String CLAIM_REASON = "Atendimento iniciado pelo bot";
    static final String FALLBACK_MESSAGE =
        "Vou encaminhar você para um de nossos atendentes. Em instantes alguém continua o atendimento.";
    private static final int MAX_REPLY_LENGTH = 1600;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_HANDOFF_ATTEMPTS = 3;
    // Mensagens mais recentes enviadas como histórico; o serviço Python ainda corta pelo limite de tokens.
    private static final int HISTORY_LIMIT = 50;
    private static final Logger LOGGER = LoggerFactory.getLogger(BotService.class);

    private final BotConfiguration config;
    private final BotClient client;
    private final BotRunRepository runs;
    private final ConversationService conversations;
    private final ConversationTransitionService transitions;
    private final ConversationActivityService activities;
    private final DepartmentService departments;
    private final MessageService messages;

    public BotService(
        BotConfiguration config,
        BotClient client,
        BotRunRepository runs,
        ConversationService conversations,
        ConversationTransitionService transitions,
        ConversationActivityService activities,
        DepartmentService departments,
        MessageService messages
    ) {
        this.config = config;
        this.client = client;
        this.runs = runs;
        this.conversations = conversations;
        this.transitions = transitions;
        this.activities = activities;
        this.departments = departments;
        this.messages = messages;
    }

    public void handleInbound(NormalizedMessage inbound) {
        String text = text(inbound);
        if (!config.enabled() || text == null || !claim(inbound.conversationId())) {
            return;
        }
        long started = System.nanoTime();
        BotDtos.ReplyRequest request = BotMapper.request(
            inbound,
            text,
            messages.page(inbound.conversationId(), HISTORY_LIMIT, null).items(),
            departments.list()
        );
        BotDtos.ReplyResponse response = null;
        try {
            response = client.reply(request);
            requireValid(response, request);
        } catch (BotUnavailableException error) {
            fallback(inbound, response, started, error.code());
            return;
        }
        if (!ownedByBot(conversations.require(inbound.conversationId()))) {
            // Um humano assumiu enquanto o bot pensava; a resposta não é enviada.
            record(inbound, BotRunOutcome.DISCARDED, response, null, started, null);
            return;
        }
        String replyId;
        try {
            replyId = send(inbound, response.reply());
        } catch (ApiException error) {
            LOGGER.warn(
                "Envio do bot recusado na conversa {}: {}",
                inbound.conversationId(),
                error.getMessage()
            );
            record(inbound, BotRunOutcome.FAILED, response, null, started, "BOT_SEND_REJECTED");
            return;
        }
        if (!response.needsHuman()) {
            record(inbound, BotRunOutcome.REPLIED, response, replyId, started, null);
            return;
        }
        boolean handedOff = returnToQueue(
            inbound.conversationId(),
            response.departmentId(),
            handoffReason(response, request)
        );
        record(
            inbound,
            handedOff ? BotRunOutcome.HANDED_OFF : BotRunOutcome.FAILED,
            response,
            replyId,
            started,
            handedOff ? null : "BOT_HANDOFF_CONFLICT"
        );
    }

    // Sem resposta válida do bot, o cliente recebe um aviso fixo e a conversa vai para a fila.
    private void fallback(
        NormalizedMessage inbound,
        BotDtos.ReplyResponse response,
        long started,
        String code
    ) {
        LOGGER.warn("Bot indisponível na conversa {}: {}", inbound.conversationId(), code);
        if (!ownedByBot(conversations.require(inbound.conversationId()))) {
            record(inbound, BotRunOutcome.DISCARDED, response, null, started, code);
            return;
        }
        String replyId = null;
        try {
            replyId = send(inbound, FALLBACK_MESSAGE);
        } catch (ApiException error) {
            LOGGER.warn("Aviso de encaminhamento recusado na conversa {}", inbound.conversationId());
        }
        boolean handedOff = returnToQueue(inbound.conversationId(), null, "Bot indisponível (" + code + ")");
        record(
            inbound,
            handedOff ? BotRunOutcome.FALLBACK_HANDOFF : BotRunOutcome.FAILED,
            response,
            replyId,
            started,
            code
        );
    }

    private boolean claim(String conversationId) {
        Conversation current = conversations.require(conversationId);
        if (
            current.getAssignedMemberId() == null &&
            current.operationalStatus() == ConversationStatus.PENDING &&
            !handedOffInCurrentCycle(current)
        ) {
            var transition = new ConversationTransition(
                ConversationStatus.IN_PROGRESS,
                config.memberId(),
                null,
                ConversationActivityType.MEMBER_ASSIGNED,
                null,
                CLAIM_REASON,
                ConversationEventTypes.CONVERSATION_ASSIGNED
            );
            current = transitions
                .attempt(current, current.revision(), transition)
                .orElseGet(() -> conversations.require(conversationId));
        }
        return ownedByBot(current);
    }

    // Depois de encaminhada, a conversa fica com os humanos até ser encerrada e reaberta.
    private boolean handedOffInCurrentCycle(Conversation conversation) {
        OffsetDateTime cycleStart =
            conversation.getCycleStartedAt() != null
                ? conversation.getCycleStartedAt()
                : conversation.getCreatedAt();
        return (
            cycleStart != null &&
            activities.recordedSince(conversation, ConversationActivityType.BOT_HANDOFF, cycleStart)
        );
    }

    // O envio altera a versão da conversa; por isso relê a conversa a cada tentativa.
    private boolean returnToQueue(String conversationId, Long departmentId, String reason) {
        for (int attempt = 0; attempt < MAX_HANDOFF_ATTEMPTS; attempt++) {
            Conversation current = conversations.require(conversationId);
            if (!ownedByBot(current)) {
                return false;
            }
            var updated = transitions.returnToQueue(
                current,
                current.revision(),
                departmentId,
                ConversationActivityType.BOT_HANDOFF,
                config.memberId(),
                reason
            );
            if (updated.isPresent()) {
                return true;
            }
        }
        LOGGER.warn(
            "Não foi possível encaminhar a conversa {} após {} tentativas",
            conversationId,
            MAX_HANDOFF_ATTEMPTS
        );
        return false;
    }

    private String send(NormalizedMessage inbound, String content) {
        var sent = messages.send(
            inbound.conversationId(),
            new MessageDtos.Send(inbound.channelId(), config.memberId(), content, operationId(inbound))
        );
        return sent.id();
    }

    private boolean ownedByBot(Conversation conversation) {
        return Objects.equals(conversation.getAssignedMemberId(), config.memberId());
    }

    private void requireValid(BotDtos.ReplyResponse response, BotDtos.ReplyRequest request) {
        boolean listedDepartment = request
            .departments()
            .stream()
            .anyMatch(department -> department.id().equals(response.departmentId()));
        // Sem setor é aceito: o Python encaminha sem setor quando um guardrail ou o fallback decidem.
        boolean departmentOk = response.departmentId() == null || listedDepartment;
        boolean invalidHandoff = response.needsHuman() && blank(response.handoffReason());
        if (
            blank(response.reply()) ||
            response.reply().length() > MAX_REPLY_LENGTH ||
            blank(response.intent()) ||
            response.run() == null ||
            !departmentOk ||
            invalidHandoff
        ) {
            throw new BotUnavailableException("BOT_INVALID_RESPONSE");
        }
    }

    private static String handoffReason(BotDtos.ReplyResponse response, BotDtos.ReplyRequest request) {
        String reason = request
            .departments()
            .stream()
            .filter(department -> department.id().equals(response.departmentId()))
            .findFirst()
            .map(department -> "Setor " + department.name() + ": " + response.handoffReason())
            .orElse(response.handoffReason());
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    }

    private void record(
        NormalizedMessage inbound,
        BotRunOutcome outcome,
        BotDtos.ReplyResponse response,
        String replyId,
        long started,
        String errorCode
    ) {
        BotRun run = BotMapper.run(inbound, outcome, response, replyId, elapsedMs(started), errorCode);
        // O registro serve à observabilidade e nunca interrompe o atendimento.
        try {
            runs.insert(run);
        } catch (DataAccessException error) {
            LOGGER.warn("Falha ao gravar bot_runs da conversa {}", inbound.conversationId(), error);
        }
    }

    private static String text(NormalizedMessage message) {
        if (message.direction() != Direction.INBOUND || message.messageType() != MessageType.TEXT) {
            return null;
        }
        Object text = message.content() == null ? null : message.content().get("text");
        return text instanceof String value && !value.isBlank() ? value : null;
    }

    // Derivado da mensagem recebida: reprocessar o mesmo evento não envia uma segunda resposta.
    static String operationId(NormalizedMessage inbound) {
        return "bot-reply-" + inbound.id();
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
