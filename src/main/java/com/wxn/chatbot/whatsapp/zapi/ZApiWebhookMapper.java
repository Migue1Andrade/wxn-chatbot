package com.wxn.chatbot.whatsapp.zapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.*;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy")
public class ZApiWebhookMapper {

    public NormalizedInboundMessage text(JsonNode body) {
        String phone = phone(required(body, "phone"));
        if (required(body.path("text"), "message").length() > 16384) {
            throw ApiException.badRequest("Texto excede o limite");
        }
        String connected = phone(required(body, "connectedPhone"));
        String text = required(body.path("text"), "message");
        return new NormalizedInboundMessage(
            required(body, "messageId"),
            phone,
            connected,
            MessageType.TEXT,
            Map.of("text", text),
            moment(body),
            body.path("senderName").asText(null),
            body.path("referenceMessageId").asText(null),
            body.path("senderLid").isTextual() && body.path("senderLid").asText().length() < 256
                ? body.path("senderLid").asText()
                : null
        );
    }

    public static String phone(String value) {
        return PhoneNumbers.required(value.startsWith("+") ? value : "+" + value);
    }

    public boolean supported(JsonNode body) {
        return (
            "ReceivedCallback".equals(body.path("type").asText()) &&
            !body.path("isGroup").asBoolean() &&
            !body.path("isNewsletter").asBoolean() &&
            !body.path("isEdit").asBoolean() &&
            !body.path("waitingMessage").asBoolean() &&
            body.path("text").path("message").isTextual()
        );
    }

    public NormalizedMessageStatus status(String value) {
        return switch (value) {
            case "SENT" -> NormalizedMessageStatus.SENT;
            case "RECEIVED" -> NormalizedMessageStatus.DELIVERED;
            case "READ" -> NormalizedMessageStatus.READ;
            // READ_BY_ME is the connected account's own read; PLAYED is media, not text delivery.
            default -> NormalizedMessageStatus.UNKNOWN;
        };
    }

    public static String required(JsonNode body, String key) {
        var value = body.path(key);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 16384) {
            throw ApiException.badRequest("Payload Z-API inválido: " + key);
        }
        return value.asText();
    }

    public static Instant moment(JsonNode body) {
        long value = body.path("momment").asLong(0);
        if (value <= 0) {
            throw ApiException.badRequest("Timestamp Z-API inválido");
        }
        try {
            return Instant.ofEpochMilli(value);
        } catch (RuntimeException error) {
            throw ApiException.badRequest("Timestamp Z-API inválido");
        }
    }
}
