package com.wxn.chatbot.openbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wxn.chatbot.autoguia.embedding.HttpKnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.generation.HttpKnowledgeAnswerGenerator;
import com.wxn.chatbot.autoguia.generation.KnowledgeAnswerGenerator;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository;
import com.wxn.chatbot.autoguia.service.KnowledgePreviewService;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "WXN_REAL_GROUNDING_TEST", matches = "true")
class RealWxnGroundingTest {

    @Test
    void answersOnlyWhenWxnArticlesCoverTheQuestion() {
        var dataSource = new DriverManagerDataSource(
            required("WXN_GROUNDING_DB_URL"),
            required("POSTGRES_USER"),
            required("POSTGRES_PASSWORD")
        );
        var jdbc = new JdbcTemplate(dataSource);
        URI endpoint = URI.create(required("WXN_GROUNDING_OLLAMA_URL"));
        var embedding = new HttpKnowledgeEmbeddingProvider(
            endpoint,
            "ollama",
            "embeddinggemma:300m",
            768,
            "",
            Duration.ofSeconds(3),
            Duration.ofSeconds(90)
        );
        var generator = new HttpKnowledgeAnswerGenerator(
            endpoint,
            required("WXN_GROUNDING_MODEL"),
            "",
            Duration.ofSeconds(3),
            Duration.ofSeconds(45)
        );
        var preview = new KnowledgePreviewService(
            new KnowledgeRetrievalRepository(jdbc),
            provider(KnowledgeEmbeddingProvider.class, embedding),
            provider(KnowledgeAnswerGenerator.class, generator),
            true,
            true,
            true,
            0.25
        );
        var support = new OpenBotSupportService(null, null, null, null, null, null, null, preview, true);
        List<Scenario> scenarios = List.of(
            new Scenario("Dá para ligar meu software de gestão aos outros sistemas?", "ERP"),
            new Scenario("Preciso trocar de plataforma sem perder meus dados", "migra"),
            new Scenario("A WXN oferece consultoria para TOTVS Protheus?", "Protheus"),
            new Scenario("Qual o preço mensal de hospedagem?", ""),
            new Scenario("Vocês vendem pneus automotivos?", ""),
            new Scenario("Qual o prazo garantido para migrar meu sistema?", ""),
            new Scenario("Quero falar com um atendente", "", "HUMAN_REQUEST")
        );
        var failures = new java.util.ArrayList<String>();
        for (Scenario scenario : scenarios) {
            long started = System.nanoTime();
            var answer = support.retrievalContext(scenario.question());
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            boolean expectedHandoff = scenario.expectedExcerpt().isEmpty();
            boolean passed =
                scenario.expectedMode().equals(answer.mode()) &&
                answer.handoff() == expectedHandoff &&
                (expectedHandoff || answer.fallback().contains(scenario.expectedExcerpt()));
            System.out.printf(
                "WXN %s | %s | %d ms | %s | %s%n",
                passed ? "OK" : "FALHOU",
                answer.mode(),
                elapsedMillis,
                scenario.question(),
                answer.handoff() ? "equipe" : answer.fallback()
            );
            if (!passed) {
                failures.add(scenario.question());
            }
        }
        assertThat(failures).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(type.cast(instance));
        return provider;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing: " + name);
        }
        return value;
    }

    private record Scenario(String question, String expectedExcerpt, String expectedMode) {
        private Scenario(String question, String expectedExcerpt) {
            this(question, expectedExcerpt, "HYBRID");
        }
    }
}
