package com.wxn.chatbot.autoguia.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.autoguia.domain.EmbeddingVector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class HttpKnowledgeEmbeddingProvider implements KnowledgeEmbeddingProvider {

    private final RestClient client;
    private final String provider;
    private final String model;
    private final String apiKey;
    private final int dimension;

    public HttpKnowledgeEmbeddingProvider(
        URI endpoint,
        String provider,
        String model,
        int dimension,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
    ) {
        if (
            endpoint == null ||
            provider == null ||
            provider.isBlank() ||
            model == null ||
            model.isBlank() ||
            dimension <= 0 ||
            invalidTimeout(connectTimeout) ||
            invalidTimeout(readTimeout)
        ) {
            throw new IllegalArgumentException("Invalid embedding provider configuration");
        }
        this.provider = provider;
        this.model = model;
        this.dimension = dimension;
        this.apiKey = apiKey;
        var httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(factory).baseUrl(endpoint.toString()).build();
    }

    @Override
    public EmbeddingVector embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding input is required");
        }
        var request = client
            .post()
            .uri("/v1/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(Map.of("input", text, "model", model));
        if (apiKey != null && !apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        JsonNode response;
        try {
            response = request.retrieve().body(JsonNode.class);
        } catch (CancellationException cancelled) {
            throw new RestClientException("Embedding request was cancelled", cancelled);
        }
        JsonNode vector = response == null ? null : response.path("data").path(0).path("embedding");
        if (vector == null || !vector.isArray()) {
            throw new IllegalStateException("Embedding provider returned an invalid response");
        }
        List<Double> values = new java.util.ArrayList<>();
        vector.forEach(value -> {
            if (!value.isNumber()) {
                throw new IllegalStateException("Embedding provider returned a non-numeric value");
            }
            values.add(value.doubleValue());
        });
        return new EmbeddingVector(provider, model, dimension, values);
    }

    private static boolean invalidTimeout(Duration timeout) {
        return timeout == null || timeout.isNegative() || timeout.isZero();
    }
}
