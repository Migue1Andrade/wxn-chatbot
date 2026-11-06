package com.wxn.chatbot.whatsapp.zapi;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy")
public class ZApiWhatsAppProvider implements WhatsAppProvider {

    private final ZApiConfiguration config;
    private final ZApiClient client;

    public ZApiWhatsAppProvider(ZApiConfiguration config, ZApiClient client) {
        this.config = config;
        this.client = client;
    }

    public void validateChannel(WhatsAppChannel channel) {
        config.requireChannel(channel);
    }

    public ProviderType type() {
        return ProviderType.ZAPI;
    }

    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, false, true, false);
    }

    public Set<MessageCapability> messageCapabilities() {
        return Set.of(
            MessageCapability.SEND_TEXT,
            MessageCapability.REPLY_TEXT,
            MessageCapability.FORWARD_TEXT,
            MessageCapability.DELETE_FOR_EVERYONE,
            MessageCapability.EDIT_TEXT,
            MessageCapability.SEND_REACTION,
            MessageCapability.REMOVE_REACTION,
            MessageCapability.MARK_READ,
            MessageCapability.CONNECTION_STATUS,
            MessageCapability.QR_CODE,
            MessageCapability.CONFIGURE_WEBHOOKS,
            MessageCapability.RESTART_INSTANCE,
            MessageCapability.DISCONNECT_INSTANCE,
            MessageCapability.MESSAGE_STATUS
        );
    }

    public SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request) {
        return reply(channel, request, null);
    }

    public SendMessageResult reply(WhatsAppChannel channel, SendMessageRequest request, String externalId) {
        config.requireChannel(channel);
        if ("DISCONNECTED".equals(connection(channel).state())) {
            throw new ProviderSendException("CHANNEL_DISCONNECTED");
        }
        return result(
            client.text(
                new ZApiDtos.Text(ZApiClient.digits(request.recipientPhone()), request.content(), externalId)
            )
        );
    }

    public SendMessageResult forward(WhatsAppChannel channel, ForwardMessageRequest request) {
        config.requireChannel(channel);
        if ("DISCONNECTED".equals(connection(channel).state())) {
            throw new ProviderSendException("CHANNEL_DISCONNECTED");
        }
        return result(
            client.forward(
                new ZApiDtos.Forward(
                    ZApiClient.digits(request.destination().recipientPhone()),
                    request.externalMessageId(),
                    ZApiClient.digits(request.sourcePhone())
                )
            )
        );
    }

    public void delete(WhatsAppChannel channel, DeleteMessageRequest request) {
        config.requireChannel(channel);
        client.delete(request);
    }

    private SendMessageResult result(ZApiDtos.Sent result) {
        if (result.messageId() == null || result.messageId().isBlank()) {
            throw new ProviderSendException("ZAPI_CONFIRMATION_UNKNOWN");
        }
        return new SendMessageResult(
            result.messageId(),
            NormalizedMessageStatus.ACCEPTED,
            false,
            result.zaapId(),
            result.id()
        );
    }

    public ChannelConnection connection(WhatsAppChannel channel) {
        try {
            config.requireChannel(channel);
        } catch (ApiException missing) {
            return ChannelConnection.of("NOT_CONFIGURED", false, "CHANNEL_NOT_CONFIGURED");
        }
        try {
            var result = client.connection();
            if (result.connected() == null) {
                return ChannelConnection.of("UNKNOWN", false, "PROVIDER_RESPONSE_INVALID");
            }
            return ChannelConnection.of(
                result.connected() ? "CONNECTED" : "DISCONNECTED",
                result.connected(),
                null
            );
        } catch (ProviderSendException unavailable) {
            return ChannelConnection.of("UNKNOWN", false, unavailable.code());
        }
    }

    public ConnectionInstructions instructions(WhatsAppChannel channel) {
        config.requireChannel(channel);
        var result = client.qr();
        if (result.has("challenge")) {
            return new ConnectionInstructions(
                "CHALLENGE_REQUIRED",
                null,
                "Conclua a verificação no painel Z-API."
            );
        }
        String value = result.isTextual() ? result.asText() : result.path("value").asText();
        if (
            !value.startsWith("data:image/png;base64,") ||
            value.length() > 900000 ||
            !value.substring(22).matches("[a-zA-Z0-9+/=\\r\\n]+")
        ) {
            throw new ProviderSendException("ZAPI_QR_RESPONSE_INVALID");
        }
        return new ConnectionInstructions(
            "QR_REQUIRED",
            value,
            "Escaneie com WhatsApp > Aparelhos conectados."
        );
    }

    public void configureWebhooks(WhatsAppChannel channel) {
        config.requireChannel(channel);
        client.registerWebhooks();
    }

    public void instanceOperation(WhatsAppChannel channel, String operation) {
        config.requireChannel(channel);
        client.instanceOperation(operation);
    }

    public String mutate(
        WhatsAppChannel channel,
        String operation,
        String phone,
        String externalId,
        String value
    ) {
        config.requireChannel(channel);
        client.mutation(operation, phone, externalId, value);
        return "MARK_READ".equals(operation) ? "CONFIRMED" : "ACCEPTED";
    }

    public NormalizedInboundMessage normalizeInbound(Map<String, String> payload) {
        throw ApiException.badRequest("Z-API utiliza o adapter de webhook JSON");
    }
}
