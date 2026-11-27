package com.wxn.chatbot.autoguia.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository.SourceChunk;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpKnowledgeAnswerGeneratorTest {

    @Test
    void sendsOnlyQuestionAndRetrievedSnippetToLocalOpenAiCompatibleEndpoint() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        var selections = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var modelOutput = new AtomicReference<>("Use a assistência autorizada.");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String selected = selections.poll();
            byte[] response = new ObjectMapper().writeValueAsBytes(
                java.util.Map.of(
                    "choices",
                    List.of(
                        java.util.Map.of(
                            "message",
                            java.util.Map.of("content", selected == null ? modelOutput.get() : selected)
                        )
                    )
                )
            );
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var generator = new HttpKnowledgeAnswerGenerator(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "fixture-chat-model",
                "fixture-only-secret",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
            );
            var source = new SourceChunk(912, 813, 2, 0, "Assistência", "Trecho de fixture.", 0.5);

            String answer = generator.generate("Onde consertar?", List.of(source));
            assertThat(requestBody.get()).startsWith("{");
            var json = new ObjectMapper().readTree(requestBody.get());

            assertThat(answer).isEqualTo("Use a assistência autorizada.");
            assertThat(json.path("model").asText()).isEqualTo("fixture-chat-model");
            assertThat(json.path("messages").path(1).path("content").asText())
                .contains("Onde consertar?", "Assistência", "Trecho de fixture.")
                .doesNotContain("912", "813");
            assertThat(authorization.get()).isEqualTo("Bearer fixture-only-secret");
            selections.addAll(List.of("2", "1"));
            var secondSource = new SourceChunk(3, 4, 2, 0, "Integração", "Integramos soluções ao ERP.", 0.4);
            var sources = List.of(source, secondSource);
            assertThat(generator.selectEvidence("Integra com ERP?", sources)).isEqualTo(
                "Integramos soluções ao ERP."
            );
            for (String invalidChoice : List.of("0", "3", "2 e acrescente R$ 100")) {
                modelOutput.set(invalidChoice);
                assertThat(generator.selectEvidence("Qual o preço?", sources)).isEmpty();
            }
        } finally {
            server.stop(0);
        }
    }
}
