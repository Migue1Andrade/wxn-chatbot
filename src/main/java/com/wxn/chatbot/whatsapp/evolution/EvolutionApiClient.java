package com.wxn.chatbot.whatsapp.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.common.PhoneNumbers;
import com.wxn.chatbot.whatsapp.provider.ProviderSendException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EvolutionApiClient {

    private final EvolutionConfiguration config;
    private final RestClient client;

    public EvolutionApiClient(EvolutionConfiguration config) {
        this.config = config;
        var http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public JsonNode createInstance() {
        return call(
            "POST",
            "/instance/create",
            Map.of(
                "instanceName",
                config.instanceName(),
                "integration",
                "WHATSAPP-BAILEYS",
                "qrcode",
                true,
                "readMessages",
                true,
                "readStatus",
                true
            )
        );
    }

    public JsonNode instances() {
        return call("GET", "/instance/fetchInstances?instanceName=" + config.instanceName(), null);
    }

    public JsonNode connect() {
        return call("GET", "/instance/connect/" + config.instanceName(), null);
    }

    public JsonNode connectionState() {
        return call("GET", "/instance/connectionState/" + config.instanceName(), null);
    }

    public String connectedPhone() {
        JsonNode result = instances();
        JsonNode instance = result.isArray() && result.size() > 0 ? result.get(0) : result;
        String owner = instance.path("ownerJid").asText(null);
        if (owner == null) {
            owner = instance.path("owner").asText(null);
        }
        if (owner == null || !owner.contains("@")) {
            return null;
        }
        String digits = owner.substring(0, owner.indexOf('@'));
        return PhoneNumbers.optional(digits.startsWith("+") ? digits : "+" + digits);
    }

    public JsonNode sendText(String phone, String text) {
        return call(
            "POST",
            "/message/sendText/" + config.instanceName(),
            Map.of("number", digits(phone), "text", text)
        );
    }

    public JsonNode sendText(String phone, String text, String quotedId, String quotedText) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("number", digits(phone));
        body.put("text", text);
        if (quotedId != null && !quotedId.isBlank()) {
            body.put(
                "quoted",
                Map.of(
                    "key",
                    Map.of("id", quotedId),
                    "message",
                    Map.of("conversation", quotedText == null ? "" : quotedText)
                )
            );
        }
        return call("POST", "/message/sendText/" + config.instanceName(), body);
    }

    public JsonNode updateMessage(String phone, String externalId, String text) {
        String remoteJid = digits(phone) + "@s.whatsapp.net";
        return call(
            "POST",
            "/chat/updateMessage/" + config.instanceName(),
            Map.of(
                "number",
                digits(phone),
                "text",
                text,
                "key",
                Map.of("id", externalId, "remoteJid", remoteJid, "fromMe", true)
            )
        );
    }

    public JsonNode deleteMessage(String phone, String externalId, boolean owner) {
        return call(
            "DELETE",
            "/chat/deleteMessageForEveryone/" + config.instanceName(),
            Map.of("id", externalId, "remoteJid", digits(phone) + "@s.whatsapp.net", "fromMe", owner)
        );
    }

    public JsonNode configureWebhook() {
        return call(
            "POST",
            "/webhook/set/" + config.instanceName(),
            Map.of(
                "webhook",
                Map.of(
                    "enabled",
                    true,
                    "url",
                    config.webhookUrl(),
                    "webhookByEvents",
                    false,
                    "webhookBase64",
                    false,
                    "events",
                    List.of(
                        "QRCODE_UPDATED",
                        "MESSAGES_UPSERT",
                        "MESSAGES_UPDATE",
                        "MESSAGES_DELETE",
                        "SEND_MESSAGE",
                        "CONNECTION_UPDATE"
                    )
                )
            )
        );
    }

    private JsonNode call(String method, String path, Object body) {
        config.requireConfigured();
        try {
            var request = client
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(URI.create(config.apiUrl() + path))
                .header("apikey", config.apiKey())
                .contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.body(body);
            }
            return request.retrieve().body(JsonNode.class);
        } catch (RestClientResponseException error) {
            throw new ProviderSendException("EVOLUTION_HTTP_" + error.getStatusCode().value());
        } catch (RestClientException error) {
            throw new ProviderSendException("EVOLUTION_API_UNAVAILABLE");
        }
    }

    static String digits(String phone) {
        if (phone == null || !phone.matches("\\+?[0-9]{8,20}")) {
            throw ApiException.badRequest("Telefone inválido para a Evolution API");
        }
        return phone.replace("+", "");
    }
}
