package com.wxn.chatbot.autoguia;

import static org.assertj.core.api.Assertions.assertThat;

import com.wxn.chatbot.autoguia.domain.EmbeddingVector;
import com.wxn.chatbot.autoguia.embedding.HttpKnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository;
import com.wxn.chatbot.autoguia.service.KnowledgeArticleService;
import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.user.service.UserService;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in functional pilot using a real local embedding endpoint and a disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "WXN_REAL_EMBEDDING_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "WXN_INTEGRATION_TEST", matches = "true")
@SpringBootTest(properties = "openbot.support.enabled=false")
class RealEmbeddingPilotIntegrationTest {

    @Autowired
    KnowledgeArticleService articles;

    @Autowired
    KnowledgeRetrievalRepository retrieval;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService users;

    @Test
    void comparesRealLocalEmbeddingsAcrossFixedCorpusAndSearchModes() {
        String endpoint = required("AUTOGUIA_EMBEDDING_ENDPOINT");
        String provider = required("AUTOGUIA_EMBEDDING_PROVIDER");
        String model = required("AUTOGUIA_EMBEDDING_MODEL");
        int dimension = Integer.parseInt(required("AUTOGUIA_EMBEDDING_DIMENSION"));
        var embeddings = new HttpKnowledgeEmbeddingProvider(
            URI.create(endpoint),
            provider,
            model,
            dimension,
            System.getenv().getOrDefault("AUTOGUIA_EMBEDDING_API_KEY", ""),
            Duration.ofSeconds(3),
            Duration.ofSeconds(30)
        );
        String marker = UUID.randomUUID().toString();
        long authorId = users.create(testAdmin()).id();
        List<PilotArticle> corpus = List.of(
            new PilotArticle(
                "Garantia de fábrica",
                "garantia",
                "A cobertura de garantia de fábrica protege contra defeitos de fabricação no prazo do certificado."
            ),
            new PilotArticle(
                "Revisão preventiva",
                "manutenção",
                "A revisão preventiva inclui troca de óleo e filtro conforme o manual do fabricante."
            ),
            new PilotArticle(
                "Assistência técnica autorizada",
                "assistência",
                "Para conserto de aparelho com defeito, solicite atendimento de assistência técnica autorizada."
            ),
            new PilotArticle(
                "Abertura de chamado",
                "suporte",
                "Para abrir um chamado de suporte, informe o número de série e descreva o problema."
            )
        );
        List<Long> articleIds = new ArrayList<>();
        long documentEmbeddingStarted = System.nanoTime();
        List<EmbeddingVector> documentVectors = new ArrayList<>();
        for (PilotArticle article : corpus) {
            documentVectors.add(embeddings.embed(article.title() + "\n" + article.content()));
        }
        long documentEmbeddingMicros =
            Duration.ofNanos(System.nanoTime() - documentEmbeddingStarted).toNanos() / 1_000;
        List<PilotQuestion> questions = List.of(
            new PilotQuestion("literal", "cobertura garantia fábrica", 0),
            new PilotQuestion(
                "paráfrase",
                "produto apresentou defeito após a compra, qual proteção existe",
                0
            ),
            new PilotQuestion("termo exato", "abrir chamado suporte número série", 3),
            new PilotQuestion("sem cobertura", "segunda via boleto condomínio", -1)
        );
        List<PilotQuestion> validation = List.of(
            new PilotQuestion("val garantia cobertura", "o que cobre a garantia de fabricação?", 0),
            new PilotQuestion("val revisão", "quando trocar óleo e filtro na revisão?", 1),
            new PilotQuestion("val assistência", "onde reparar aparelho autorizado?", 2),
            new PilotQuestion("val chamado", "quais dados preciso para abrir suporte?", 3),
            new PilotQuestion("val preço", "qual o preço do seguro residencial?", -1),
            new PilotQuestion("val exclusão", "garantia cobre dano por queda ou acidente?", -1)
        );
        List<PilotQuestion> allQuestions = new ArrayList<>(questions);
        allQuestions.addAll(validation);
        List<EmbeddingVector> questionVectors = new ArrayList<>();
        long queryEmbeddingStarted = System.nanoTime();
        for (PilotQuestion question : allQuestions) {
            questionVectors.add(embeddings.embed(question.text()));
        }
        long queryEmbeddingMicros =
            Duration.ofNanos(System.nanoTime() - queryEmbeddingStarted).toNanos() / 1_000;
        assertThat(documentVectors).allSatisfy(vector -> assertThat(vector.dimension()).isEqualTo(dimension));
        assertThat(questionVectors).allSatisfy(vector -> assertThat(vector.dimension()).isEqualTo(dimension));

        for (int index = 0; index < corpus.size(); index++) {
            PilotArticle article = corpus.get(index);
            String title = article.title() + " " + marker;
            var draft = articles.create(
                new KnowledgeArticleService.Input(title, article.category(), article.content()),
                authorId
            );
            long articleId = draft.article().id();
            articles.publish(
                articleId,
                draft.versions().get(0).id(),
                List.of(
                    new KnowledgeArticleService.IndexedChunk(0, article.content(), documentVectors.get(index))
                )
            );
            articleIds.add(articleId);
        }

        double minimumSimilarity = Double.parseDouble(
            System.getenv().getOrDefault("AUTOGUIA_RETRIEVAL_MINIMUM_SIMILARITY", "0.25")
        );
        String articlePlaceholders = String.join(",", java.util.Collections.nCopies(articleIds.size(), "?"));
        String similaritySql =
            "select article.id, 1 - (chunk.embedding <=> ?::vector) as similarity " +
            "from knowledge_articles article join knowledge_article_versions version " +
            "on version.id=article.current_version_id join knowledge_article_chunks chunk " +
            "on chunk.version_id=version.id where article.id in (" +
            articlePlaceholders +
            ") " +
            "order by chunk.embedding <=> ?::vector limit 1";
        List<Double> calibrationCosines = new ArrayList<>();
        for (int index = 0; index < allQuestions.size(); index++) {
            String vector = questionVectors.get(index).values().toString();
            List<Object> args = new ArrayList<>();
            args.add(vector);
            args.addAll(articleIds);
            args.add(vector);
            var nearest = jdbc.queryForMap(similaritySql, args.toArray());
            double cosine = ((Number) nearest.get("similarity")).doubleValue();
            if (index < questions.size()) {
                calibrationCosines.add(cosine);
            }
            System.out.printf(
                "RAG real pilot nearest question=%s article=%s cosine=%.6f expectedIndex=%d set=%s%n",
                allQuestions.get(index).name(),
                nearest.get("id"),
                cosine,
                allQuestions.get(index).expectedIndex(),
                index < questions.size() ? "calibration" : "validation"
            );
        }
        double maxCalibrationGap = calibrationCosines.get(3);
        double minCalibrationPositive = Math.min(
            calibrationCosines.get(0),
            Math.min(calibrationCosines.get(1), calibrationCosines.get(2))
        );
        double calibratedThreshold = (maxCalibrationGap + minCalibrationPositive) / 2.0;
        System.out.printf(
            "RAG real pilot calibration threshold baseline=%.6f maxGap=%.6f minPositive=%.6f candidate=%.6f%n",
            minimumSimilarity,
            maxCalibrationGap,
            minCalibrationPositive,
            calibratedThreshold
        );
        System.out.printf(
            "RAG real pilot provider=%s model=%s dimension=%d documentEmbeddingMicros=%d queryEmbeddingMicros=%d%n",
            provider,
            model,
            dimension,
            documentEmbeddingMicros,
            queryEmbeddingMicros
        );
        evaluate(
            "calibration",
            questions,
            questionVectors.subList(0, questions.size()),
            articleIds,
            minimumSimilarity
        );
        var validationVectors = questionVectors.subList(questions.size(), questionVectors.size());
        evaluate("validation", validation, validationVectors, articleIds, minimumSimilarity);
        evaluate("validation", validation, validationVectors, articleIds, calibratedThreshold);
    }

