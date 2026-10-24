package com.wxn.chatbot.whatsapp.provider;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
public class MockWhatsAppProvider implements WhatsAppProvider {

    public ProviderType type() {
        return ProviderType.MOCK;
    }

    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, false, false, true);
    }

    public SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request) {
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
