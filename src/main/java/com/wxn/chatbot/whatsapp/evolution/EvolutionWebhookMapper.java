package com.wxn.chatbot.whatsapp.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.common.PhoneNumbers;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EvolutionWebhookMapper {

    public boolean supported(JsonNode data) {
        return (
            data.path("key").path("remoteJid").isTextual() &&
            text(data.path("message")) != null &&
            !data.path("key").path("remoteJid").asText().endsWith("@g.us") &&
            !data.path("key").path("remoteJid").asText().equals("status@broadcast")
        );
    }

    public NormalizedInboundMessage text(JsonNode data, String connectedPhone) {
        String remote = phone(data.path("key").path("remoteJid").asText());
        String content = text(data.path("message"));
        if (content == null || content.length() > 1600) {
            throw ApiException.badRequest("Texto Evolution inválido ou excede 1600 caracteres");
        }
        long timestamp = data.path("messageTimestamp").asLong(0);
        return new NormalizedInboundMessage(
            required(data.path("key").path("id").asText(null)),
            remote,
            PhoneNumbers.required(connectedPhone),
            MessageType.TEXT,
            Map.of("text", content),
            timestamp > 0 ? Instant.ofEpochSecond(timestamp) : Instant.now(),
            data.path("pushName").asText(null),
            data.path("message").path("extendedTextMessage").path("contextInfo").path("stanzaId").asText(null)
        );
    }

    public NormalizedMessageStatus status(JsonNode value) {
        String status = value.isNumber() ? value.asText() : value.asText("").toUpperCase();
        return switch (status) {
            case "1", "SERVER_ACK", "SENT" -> NormalizedMessageStatus.SENT;
            case "2", "DELIVERY_ACK", "DELIVERED", "RECEIVED" -> NormalizedMessageStatus.DELIVERED;
            case "3", "READ" -> NormalizedMessageStatus.READ;
            case "5", "ERROR", "FAILED" -> NormalizedMessageStatus.FAILED;
            default -> NormalizedMessageStatus.UNKNOWN;
        };
    }

    private String text(JsonNode message) {
        String conversation = message.path("conversation").asText(null);
        if (conversation != null) {
            return conversation;
        }
        return message.path("extendedTextMessage").path("text").asText(null);
    }

    private String phone(String value) {
        int separator = value.indexOf('@');
        String digits = separator < 0 ? value : value.substring(0, separator);
        return PhoneNumbers.required(digits.startsWith("+") ? digits : "+" + digits);
    }

    private String required(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw ApiException.badRequest("ID externo Evolution inválido");
        }
        return value;
    }
}
