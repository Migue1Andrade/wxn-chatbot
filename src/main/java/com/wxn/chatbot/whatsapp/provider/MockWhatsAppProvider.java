package com.wxn.chatbot.whatsapp.provider;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy & dev & !prod")
public class MockWhatsAppProvider implements WhatsAppProvider {

    private final java.util.concurrent.ConcurrentMap<Long, String> states =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<Long, String> scenarios =
        new java.util.concurrent.ConcurrentHashMap<>();

    public void scenario(Long channelId, String scenario) {
        scenarios.put(channelId, scenario);
    }

    public ChannelConnection connection(WhatsAppChannel channel) {
        boolean connected = !"DISCONNECTED".equals(states.get(channel.getId()));
        return ChannelConnection.of(connected ? "CONNECTED" : "DISCONNECTED", connected, null);
    }

    public void instanceOperation(WhatsAppChannel channel, String operation) {
        states.put(channel.getId(), "disconnect".equals(operation) ? "DISCONNECTED" : "CONNECTED");
    }

    public String mutate(
        WhatsAppChannel channel,
        String operation,
        String phone,
        String externalId,
        String value
    ) {
        if (!connection(channel).available()) {
            throw new ProviderSendException("CHANNEL_DISCONNECTED");
        }
        return "CONFIRMED";
    }

    public ProviderType type() {
        return ProviderType.MOCK;
    }

    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, false, true, true);
    }

    public java.util.Set<MessageCapability> messageCapabilities() {
        return java.util.Set.of(
            MessageCapability.SEND_TEXT,
            MessageCapability.REPLY_TEXT,
            MessageCapability.FORWARD_TEXT,
            MessageCapability.DELETE_FOR_EVERYONE,
            MessageCapability.EDIT_TEXT,
            MessageCapability.SEND_REACTION,
            MessageCapability.REMOVE_REACTION,
            MessageCapability.MARK_READ,
            MessageCapability.CONNECTION_STATUS,
            MessageCapability.RESTART_INSTANCE,
            MessageCapability.DISCONNECT_INSTANCE,
            MessageCapability.MESSAGE_STATUS
        );
    }

    public SendMessageResult reply(WhatsAppChannel channel, SendMessageRequest request, String externalId) {
        return send(channel, request);
    }

    public SendMessageResult forward(WhatsAppChannel channel, ForwardMessageRequest request) {
        return send(channel, request.destination());
    }

    public void delete(WhatsAppChannel channel, DeleteMessageRequest request) {
        // Mock remote account is represented by the canonical local tombstone and audit log.
    }

    public SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request) {
        if (!connection(channel).available()) {
            throw new ProviderSendException("CHANNEL_DISCONNECTED");
        }
        String scenario = scenarios.remove(channel.getId());
        if ("TIMEOUT".equals(scenario)) {
            throw new ProviderSendException("PROVIDER_CONFIRMATION_UNKNOWN");
        }
        if ("FAIL".equals(scenario)) {
            throw new ProviderSendException("MOCK_CONFIRMED_FAILURE");
        }
        return new SendMessageResult(
            "mock-" + channel.getId() + "-" + request.internalMessageId(),
            NormalizedMessageStatus.SIMULATED,
            true
        );
    }

    public NormalizedInboundMessage normalizeInbound(Map<String, String> payload) {
        String id = payload.get("externalMessageId");
        String text = payload.get("content");
        if (
            id == null ||
            id.isBlank() ||
            id.length() > 255 ||
            text == null ||
            text.isBlank() ||
            text.length() > 1600
        ) {
            throw ApiException.badRequest("externalMessageId e content são obrigatórios");
        }
        return new NormalizedInboundMessage(
            id,
            PhoneNumbers.required(payload.get("senderPhone")),
            PhoneNumbers.required(payload.get("recipientPhone")),
            MessageType.TEXT,
            Map.of("text", text),
            Instant.now(),
            payload.get("senderName")
        );
    }
}
