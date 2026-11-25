package com.wxn.chatbot.whatsapp.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.repository.WebhookInboxRepository;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EvolutionWebhookService {

    private final EvolutionConfiguration config;
    private final EvolutionApiClient client;
    private final EvolutionSetupService setup;
    private final ChannelService channels;
    private final MessageService messages;
    private final EvolutionWebhookMapper mapper;
    private final WebhookInboxRepository inbox;

    public EvolutionWebhookService(
        EvolutionConfiguration config,
        EvolutionApiClient client,
        EvolutionSetupService setup,
        ChannelService channels,
        MessageService messages,
        EvolutionWebhookMapper mapper,
        WebhookInboxRepository inbox
    ) {
        this.config = config;
        this.client = client;
        this.setup = setup;
        this.channels = channels;
        this.messages = messages;
        this.mapper = mapper;
        this.inbox = inbox;
    }

    public void receive(String secret, JsonNode body) {
        if (!config.configured() || !config.webhookAuthorized(secret)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Webhook Evolution não autorizado");
        }
        if (!body.isObject()) {
            throw ApiException.badRequest("Payload Evolution inválido");
        }
        setup.observe(body, secret);
        WhatsAppChannel channel = channels.firstActive(ProviderType.EVOLUTION);
        if (channel == null) {
            throw ApiException.conflict("Canal Evolution ainda não foi inicializado");
        }
        synchronizeConnectedPhone(channel);
        String key = inbox.accept(channel.getId(), eventName(body), body);
        process(key);
    }

    private void process(String key) {
        var row = inbox.claim(key);
        if (row == null) {
            return;
        }
        try {
            JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                row.getString("payload")
            );
            WhatsAppChannel channel = channels.requireActive(row.getLong("channelId"));
            apply(channel, body);
            inbox.finish(key, "APPLIED");
        } catch (RuntimeException error) {
            inbox.failed(key);
            throw error;
        } catch (Exception error) {
            inbox.failed(key);
            throw ApiException.badRequest("Payload Evolution inválido");
        }
    }

    private void apply(WhatsAppChannel channel, JsonNode body) {
        switch (eventName(body)) {
            case "MESSAGES_UPSERT", "SEND_MESSAGE" -> applyMessages(channel, body.path("data"));
            case "MESSAGES_UPDATE" -> applyUpdates(channel, body.path("data"));
            case "MESSAGES_DELETE" -> applyDeletes(channel, body.path("data"));
            case "CONNECTION_UPDATE", "QRCODE_UPDATED" -> {
                // Connection state was observed before the idempotent inbox processing.
            }
            default -> {
                // Other Evolution events are intentionally acknowledged and ignored for TEXT-only support.
            }
        }
    }

    static String eventName(JsonNode body) {
        return body.path("event").asText("").replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private void synchronizeConnectedPhone(WhatsAppChannel channel) {
        if (channel.getPhoneNumber() == null) {
            String phone = client.connectedPhone();
            if (phone != null) {
                channels.updatePhone(channel, phone);
            }
        }
    }

    private void applyMessages(WhatsAppChannel channel, JsonNode data) {
        if (data.isArray()) {
            for (JsonNode item : data) {
                applyMessage(channel, item);
            }
            return;
        }
        applyMessage(channel, data);
    }

    private void applyMessage(WhatsAppChannel channel, JsonNode data) {
        if (!mapper.supported(data)) {
            return;
        }
        var incoming = mapper.text(data, channel.getPhoneNumber());
        if (data.path("key").path("fromMe").asBoolean()) {
            messages.echo(channel, incoming, true);
        } else {
            messages.receive(channel, incoming, false);
        }
    }

    private void applyStatus(WhatsAppChannel channel, JsonNode data) {
        String externalId = data.path("key").path("id").asText(null);
        if (externalId == null) {
            externalId = data.path("id").asText(null);
        }
        if (externalId == null || externalId.isBlank()) {
            return;
        }
        messages.status(
            channel,
            new NormalizedStatusEvent(externalId, mapper.status(data.path("status")), null)
        );
    }

    private void applyUpdates(WhatsAppChannel channel, JsonNode data) {
        if (data.isArray()) {
            data.forEach(item -> applyUpdates(channel, item));
            return;
        }
        String editedId = mapper.messageId(data);
        String editedText = mapper.editedText(data);
        if (editedId != null && editedText != null) {
            messages.remoteEdit(channel, editedId, editedText, mapper.timestamp(data));
            return;
        }
        applyStatus(channel, data);
    }

    private void applyDeletes(WhatsAppChannel channel, JsonNode data) {
        if (data.isArray()) {
            data.forEach(item -> applyDeletes(channel, item));
            return;
        }
        String id = data.path("key").path("id").asText(null);
        if (id == null) {
            id = data.path("message").path("protocolMessage").path("key").path("id").asText(null);
        }
        if (id != null && !id.isBlank()) {
            messages.remoteDelete(channel, id, mapper.timestamp(data));
        }
    }
}