    private void evaluate(
        String dataset,
        List<PilotQuestion> questions,
        List<EmbeddingVector> questionVectors,
        List<Long> articleIds,
        double minimumSimilarity
    ) {
        for (SearchMode mode : SearchMode.values()) {
            int hitAtOne = 0;
            int hitAtFive = 0;
            int correctAbsence = 0;
            int falsePositives = 0;
            int positives = 0;
            List<Long> sqlMicros = new ArrayList<>();
            for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
                PilotQuestion question = questions.get(questionIndex);
                long started = System.nanoTime();
                List<KnowledgeRetrievalRepository.SourceChunk> results = switch (mode) {
                    case TEXT -> retrieval.searchText(question.text(), 5);
                    case VECTOR -> retrieval.searchHybrid(
                        ".",
                        questionVectors.get(questionIndex),
                        5,
                        minimumSimilarity
                    );
                    case HYBRID -> retrieval.searchHybrid(
                        question.text(),
                        questionVectors.get(questionIndex),
                        5,
                        minimumSimilarity
                    );
                };
                assertThat(results).allSatisfy(source -> assertThat(articleIds).contains(source.articleId()));
                sqlMicros.add(Duration.ofNanos(System.nanoTime() - started).toNanos() / 1_000);
                Long expected =
                    question.expectedIndex() < 0 ? null : articleIds.get(question.expectedIndex());
                if (expected == null) {
                    if (results.isEmpty()) {
                        correctAbsence++;
                    } else {
                        falsePositives++;
                    }
                } else {
                    positives++;
                    if (!results.isEmpty() && results.get(0).articleId() == expected) {
                        hitAtOne++;
                    }
                    if (
                        results
                            .stream()
                            .limit(5)
                            .anyMatch(source -> source.articleId() == expected)
                    ) {
                        hitAtFive++;
                    }
                }
                System.out.printf(
                    "RAG real pilot set=%s threshold=%.6f mode=%s question=%s expectedArticle=%s firstArticle=%s resultCount=%d%n",
                    dataset,
                    minimumSimilarity,
                    mode,
                    question.name(),
                    expected,
                    results.isEmpty() ? "none" : results.get(0).articleId(),
                    results.size()
                );
            }
            sqlMicros.sort(Long::compareTo);
            System.out.printf(
                "RAG real pilot summary set=%s threshold=%.6f mode=%s hit@1=%d/%d hit@5=%d/%d " +
                    "correctAbsence=%d/%d falsePositives=%d sqlMicros=%s%n",
                dataset,
                minimumSimilarity,
                mode,
                hitAtOne,
                positives,
                hitAtFive,
                positives,
                correctAbsence,
                questions.size() - positives,
                falsePositives,
                sqlMicros
            );
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing opt-in pilot setting: " + name);
        }
        return value;
    }

    private static UserDtos.Create testAdmin() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String digits = String.format("%09d", ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999));
        for (int size = 9; size <= 10; size++) {
            int sum = 0;
            for (int index = 0; index < size; index++) {
                sum += (digits.charAt(index) - '0') * (size + 1 - index);
            }
            int remainder = sum % 11;
            digits += remainder < 2 ? "0" : Integer.toString(11 - remainder);
        }
        return new UserDtos.Create(
            "RAG real pilot fixture",
            "ragfixture" + suffix,
            "ragfixture" + suffix + "@example.invalid",
            digits,
            "",
            "fixture-password-local",
            "fixture-password-local",
            UserRole.ADMIN,
            ActiveStatus.ACTIVE,
            List.of()
        );
    }

    private record PilotArticle(String title, String category, String content) {}

    private record PilotQuestion(String name, String text, int expectedIndex) {}

    private enum SearchMode {
        TEXT,
        VECTOR,
        HYBRID,
    }
}
