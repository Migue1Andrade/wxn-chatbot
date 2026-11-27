package com.wxn.chatbot.autoguia.generation;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "wxn.autoguia.generation", name = "enabled", havingValue = "true")
public class KnowledgeAnswerGenerationConfiguration {

    @Bean
    KnowledgeAnswerGenerator knowledgeAnswerGenerator(
        @Value("${wxn.autoguia.generation.endpoint}") URI endpoint,
        @Value("${wxn.autoguia.generation.model}") String model,
        @Value("${wxn.autoguia.generation.api-key:}") String apiKey,
        @Value("${wxn.autoguia.generation.connect-timeout:PT3S}") Duration connectTimeout,
        @Value("${wxn.autoguia.generation.timeout:PT45S}") Duration readTimeout
    ) {
        return new HttpKnowledgeAnswerGenerator(endpoint, model, apiKey, connectTimeout, readTimeout);
    }
}
