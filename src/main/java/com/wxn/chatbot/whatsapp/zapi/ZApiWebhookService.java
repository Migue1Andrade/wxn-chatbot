package com.wxn.chatbot.whatsapp.zapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.*;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("legacy")
public class ZApiWebhookService {

    private final com.wxn.chatbot.whatsapp.repository.WebhookInboxRepository inbox;
    private final com.wxn.chatbot.whatsapp.operations.ChannelOperationsService operations;
    private long rateWindow;
    private int requests;
    private final com.wxn.chatbot.message.service.MessageMutationService mutations;
    private final ZApiConfiguration config;
    private final ChannelService channels;
    private final MessageService messages;
    private final ZApiWebhookMapper mapper;
    private final com.wxn.chatbot.messagelog.service.MessageLogService logs;

    public ZApiWebhookService(
        ZApiConfiguration config,
        ChannelService channels,
        MessageService messages,
        ZApiWebhookMapper mapper,
        com.wxn.chatbot.messagelog.service.MessageLogService logs,
        com.wxn.chatbot.whatsapp.repository.WebhookInboxRepository inbox,
        com.wxn.chatbot.whatsapp.operations.ChannelOperationsService operations,
        com.wxn.chatbot.message.service.MessageMutationService mutations
    ) {
        this.mutations = mutations;
        this.inbox = inbox;
        this.operations = operations;
        this.config = config;
        this.channels = channels;
        this.messages = messages;
        this.mapper = mapper;
        this.logs = logs;
    }

    public void receive(String secret, String kind, JsonNode body) {
        if (!config.authenticated(secret)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Webhook não autorizado");
        }
        var channel = channels.requireActive(config.channelId());
        config.requireChannel(channel);
        if (!config.instanceId().equals(body.path("instanceId").asText())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Instância inválida");
        }
        if (!body.isObject()) {
            throw ApiException.badRequest("Objeto JSON obrigatório");
        }
        rate();
        String key = inbox.accept(channel.getId(), kind, body);
        process(key);
    }

    private synchronized void rate() {
        long minute = System.currentTimeMillis() / 60000;
        if (rateWindow != minute) {
            rateWindow = minute;
            requests = 0;
        }
        if (++requests > 3000) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Limite temporário de callbacks");
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15000)
    public void replay() {
        for (var row : inbox.due()) {
            process(row.getString("_id"));
        }
    }

    private void process(String key) {
        var row = inbox.claim(key);
        if (row == null) {
            return;
        }
        try {
            var channel = channels.requireActive(row.getLong("channelId"));
            var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(row.getString("payload"));
            inbox.finish(key, apply(channel, row.getString("kind"), body));
        } catch (Exception error) {
            inbox.failed(key);
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                "whatsapp_webhook outcome=FAILED correlationId={} errorType={}",
                key,
                error.getClass().getSimpleName()
            );
        }
    }

    private String apply(
        com.wxn.chatbot.whatsapp.entity.WhatsAppChannel channel,
        String kind,
        JsonNode body
    ) {
        operations.observed(channel.getId(), null, kind);
        if ("connected".equals(kind) || "disconnected".equals(kind)) {
            if (
                ("ConnectedCallback".equals(body.path("type").asText()) &&
                    body.path("connected").asBoolean()) ||
                ("DisconnectedCallback".equals(body.path("type").asText()) &&
                    body.path("disconnected").asBoolean())
            ) {
                operations.observed(
                    channel.getId(),
                    "connected".equals(kind) ? "CONNECTED" : "DISCONNECTED",
                    kind
                );
            } else {
                throw ApiException.badRequest("Evento de conexão inválido");
            }
            return "APPLIED";
        }
        logs.webhook(channel, kind, body);
        if ("received".equals(kind)) {
            if (
                !"ReceivedCallback".equals(body.path("type").asText()) ||
                body.path("isGroup").asBoolean() ||
                body.path("isNewsletter").asBoolean()
            ) {
                return "UNSUPPORTED_EXTERNAL_EVENT";
            }
            if (!mapper.supported(body) && !body.path("isEdit").asBoolean() && !body.has("reaction")) {
                return "UNSUPPORTED_EXTERNAL_EVENT";
            }
            String connected = ZApiWebhookMapper.phone(ZApiWebhookMapper.required(body, "connectedPhone"));
            if (!channel.getPhoneNumber().equals(connected)) {
                throw ApiException.badRequest("Número conectado inválido");
            }
            if (body.has("reaction")) {
                var reaction = body.path("reaction");
                String external = ZApiWebhookMapper.required(reaction.path("referencedMessage"), "messageId");
                String value = reaction.path("value").asText("");
                if (value.length() > 32) {
                    throw ApiException.badRequest("Reação inválida");
                }
                boolean own = connected.equals(
                    ZApiWebhookMapper.phone(ZApiWebhookMapper.required(reaction, "reactionBy"))
                );
                mutations.observed(
                    channel.getId(),
                    external,
                    "SEND_REACTION",
                    value,
                    own,
                    ZApiWebhookMapper.moment(body),
                    ZApiWebhookMapper.phone(ZApiWebhookMapper.required(body, "phone"))
                );
                return "REACTION_APPLIED";
            }
            if (body.path("isEdit").asBoolean()) {
                var edit = mapper.text(body);
                mutations.observed(
                    channel.getId(),
                    edit.externalMessageId(),
                    "EDIT_TEXT",
                    edit.content().get("text").toString(),
                    body.path("fromMe").asBoolean(),
                    edit.occurredAt(),
                    edit.senderPhone()
                );
                return "EDIT_APPLIED";
            }
            var incoming = mapper.text(body);
            if (!channel.getPhoneNumber().equals(incoming.recipientPhone())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Número conectado inválido");
            }
            if (body.path("fromMe").asBoolean()) {
                messages.echo(
                    channel,
                    incoming,
                    body.path("fromApi").isBoolean() ? body.path("fromApi").asBoolean() : null
                );
            } else {
                messages.receive(channel, incoming, false);
            }
            return "APPLIED";
        }
        if ("sent".equals(kind)) {
            if (!"DeliveryCallback".equals(body.path("type").asText())) {
                return "APPLIED";
            }
            String external = ZApiWebhookMapper.required(body, "messageId");
            String tracking = body.path("zaapId").asText(null);
            var status = new NormalizedStatusEvent(
                external,
                body.hasNonNull("error") ? NormalizedMessageStatus.FAILED : NormalizedMessageStatus.SENT,
                body.hasNonNull("error") ? "ZAPI_DELIVERY_FAILED" : null
            );
            messages.delivery(channel, tracking, status);
            return "APPLIED";
        }
        if (!"MessageStatusCallback".equals(body.path("type").asText())) {
            return "APPLIED";
        }
        var status = mapper.status(body.path("status").asText());
        if (status == NormalizedMessageStatus.UNKNOWN) {
            return "APPLIED";
        }
        if (!body.path("ids").isArray() || body.path("ids").size() > 1000) {
            throw ApiException.badRequest("Lista de IDs inválida");
        }
        for (JsonNode id : body.path("ids")) {
            if (id.isTextual() && !id.asText().isBlank()) {
                messages.status(channel, new NormalizedStatusEvent(id.asText(), status, null));
            }
        }
        return "APPLIED";
    }
}
