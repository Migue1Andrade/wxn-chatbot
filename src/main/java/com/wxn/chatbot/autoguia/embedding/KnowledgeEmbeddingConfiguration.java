package com.wxn.chatbot.autoguia.embedding;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "wxn.autoguia.embedding", name = "enabled", havingValue = "true")
public class KnowledgeEmbeddingConfiguration {

    @Bean
    KnowledgeEmbeddingProvider knowledgeEmbeddingProvider(
        @Value("${wxn.autoguia.embedding.endpoint}") URI endpoint,
        @Value("${wxn.autoguia.embedding.provider}") String provider,
        @Value("${wxn.autoguia.embedding.model}") String model,
        @Value("${wxn.autoguia.embedding.dimension}") int dimension,
        @Value("${wxn.autoguia.embedding.api-key:}") String apiKey,
        @Value("${wxn.autoguia.embedding.connect-timeout:PT3S}") Duration connectTimeout,
        @Value("${wxn.autoguia.embedding.timeout:PT15S}") Duration readTimeout
    ) {
        return new HttpKnowledgeEmbeddingProvider(
            endpoint,
            provider,
            model,
            dimension,
            apiKey,
            connectTimeout,
            readTimeout
        );
    }
}
