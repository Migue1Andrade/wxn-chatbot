package com.wxn.chatbot.openbot;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos.Action;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos.Assign;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.service.ConversationService;
import com.wxn.chatbot.member.entity.MemberType;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.NormalizedInboundMessage;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(prefix = "openbot.support", name = "enabled", havingValue = "true")
public class OpenBotSupportService {

    private static final Map<String, List<String>> FAQ = Map.of(
        "test-drive",
        List.of(
            "test-drive",
            "test drive",
            "dirigir",
            "experimentar",
            "visita",
            "agendar",
            "agendamento",
            "horário"
        ),
        "documentos",
        List.of("documento", "documentos", "documentação", "cpf", "identidade", "comprovante"),
        "financiamento",
        List.of(
            "financiamento",
            "financiar",
            "parcela",
            "entrada",
            "crédito",
            "credito",
            "taxa",
            "aprovação"
        ),
        "pós-venda",
        List.of(
            "pós-venda",
            "pos-venda",
            "revisão",
            "revisao",
            "garantia",
            "manutenção",
            "manutencao",
            "oficina"
        ),
        "atendimento",
        List.of("olá", "ola", "oi", "bom dia", "boa tarde", "boa noite", "ajuda", "atendente"),
        "preço",
        List.of("preço", "preco", "valor", "estoque", "disponível", "disponivel", "modelo")
    );

    private static final Map<String, String> ANSWERS = Map.of(
        "test-drive",
        "Para agendar uma visita ou test-drive, informe o modelo desejado e sua disponibilidade. A equipe confirmará os horários.",
        "documentos",
        "Para iniciar a compra, normalmente são necessários documento oficial com foto, CPF e comprovante de endereço. A equipe confirma os requisitos do veículo.",
        "financiamento",
        "As condições dependem de análise de crédito e instituição financeira. Não podemos garantir aprovação, taxa ou parcela antes da simulação formal.",
        "pós-venda",
        "Para suporte pós-venda, informe o veículo e o assunto. A equipe WXN Carros direcionará sua solicitação.",
        "atendimento",
        "Olá! Sou o assistente da WXN Carros. Posso ajudar com agendamento, documentação, financiamento ou pós-venda.",
        "preço",
        "A disponibilidade e o valor variam por modelo. Informe qual veículo procura para a equipe confirmar as opções."
    );

    private static final java.time.Duration SUPPORT_REPLY_TIMEOUT = java.time.Duration.ofSeconds(25);
    private static final java.time.Duration SUPPORT_RETRY_WINDOW = java.time.Duration.ofMinutes(2);
    private static final java.time.Duration BOT_IDLE_REMINDER = java.time.Duration.ofMinutes(20);
    private static final java.time.Duration BOT_IDLE_CANCEL = java.time.Duration.ofMinutes(30);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(
        OpenBotSupportService.class
    );

    private final SupportProperties properties;
    private final MemberRepository members;
    private final com.wxn.chatbot.message.repository.MessageRepository messageRepository;
    private final com.wxn.chatbot.conversation.repository.ConversationActivityRepository activityRepository;
    private final ConversationService conversations;
    private final com.wxn.chatbot.conversation.service.ConversationManagementService management;
    private final org.springframework.beans.factory.ObjectProvider<MessageService> messages;
    private final RestClient client = RestClient.create();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, String> callbackStates = new ConcurrentHashMap<>();

    public OpenBotSupportService(
        SupportProperties properties,
        MemberRepository members,
        ConversationService conversations,
        org.springframework.beans.factory.ObjectProvider<MessageService> messages,
        com.wxn.chatbot.message.repository.MessageRepository messageRepository,
        com.wxn.chatbot.conversation.repository.ConversationActivityRepository activityRepository,
        com.wxn.chatbot.conversation.service.ConversationManagementService management
    ) {
        this.properties = properties;
        this.members = members;
        this.conversations = conversations;
        this.messages = messages;
        this.messageRepository = messageRepository;
        this.activityRepository = activityRepository;
        this.management = management;
    }

