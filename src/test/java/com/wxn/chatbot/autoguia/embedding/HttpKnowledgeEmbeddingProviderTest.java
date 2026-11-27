package com.wxn.chatbot.autoguia.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

class HttpKnowledgeEmbeddingProviderTest {

    @Test
    void mapsResponseAndPreservesConfiguredIdentity() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        withServer(
            "{\"data\":[{\"embedding\":[0.5, -0.25]}]}",
            0,
            requestBody,
            contentType,
            accept,
            server -> {
                var result = provider(server, Duration.ofSeconds(2)).embed("sample text");
                assertEquals("tenant-model-space", result.provider());
                assertEquals("model-v2", result.model());
                assertEquals(2, result.dimension());
                assertEquals(java.util.List.of(0.5, -0.25), result.values());
                assertThat(contentType.get()).startsWith("application/json");
                assertThat(accept.get()).contains("application/json");
                var request = new ObjectMapper().readTree(requestBody.get());
                assertThat(request.path("model").asText()).isEqualTo("model-v2");
                assertThat(request.path("input").asText()).isEqualTo("sample text");
            }
        );
    }

    @Test
    void rejectsNonNumericEmbeddingElement() throws Exception {
        withServer("{\"data\":[{\"embedding\":[0.5, \"bad\"]}]}", 0, server ->
            assertThrows(IllegalStateException.class, () ->
                provider(server, Duration.ofSeconds(2)).embed("sample text")
            )
        );
    }

    @Test
    void appliesReadTimeoutToSlowProvider() throws Exception {
        withServer("{\"data\":[{\"embedding\":[0.5, -0.25]}]}", 350, server ->
            assertThrows(RestClientException.class, () ->
                provider(server, Duration.ofMillis(80)).embed("sample text")
            )
        );
    }

    private static HttpKnowledgeEmbeddingProvider provider(HttpServer server, Duration timeout) {
        return new HttpKnowledgeEmbeddingProvider(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "tenant-model-space",
            "model-v2",
            2,
            "",
            Duration.ofSeconds(1),
            timeout
        );
    }

    private static void withServer(String response, long delayMillis, ServerAction action) throws Exception {
        withServer(
            response,
            delayMillis,
            new AtomicReference<>(),
            new AtomicReference<>(),
            new AtomicReference<>(),
            action
        );
    }

    private static void withServer(
        String response,
        long delayMillis,
        AtomicReference<String> requestBody,
        AtomicReference<String> contentType,
        AtomicReference<String> accept,
        ServerAction action
    ) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            try {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            action.run(server);
        } finally {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface ServerAction {
        void run(HttpServer server) throws Exception;
    }
}
