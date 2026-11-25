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
        String remoteJid = data.path("key").path("remoteJid").asText();
        if (remoteJid.endsWith("@lid") && data.path("key").path("remoteJidAlt").isTextual()) {
            remoteJid = data.path("key").path("remoteJidAlt").asText();
        }
        String remote = phone(remoteJid);
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
            data
                .path("message")
                .path("extendedTextMessage")
                .path("contextInfo")
                .path("stanzaId")
                .asText(null),
            remoteJid
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

    public String editedText(JsonNode data) {
        JsonNode message = data.path("message");
        JsonNode protocol = message.path("protocolMessage");
        String value = text(protocol.path("editedMessage"));
        if (value == null) {
            value = text(message.path("editedMessage"));
        }
        if (value == null) {
            value = text(data.path("editedMessage"));
        }
        if (value == null) {
            value = text(data.path("update").path("editedMessage"));
        }
        if (value == null) {
            value = text(data.path("update").path("message").path("editedMessage"));
        }
        if (value == null) {
            value = text(data.path("update").path("message").path("protocolMessage").path("editedMessage"));
        }
        return value;
    }

    public String messageId(JsonNode data) {
        String value = data.path("key").path("id").asText(null);
        if (value == null) {
            value = data.path("keyId").asText(null);
        }
        if (value == null) {
            value = data.path("messageId").asText(null);
        }
        if (value == null) {
            value = data.path("update").path("key").path("id").asText(null);
        }
        if (value == null) {
            value = data.path("update").path("keyId").asText(null);
        }
        if (value == null) {
            value = data.path("update").path("messageId").asText(null);
        }
        if (value == null) {
            value = data
                .path("update")
                .path("message")
                .path("protocolMessage")
                .path("key")
                .path("id")
                .asText(null);
        }
        if (value == null) {
            value = data.path("message").path("protocolMessage").path("key").path("id").asText(null);
        }
        if (value == null) {
            value = data
                .path("message")
                .path("protocolMessage")
                .path("editedMessage")
                .path("key")
                .path("id")
                .asText(null);
        }
        return value;
    }

    public String remotePhone(JsonNode data) {
        String remoteJid = data.path("key").path("remoteJid").asText(null);
        if (remoteJid == null) {
            remoteJid = data.path("remoteJid").asText(null);
        }
        if (remoteJid == null) {
            remoteJid = data.path("update").path("key").path("remoteJid").asText(null);
        }
        return remoteJid == null ? null : phone(remoteJid);
    }

    public Instant timestamp(JsonNode data) {
        long value = data.path("messageTimestamp").asLong(0);
        return value > 0 ? Instant.ofEpochSecond(value) : Instant.now();
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