    public void accept(
        WhatsAppChannel channel,
        com.wxn.chatbot.message.document.Message message,
        NormalizedInboundMessage incoming,
        Long assignedBotId
    ) {
        if (!properties.enabled()) {
            return;
        }
        var bot = members.findByIdAndDeletedAtIsNull(assignedBotId).orElse(null);
        var conversation = conversations.require(message.getConversationId().toHexString());
        if (
            bot == null ||
            bot.getType() != MemberType.BOT ||
            !bot.getStatus().name().equals("ACTIVE") ||
            !Objects.equals(bot.getId(), conversation.getAssignedMemberId())
        ) {
            return;
        }
        if (!messageRepository.findById(message.getId()).isPresent()) {
            return;
        }
        String requestId = UUID.nameUUIDFromBytes(
            (message.getChannelId() + ":" + message.getExternalMessageId()).getBytes(StandardCharsets.UTF_8)
        ).toString();
        String text = String.valueOf(incoming.content().getOrDefault("text", ""));
        if (text.isBlank() || callbackStates.containsKey(requestId)) {
            return;
        }
        List<String> retrieved = retrieve(text);
        boolean handoff =
            requiresHuman(text) ||
            retrieved.isEmpty() ||
            (retrieved.size() == 1 &&
                ANSWERS.get("atendimento").equals(retrieved.getFirst()) &&
                !isGreeting(text));
        String fallback = handoff ? "" : String.join(" ", retrieved);
        boolean hasWebhook =
            properties.webhookUrl() != null &&
            !properties.webhookUrl().isBlank() &&
            properties.hmacSecret() != null &&
            !properties.hmacSecret().isBlank();
        var deadline = hasWebhook ? Instant.now().plus(SUPPORT_REPLY_TIMEOUT) : Instant.now();
        var item = new Pending(
            conversation.getId().toHexString(),
            channel.getId(),
            bot.getId(),
            conversation.getLastInboundAt(),
            deadline,
            fallback,
            handoff
        );
        if (pending.putIfAbsent(requestId, item) != null || !hasWebhook) {
            return;
        }
        var context = retrieved.isEmpty()
            ? List.of("Não há resposta confirmada na FAQ. Solicite atendimento humano.")
            : retrieved;
        Map<String, Object> envelope = Map.of(
            "type",
            "wxn.support.message",
            "data",
            Map.of(
                "message",
                text,
                "context",
                context,
                "history",
                historyFor(message),
                "requestId",
                requestId
            )
        );
        String body = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(envelope).toString();
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        try {
            client
                .post()
                .uri(URI.create(properties.webhookUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-openbot-timestamp", timestamp)
                .header("x-openbot-delivery-id", requestId)
                .header("x-openbot-signature", "sha256=" + hmac(timestamp + "." + requestId + "." + body))
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException failure) {
            LOG.warn("Webhook de atendimento indisponível. A resposta de contingência está pendente.");
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 2000)
    public void replyToExpiredRequests() {
        Instant now = Instant.now();
        pending.forEach((requestId, item) -> {
            if (now.isBefore(item.deadline())) {
                return;
            }
            try {
                if (
                    reply(
                        requestId,
                        item.fallback(),
                        item.handoff(),
                        !item.handoff() &&
                            !item.fallback().equals(ANSWERS.get("atendimento")) &&
                            !item.fallback().isBlank()
                    )
                ) {
                    LOG.info("Atendimento concluído pela FAQ de contingência.");
                }
            } catch (RuntimeException failure) {
                if (!now.isBefore(item.deadline().plus(SUPPORT_RETRY_WINDOW))) {
                    pending.remove(requestId, item);
                    LOG.warn("Prazo de contingência do atendimento expirou sem confirmação.");
                }
            }
        });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void checkBotInactivity() {
        processInactivityAt(Instant.now());
    }

    void processInactivityAt(Instant now) {
        if (!properties.enabled() || properties.autoAssignBotId() == null) {
            return;
        }
        for (var snapshot : conversations.activeBotSessions(properties.autoAssignBotId())) {
            if (!Objects.equals(snapshot.getChannelId(), properties.autoAssignChannelId())) {
                continue;
            }
            messages.getObject().withChannelWriteLock(snapshot.getChannelId(), () -> {
                processIdleConversation(snapshot.getId().toHexString(), now);
                return null;
            });
        }
    }

    private void processIdleConversation(String id, Instant now) {
        conversations.withWriteLock(id, () -> {
            var current = conversations.require(id);
            Long botId = properties.autoAssignBotId();
            if (
                current.getLastInboundAt() == null ||
                !Objects.equals(current.getAssignedMemberId(), botId) ||
                !Objects.equals(current.getChannelId(), properties.autoAssignChannelId()) ||
                !Set.of(ConversationStatus.IN_PROGRESS, ConversationStatus.WAITING_CUSTOMER).contains(
                    current.operationalStatus()
                )
            ) {
                return null;
            }
            Instant lastInbound = current.getLastInboundAt().toInstant();
            var idle = java.time.Duration.between(lastInbound, now);
            if (idle.compareTo(BOT_IDLE_CANCEL) >= 0) {
                endForInactivity(current, botId);
            } else if (idle.compareTo(BOT_IDLE_REMINDER) >= 0 && current.getBotInactivityWarnedAt() == null) {
                remindForInactivity(current, botId, now);
            }
            return null;
        });
    }

    private void remindForInactivity(
        com.wxn.chatbot.conversation.document.Conversation current,
        Long botId,
        Instant now
    ) {
        if (
            conversations.updateBotWarning(
                current,
                java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                botId
            ) == 0
        ) {
            return;
        }
        try {
            messages
                .getObject()
                .sendAsAssignedBot(
                    current.getId().toHexString(),
                    new MessageDtos.Send(
                        current.getChannelId(),
                        botId,
                        "Este atendimento será encerrado em 10 minutos por inatividade. Responda para continuar. Após o encerramento, envie uma nova mensagem para iniciar outro atendimento.",
                        stableOperation(
                            "idlewarn",
                            current.getId().toHexString() + current.getLastInboundAt()
                        )
                    )
                );
        } catch (RuntimeException failure) {
            conversations.resetBotWarning(current, botId);
            LOG.warn("Aviso de inatividade do BOT não enviado; tentativa liberada.");
        }
    }

    private void endForInactivity(com.wxn.chatbot.conversation.document.Conversation current, Long botId) {
        String id = current.getId().toHexString();
        var sent = messages
            .getObject()
            .sendAsAssignedBot(
                id,
                new MessageDtos.Send(
                    current.getChannelId(),
                    botId,
                    "Este atendimento foi encerrado por inatividade. Se precisar, envie uma nova mensagem para iniciar outro atendimento.",
                    stableOperation("idleend", id + current.getLastInboundAt())
                )
            );
        if (sent.status() == com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus.FAILED) {
            return;
        }
        var latest = conversations.require(id);
        if (
            !Objects.equals(latest.getLastInboundAt(), current.getLastInboundAt()) ||
            !Objects.equals(latest.getAssignedMemberId(), botId)
        ) {
            return;
        }
        management.cancelBotForInactivity(
            id,
            new Action(botId, latest.getVersion(), "Inatividade por 30 minutos")
        );
    }

    private static String stableOperation(String prefix, String key) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                key.getBytes(StandardCharsets.UTF_8)
            );
            return "openbot_" + prefix + "_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public boolean reply(String requestId, String reply, boolean handoff) {
        return reply(requestId, reply, handoff, false);
    }

    public boolean reply(String requestId, String reply, boolean handoff, Boolean askResolution) {
        if ("COMPLETED".equals(callbackStates.get(requestId))) {
            return false;
        }
        if (callbackStates.putIfAbsent(requestId, "PROCESSING") != null) {
            throw ApiException.conflict("Resposta em processamento");
        }
        try {
            replyPending(requestId, reply, handoff, askResolution);
            callbackStates.put(requestId, "COMPLETED");
            return true;
        } catch (RuntimeException failure) {
            callbackStates.remove(requestId, "PROCESSING");
            throw failure;
        }
    }

    private void replyPending(String requestId, String reply, boolean handoff, Boolean askResolution) {
        Pending item = pending.get(requestId);
        if (item == null) {
            throw new ApiException(HttpStatus.GONE, "Solicitação inexistente ou expirada");
        }
        if (handoff) {
            conversations.withWriteLock(item.conversationId(), () -> {
                transferToHuman(requestId, item);
                return null;
            });
        } else {
            boolean shouldConfirm =
                askResolution != null
                    ? askResolution
                    : !item.fallback().isBlank() && !item.fallback().equals(ANSWERS.get("atendimento"));
            sendReply(requestId, item, reply, shouldConfirm);
        }
        pending.remove(requestId, item);
    }

    private void transferToHuman(String requestId, Pending item) {
        var conversation = conversations.require(item.conversationId());
        if (
            !Objects.equals(conversation.getAssignedMemberId(), item.botId()) ||
            !Objects.equals(conversation.getChannelId(), item.channelId()) ||
            conversation.operationalStatus() == ConversationStatus.CLOSED ||
            conversation.operationalStatus() == ConversationStatus.RESOLVED ||
            conversation.operationalStatus() == ConversationStatus.CANCELLED
        ) {
            pending.remove(requestId);
            throw new ApiException(HttpStatus.GONE, "Conversa já transferida ou encerrada");
        }
        management.handoffToQueue(
            item.conversationId(),
            new Action(item.botId(), conversation.getVersion(), "Solicitado pelo suporte OpenBot")
        );
    }

    private void sendReply(String requestId, Pending item, String reply, boolean askResolution) {
        if (reply == null || reply.isBlank()) {
            throw ApiException.badRequest("Resposta do BOT sem texto");
        }
        String outgoing = askResolution ? reply + "\n\nTodas as suas dúvidas foram solucionadas?" : reply;
        var delivered = messages
            .getObject()
            .sendAsAssignedBot(
                item.conversationId(),
                new MessageDtos.Send(item.channelId(), item.botId(), outgoing, "openbot_" + requestId)
            );
        if (delivered.status() == com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus.FAILED) {
            throw ApiException.conflict("O provedor não confirmou o envio da resposta");
        }
        conversations.withWriteLock(item.conversationId(), () -> {
            var current = conversations.require(item.conversationId());
            if (
                Objects.equals(current.getAssignedMemberId(), item.botId()) &&
                current.operationalStatus() == ConversationStatus.IN_PROGRESS &&
                Objects.equals(current.getLastInboundAt(), item.lastInboundAt())
            ) {
                if (askResolution) {
                    conversations.updateBotConfirmation(
                        current,
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC),
                        item.botId()
                    );
                }
                management.waitForCustomerAsBot(
                    item.conversationId(),
                    new Action(item.botId(), current.getVersion(), "Resposta enviada pelo suporte OpenBot")
                );
            }
            return null;
        });
    }

    @org.springframework.scheduling.annotation.Async
    public void dispatch(WhatsAppChannel channel, NormalizedInboundMessage incoming) {
        if (
            !properties.enabled() || incoming.messageType() != com.wxn.chatbot.whatsapp.model.MessageType.TEXT
        ) {
            return;
        }
        var saved = messageRepository
            .findByChannelIdAndExternalMessageId(channel.getId(), incoming.externalMessageId())
            .orElse(null);
        if (saved != null && Boolean.TRUE.equals(saved.getInboundCompleted())) {
            String id = saved.getConversationId().toHexString();
            var conversation = conversations.require(id);
            Long configuredBot = properties.autoAssignBotId();
            if (
                conversation.operationalStatus() == ConversationStatus.PENDING &&
                conversation.getAssignedMemberId() == null &&
                !activityRepository.existsByConversationIdAndEvent(
                    conversation.getId(),
                    com.wxn.chatbot.conversation.model.ConversationActivityType.BOT_HANDOFF_TO_QUEUE
                ) &&
                Objects.equals(properties.autoAssignChannelId(), channel.getId()) &&
                configuredBot != null &&
                members
                    .findByIdAndDeletedAtIsNull(configuredBot)
                    .filter(
                        member ->
                            member.getType() == MemberType.BOT &&
                            member.getStatus() == com.wxn.chatbot.common.ActiveStatus.ACTIVE
                    )
                    .isPresent()
            ) {
                try {
                    management.assign(
                        id,
                        new Assign(
                            configuredBot,
                            configuredBot,
                            conversation.getVersion(),
                            "Triagem automática WXN Carros"
                        )
                    );
                } catch (ApiException failure) {
                    if (failure.getStatus() != HttpStatus.CONFLICT) {
                        throw failure;
                    }
                }
                conversation = conversations.require(id);
            }
            if (
                conversation.getAssignedMemberId() != null &&
                (conversation.operationalStatus() == ConversationStatus.IN_PROGRESS ||
                    conversation.operationalStatus() == ConversationStatus.WAITING_CUSTOMER)
            ) {
                if (Objects.equals(conversation.getAssignedMemberId(), configuredBot)) {
                    conversations.resetBotWarning(conversation, configuredBot);
                    if (conversation.getBotResolutionPendingAt() != null) {
                        if (
                            isPositiveConfirmation(
                                String.valueOf(incoming.content().getOrDefault("text", ""))
                            )
                        ) {
                            confirmResolution(id, saved, configuredBot);
                            return;
                        }
                        conversations.updateBotConfirmation(conversation, null, configuredBot);
                    }
                }
                accept(channel, saved, incoming, conversation.getAssignedMemberId());
            }
        }
    }

    private void confirmResolution(String id, com.wxn.chatbot.message.document.Message inbound, Long botId) {
        var sent = messages
            .getObject()
            .sendAsAssignedBot(
                id,
                new MessageDtos.Send(
                    inbound.getChannelId(),
                    botId,
                    "Obrigado pelo contato! Seu atendimento foi resolvido.",
                    stableOperation("confirmed", id + inbound.getExternalMessageId())
                )
            );
        if (sent.status() == com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus.FAILED) {
            return;
        }
        var current = conversations.require(id);
        if (
            Objects.equals(current.getAssignedMemberId(), botId) &&
            Objects.equals(current.getLastInboundAt(), inbound.getCreatedAt())
        ) {
            management.resolveAsBot(
                id,
                new Action(botId, current.getVersion(), "Cliente confirmou resolução")
            );
        }
    }

    static boolean isPositiveConfirmation(String text) {
        return Set.of(
            "sim",
            "sim obrigado",
            "sim obrigada",
            "isso",
            "isso mesmo",
            "perfeito",
            "resolvido",
            "tudo certo",
            "está resolvido",
            "ta resolvido",
            "ok"
        ).contains(normalize(text).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip());
    }

    private static boolean requiresHuman(String text) {
        String normalized = normalize(text);
        return List.of("atendente", "consultor", "ser humano", "falar com alguem")
            .stream()
            .anyMatch(normalized::contains);
    }

    private List<Map<String, String>> historyFor(com.wxn.chatbot.message.document.Message current) {
        var rows = messageRepository.findByConversationIdOrderByCreatedAtDesc(
            current.getConversationId(),
            org.springframework.data.domain.PageRequest.of(0, 10)
        );
        var recent = rows
            .stream()
            .filter(message -> !message.getId().equals(current.getId()))
            .filter(message -> message.getContent() != null && message.getContent().getString("text") != null)
            .limit(8)
            .map(message ->
                Map.of(
                    "role",
                    "INBOUND".equals(message.getDirection()) ? "customer" : "assistant",
                    "text",
                    message
                        .getContent()
                        .getString("text")
                        .substring(0, Math.min(300, message.getContent().getString("text").length()))
                )
            )
            .toList();
        var chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    private static boolean isGreeting(String text) {
        return normalize(text)
            .strip()
            .replaceAll("[!?.]+$", "")
            .matches("oi|ola|bom dia|boa tarde|boa noite");
    }

    private static List<String> retrieve(String text) {
        String normalized = normalize(text);
        return FAQ.entrySet()
            .stream()
            .filter(entry ->
                entry
                    .getValue()
                    .stream()
                    .anyMatch(term ->
                        normalized.contains(
                            java.text.Normalizer.normalize(term, java.text.Normalizer.Form.NFD).replaceAll(
                                "\\p{M}",
                                ""
                            )
                        )
                    )
            )
            .map(entry -> ANSWERS.get(entry.getKey()))
            .distinct()
            .toList();
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(
            text.toLowerCase(Locale.ROOT),
            java.text.Normalizer.Form.NFD
        ).replaceAll("\\p{M}", "");
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                new SecretKeySpec(properties.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256")
            );
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Pending(
        String conversationId,
        Long channelId,
        Long botId,
        java.time.OffsetDateTime lastInboundAt,
        Instant deadline,
        String fallback,
        boolean handoff
    ) {}
}
