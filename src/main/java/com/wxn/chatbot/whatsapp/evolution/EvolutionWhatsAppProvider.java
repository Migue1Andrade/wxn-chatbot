package com.wxn.chatbot.whatsapp.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.ProviderSendException;
import com.wxn.chatbot.whatsapp.provider.WhatsAppProvider;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EvolutionWhatsAppProvider implements WhatsAppProvider {

    private final EvolutionConfiguration config;
    private final EvolutionApiClient client;

    public EvolutionWhatsAppProvider(EvolutionConfiguration config, EvolutionApiClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    public ProviderType type() {
        return ProviderType.EVOLUTION;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, false, true, false);
    }

    @Override
    public Set<MessageCapability> messageCapabilities() {
        return Set.of(
            MessageCapability.SEND_TEXT,
            MessageCapability.CONNECTION_STATUS,
            MessageCapability.CONFIGURE_WEBHOOKS,
            MessageCapability.MESSAGE_STATUS
        );
    }

    @Override
    public ChannelConnection connection(WhatsAppChannel channel) {
        try {
            requireChannel(channel);
            JsonNode result = client.connectionState();
            String state = result.path("instance").path("state").asText("unknown");
            boolean connected = "open".equalsIgnoreCase(state);
            return ChannelConnection.of(connected ? "CONNECTED" : "DISCONNECTED", connected, null);
        } catch (RuntimeException error) {
            return ChannelConnection.of("UNKNOWN", false, "EVOLUTION_API_UNAVAILABLE");
        }
    }

    @Override
    public ConnectionInstructions instructions(WhatsAppChannel channel) {
        requireChannel(channel);
        var state = connection(channel);
        if (state.available()) {
            return new ConnectionInstructions("CONNECTED", null, "WhatsApp já está conectado.");
        }
        JsonNode result = client.connect();
        String image = result.path("qrcode").path("base64").asText(null);
        if (image == null) {
            image = result.path("base64").asText(null);
        }
        if (
            image == null &&
            result.path("code").isTextual() &&
            result.path("code").asText().startsWith("data:image")
        ) {
            image = result.path("code").asText();
        }
        if (image == null || !image.startsWith("data:image")) {
            throw new ProviderSendException("EVOLUTION_QR_UNAVAILABLE");
        }
        return new ConnectionInstructions(
            "QR_REQUIRED",
            image,
            "Escaneie em WhatsApp > Dispositivos conectados."
        );
    }

    @Override
    public void configureWebhooks(WhatsAppChannel channel) {
        requireChannel(channel);
        client.configureWebhook();
    }

    @Override
    public SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request) {
        requireChannel(channel);
        if (!connection(channel).available()) {
            throw new ProviderSendException("CHANNEL_DISCONNECTED");
        }
        JsonNode result = client.sendText(request.recipientPhone(), request.content());
        String id = result.path("key").path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new ProviderSendException("EVOLUTION_CONFIRMATION_UNKNOWN");
        }
        return new SendMessageResult(
            id,
            NormalizedMessageStatus.ACCEPTED,
            false,
            result.path("key").path("id").asText(null),
            null
        );
    }

    @Override
    public NormalizedInboundMessage normalizeInbound(Map<String, String> payload) {
        throw ApiException.badRequest("Evolution utiliza o adapter de webhook JSON");
    }

    private void requireChannel(WhatsAppChannel channel) {
        config.requireConfigured();
        if (
            channel.getProvider() != ProviderType.EVOLUTION ||
            !config.instanceName().equals(channel.getExternalChannelId())
        ) {
            throw ApiException.conflict("Canal não corresponde à instância Evolution configurada");
        }
    }
}
