package com.wxn.chatbot.whatsapp.zapi;

import com.wxn.chatbot.whatsapp.model.DeleteMessageRequest;
import com.wxn.chatbot.whatsapp.provider.ProviderSendException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Profile("legacy")
public class ZApiClient {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
        new com.fasterxml.jackson.databind.ObjectMapper().configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        );
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ZApiClient.class);
    private final ZApiConfiguration config;
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public ZApiClient(ZApiConfiguration config) {
        this.config = config;
        var http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    ZApiClient(ZApiConfiguration config, RestClient client) {
        this.config = config;
        this.client = client;
    }

    private <T> T call(HttpMethod method, URI uri, Object body, Class<T> responseType) {
        long started = System.nanoTime();
        String correlation = java.util.UUID.randomUUID().toString();
        var outcome = new java.util.concurrent.atomic.AtomicReference<String>("UNKNOWN");
        try {
            var request = client
                .method(method)
                .uri(uri)
                .header("Client-Token", config.clientToken())
                .contentType(MediaType.APPLICATION_JSON);
            if (body != null) {
                request.body(body);
            }
            return request.exchange((outgoing, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new ProviderSendException(
                        response.getStatusCode().is4xxClientError()
                            ? "ZAPI_HTTP_" + response.getStatusCode().value()
                            : "ZAPI_CONFIRMATION_UNKNOWN"
                    );
                }
                byte[] data = response.getBody().readNBytes(1048577);
                if (data.length == 0 || data.length > 1048576) {
                    throw new ProviderSendException("ZAPI_CONFIRMATION_UNKNOWN");
                }
                try {
                    T result = JSON.readValue(data, responseType);
                    outcome.set("ACKNOWLEDGED");
                    return result;
                } catch (java.io.IOException invalid) {
                    throw new ProviderSendException("ZAPI_CONFIRMATION_UNKNOWN");
                }
            });
        } catch (ProviderSendException error) {
            outcome.set(error.code());
            throw error;
        } catch (RestClientResponseException error) {
            // Never retain exception messages, response bodies or causes: they may contain credentials.
            throw new ProviderSendException(
                error.getStatusCode().is4xxClientError()
                    ? "ZAPI_HTTP_" + error.getStatusCode().value()
                    : "ZAPI_CONFIRMATION_UNKNOWN"
            );
        } catch (RestClientException error) {
            throw new ProviderSendException("ZAPI_CONFIRMATION_UNKNOWN");
        } finally {
            LOGGER.info(
                "whatsapp_provider correlationId={} provider=ZAPI channelId={} operation={} outcome={} durationMs={}",
                correlation,
                config.channelId(),
                uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1),
                outcome.get(),
                (System.nanoTime() - started) / 1000000
            );
        }
    }

    private URI endpoint(String operation) {
        return URI.create(config.baseUrl() + "/" + operation);
    }

    public ZApiDtos.Sent text(ZApiDtos.Text body) {
        return call(HttpMethod.POST, endpoint("send-text"), body, ZApiDtos.Sent.class);
    }

    public ZApiDtos.Sent forward(ZApiDtos.Forward body) {
        return call(HttpMethod.POST, endpoint("forward-message"), body, ZApiDtos.Sent.class);
    }

    public void delete(DeleteMessageRequest request) {
        URI uri = UriComponentsBuilder.fromUri(endpoint("messages"))
            .queryParam("messageId", request.externalMessageId())
            .queryParam("phone", digits(request.phone()))
            .queryParam("owner", request.owner())
            .queryParam("deleteForMe", request.intent() == DeleteMessageRequest.DeleteIntent.DELETE_FOR_ME)
            .build()
            .encode()
            .toUri();
        if (!call(HttpMethod.DELETE, uri, null, ZApiDtos.Value.class).value()) {
            throw new ProviderSendException("ZAPI_DELETE_REJECTED");
        }
    }

    public ZApiDtos.Connection connection() {
        return call(HttpMethod.GET, endpoint("status"), null, ZApiDtos.Connection.class);
    }

    public com.fasterxml.jackson.databind.JsonNode qr() {
        return call(
            HttpMethod.GET,
            endpoint("qr-code/image"),
            null,
            com.fasterxml.jackson.databind.JsonNode.class
        );
    }

    public void instanceOperation(String operation) {
        if (!java.util.Set.of("restart", "disconnect").contains(operation)) {
            throw com.wxn.chatbot.common.ApiException.badRequest("Operação inválida");
        }
        if (!call(HttpMethod.GET, endpoint(operation), null, ZApiDtos.Value.class).value()) {
            throw new ProviderSendException("ZAPI_OPERATION_REJECTED");
        }
    }

    public void mutation(String operation, String phone, String externalId, String value) {
        String digits = digits(phone);
        switch (operation) {
            case "EDIT_TEXT" -> acknowledgement(text(new ZApiDtos.Text(digits, value, null, externalId)));
            case "SEND_REACTION" -> acknowledgement(
                call(
                    HttpMethod.POST,
                    endpoint("send-reaction"),
                    java.util.Map.of("phone", digits, "messageId", externalId, "reaction", value),
                    ZApiDtos.Sent.class
                )
            );
            case "REMOVE_REACTION" -> acknowledgement(
                call(
                    HttpMethod.POST,
                    endpoint("send-remove-reaction"),
                    java.util.Map.of("phone", digits, "messageId", externalId),
                    ZApiDtos.Sent.class
                )
            );
            case "MARK_READ" -> {
                if (
                    !call(
                        HttpMethod.POST,
                        endpoint("read-message"),
                        java.util.Map.of("phone", digits, "messageId", externalId),
                        ZApiDtos.Value.class
                    ).value()
                ) {
                    throw new ProviderSendException("ZAPI_READ_REJECTED");
                }
            }
            default -> throw com.wxn.chatbot.common.ApiException.badRequest("Operação inválida");
        }
    }

    private void acknowledgement(ZApiDtos.Sent result) {
        if (result.messageId() == null || result.messageId().isBlank()) {
            throw new ProviderSendException("ZAPI_CONFIRMATION_UNKNOWN");
        }
    }

    public void registerWebhooks() {
        register("update-webhook-received", "received");
        register("update-webhook-received-delivery", "received");
        register("update-webhook-message-status", "message-status");
        register("update-webhook-delivery", "sent");
        register("update-webhook-connected", "connected");
        register("update-webhook-disconnected", "disconnected");
        var result = call(
            HttpMethod.PUT,
            endpoint("update-notify-sent-by-me"),
            new ZApiDtos.Notify(true),
            ZApiDtos.Value.class
        );
        if (!result.value()) {
            throw new ProviderSendException("ZAPI_WEBHOOK_CONFIGURATION_REJECTED");
        }
    }

    private void register(String operation, String callback) {
        var result = call(
            HttpMethod.PUT,
            endpoint(operation),
            new ZApiDtos.Webhook(config.callback(callback)),
            ZApiDtos.Value.class
        );
        if (!result.value()) {
            throw new ProviderSendException("ZAPI_WEBHOOK_CONFIGURATION_REJECTED");
        }
    }

    public static String digits(String phone) {
        return phone.startsWith("+") ? phone.substring(1) : phone;
    }
}
