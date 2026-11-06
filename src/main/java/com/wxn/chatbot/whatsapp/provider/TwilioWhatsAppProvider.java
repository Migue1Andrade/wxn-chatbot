package com.wxn.chatbot.whatsapp.provider;

import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.wxn.chatbot.common.PhoneNumbers;
import com.wxn.chatbot.whatsapp.config.TwilioProperties;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy")
public class TwilioWhatsAppProvider implements WhatsAppProvider, WhatsAppStatusNormalizer {

    private final TwilioProperties config;

    public TwilioWhatsAppProvider(TwilioProperties config) {
        this.config = config;
    }

    public ProviderType type() {
        return ProviderType.TWILIO;
    }

    public java.util.Set<MessageCapability> messageCapabilities() {
        return java.util.Set.of(MessageCapability.SEND_TEXT, MessageCapability.MESSAGE_STATUS);
    }

    public ProviderCapabilities capabilities() {
        return new ProviderCapabilities(true, false, false, true, false);
    }

    public void validateChannel(WhatsAppChannel channel) {
        config.requireConfigured();
        if (
            !config.getChannelId().equals(channel.getId()) ||
            channel.getProvider() != type() ||
            !PhoneNumbers.required(config.getPhoneNumber()).equals(channel.getPhoneNumber())
        ) {
            throw com.wxn.chatbot.common.ApiException.conflict(
                "Canal não corresponde à configuração segura Twilio"
            );
        }
    }

    public SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request) {
        validateChannel(channel);
        // Use explicit channel credentials instead of global Twilio.init().
        TwilioRestClient client = new TwilioRestClient.Builder(
            config.getAccountSid(),
            config.getAuthToken()
        ).build();
        try {
            Message result = Message.creator(
                new PhoneNumber("whatsapp:" + request.recipientPhone()),
                new PhoneNumber("whatsapp:" + channel.getPhoneNumber()),
                request.content()
            )
                .setStatusCallback(URI.create(config.statusUrl()))
                .create(client);
            return new SendMessageResult(result.getSid(), status(result.getStatus().toString()), false);
        } catch (ApiException error) {
            throw new ProviderSendException("TWILIO_" + error.getCode());
        } catch (RuntimeException error) {
            throw new ProviderSendException("TWILIO_TRANSPORT_UNKNOWN");
        }
    }

    public NormalizedInboundMessage normalizeInbound(Map<String, String> payload) {
        String sid = required(payload, "MessageSid");
        String body = payload.getOrDefault("Body", "");
        int mediaCount;
        try {
            mediaCount = Integer.parseInt(payload.getOrDefault("NumMedia", "0"));
        } catch (NumberFormatException error) {
            throw com.wxn.chatbot.common.ApiException.badRequest("NumMedia inválido");
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("text", body);
        if (mediaCount > 0) {
            // Retain metadata only. This version does not fetch media or expose authenticated URLs.
            content.put("mediaCount", mediaCount);
        }
        return new NormalizedInboundMessage(
            sid,
            PhoneNumbers.required(required(payload, "From")),
            PhoneNumbers.required(required(payload, "To")),
            mediaCount > 0 ? MessageType.MEDIA : MessageType.TEXT,
            content,
            Instant.now(),
            displayName(payload.get("ProfileName"))
        );
    }

    public NormalizedStatusEvent normalizeStatus(Map<String, String> payload) {
        String code = payload.get("ErrorCode");
        if (code != null && !code.matches("[0-9]{1,10}")) {
            code = "UNKNOWN";
        }
        return new NormalizedStatusEvent(
            required(payload, "MessageSid"),
            status(required(payload, "MessageStatus")),
            code
        );
    }

    public NormalizedMessageStatus status(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "accepted", "queued", "sending", "scheduled" -> NormalizedMessageStatus.ACCEPTED;
            case "sent" -> NormalizedMessageStatus.SENT;
            case "delivered" -> NormalizedMessageStatus.DELIVERED;
            case "read" -> NormalizedMessageStatus.READ;
            case "received" -> NormalizedMessageStatus.RECEIVED;
            case "failed", "undelivered", "canceled" -> NormalizedMessageStatus.FAILED;
            default -> NormalizedMessageStatus.UNKNOWN;
        };
    }

    private String displayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value.trim();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private String required(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank() || value.length() > 255) {
            throw com.wxn.chatbot.common.ApiException.badRequest("Payload Twilio inválido");
        }
        return value;
    }
}
