package com.wxn.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Opt-in API flow against a fresh Flyway-backed PostgreSQL database and a local fake embeddings endpoint. */
@EnabledIfEnvironmentVariable(named = "WXN_INTEGRATION_TEST", matches = "true")
@ActiveProfiles({ "dev", "legacy" })
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "wxn.dev.api-key=integration-test-key-minimum-32-characters",
        "openbot.support.enabled=false",
        "wxn.autoguia.embedding.enabled=true",
        "wxn.autoguia.embedding.provider=fixture-provider",
        "wxn.autoguia.embedding.model=fixture-model-v1",
        "wxn.autoguia.embedding.dimension=2",
        "wxn.autoguia.embedding.api-key=fixture-only-key",
        "wxn.autoguia.embedding.connect-timeout=PT2S",
        "wxn.autoguia.embedding.timeout=PT2S",
        "wxn.autoguia.indexing.enabled=true",
        "wxn.autoguia.indexing.delay=PT0.1S",
        "wxn.autoguia.indexing.lease=PT30S",
        "wxn.autoguia.rag.enabled=true",
    }
)
class KnowledgeAdminIntegrationTest {

    private static final String DEV_KEY = "integration-test-key-minimum-32-characters";
    private static final AtomicInteger EMBEDDING_REQUESTS = new AtomicInteger();
    private static final HttpServer EMBEDDING_SERVER = startEmbeddingServer();

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    com.wxn.chatbot.user.service.UserService accountUsers;

    @Autowired
    com.wxn.chatbot.autoguia.service.KnowledgeArticleService articles;

    @Autowired
    com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider embeddings;

    @Autowired
    com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository retrieval;

    @Autowired
    com.wxn.chatbot.autoguia.service.KnowledgeGapService knowledgeGaps;

    private HttpHeaders adminHeaders;

    @DynamicPropertySource
    static void embeddingEndpoint(DynamicPropertyRegistry registry) {
        registry.add(
            "wxn.autoguia.embedding.endpoint",
            () -> "http://127.0.0.1:" + EMBEDDING_SERVER.getAddress().getPort()
        );
    }

    private static HttpServer startEmbeddingServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                exchange.getRequestBody().readAllBytes();
                EMBEDDING_REQUESTS.incrementAndGet();
                byte[] response = "{\"data\":[{\"embedding\":[1.0,0.0]}]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @AfterAll
    static void stopEmbeddingServer() {
        EMBEDDING_SERVER.stop(0);
    }

    @BeforeEach
    void authenticateAdmin() {
        var request = LocalFlowIntegrationTest.account(
            "Admin RAG fixture",
            com.wxn.chatbot.user.entity.UserRole.ADMIN,
            List.of()
        );
        accountUsers.create(request);
        adminHeaders = login(request);
    }

    @Test
    void publishesThroughHttpPreviewsPublishedVersionAndKeepsDraftUnpublished() throws Exception {
        assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history where success = true and version between '17' and '21'",
                String.class
            )
        ).contains("17", "18", "19", "20", "21");
        String requestId = UUID.randomUUID().toString();
        String conversationId = "64b000000000000000000001";
        knowledgeGaps.recordNoSource(requestId, conversationId);
        knowledgeGaps.recordNoSource(requestId, conversationId);
        JsonNode gapPage = exchange(
            "/api/admin/knowledge/gaps?page=0&size=10",
            HttpMethod.GET,
            null,
            adminHeaders,
            HttpStatus.OK
        );
        assertThat(gapPage.path("total").asLong()).isEqualTo(1);
        JsonNode gap = gapPage.path("items").get(0);
        assertThat(gap.path("requestId").asText()).isEqualTo(requestId);
        assertThat(gap.path("conversationId").asText()).isEqualTo(conversationId);
        assertThat(gap.has("question")).isFalse();
        assertThat(gap.has("message")).isFalse();
        JsonNode convertedGap = exchange(
            "/api/admin/knowledge/gaps/" + gap.path("id").asLong() + "/draft",
            HttpMethod.POST,
            Map.of("title", "Sugestão da lacuna", "category", "assistência", "content", "Rascunho fictício"),
            adminHeaders,
            HttpStatus.OK
        );
        long gapDraftId = convertedGap.path("draft").path("article").path("id").asLong();
        assertThat(convertedGap.path("created").asBoolean()).isTrue();
        assertThat(convertedGap.path("draft").path("article").path("status").asText()).isEqualTo("DRAFT");
        JsonNode repeatedConversion = exchange(
            "/api/admin/knowledge/gaps/" + gap.path("id").asLong() + "/draft",
            HttpMethod.POST,
            Map.of("title", "Sugestão repetida", "category", "assistência", "content", "Ignorado"),
            adminHeaders,
            HttpStatus.OK
        );
        assertThat(repeatedConversion.path("created").asBoolean()).isFalse();
        assertThat(repeatedConversion.path("draft").path("article").path("id").asLong()).isEqualTo(
            gapDraftId
        );
        int beforeEmbeddings = EMBEDDING_REQUESTS.get();
        String uniqueTerm = "rag" + UUID.randomUUID().toString().replace("-", "");

        JsonNode created = exchange(
            "/api/admin/knowledge/articles",
            HttpMethod.POST,
            Map.of(
                "title",
                "Revisão " + uniqueTerm,
                "category",
                "manutenção",
                "content",
                "Procedimento " + uniqueTerm + ": troque o filtro de ar a cada revisão."
            ),
            adminHeaders,
            HttpStatus.CREATED
        );
        long articleId = created.path("article").path("id").asLong();
        JsonNode firstVersion = created.path("versions").get(0);
        long firstVersionId = firstVersion.path("id").asLong();

        JsonNode queued = exchange(
            publishPath(articleId, firstVersionId),
            HttpMethod.POST,
            Map.of(),
            adminHeaders,
            HttpStatus.ACCEPTED
        );
        assertThat(queued.path("status").asText()).isIn("PENDING", "PROCESSING", "COMPLETED");
        JsonNode status = awaitCompleted(articleId, firstVersionId);
        assertThat(status.path("attempts").asInt()).isPositive();
        assertThat(EMBEDDING_REQUESTS.get()).isGreaterThan(beforeEmbeddings);
        String question = "Procedimento " + uniqueTerm + " filtro de ar";
        var queryEmbedding = embeddings.embed(question);
        assertThat(retrieval.searchHybrid(question, queryEmbedding, 5, 0.25)).isNotEmpty();

        JsonNode preview = exchange(
            "/api/admin/knowledge/preview",
            HttpMethod.POST,
            Map.of("question", question, "limit", 5),
            adminHeaders,
            HttpStatus.OK
        );
        assertThat(preview.path("mode").asText()).isEqualTo("HYBRID");
        JsonNode publishedSource = sourceForArticle(preview, articleId);
        assertThat(publishedSource).isNotNull();
        assertThat(publishedSource.path("versionId").asLong()).isEqualTo(firstVersionId);
        assertThat(publishedSource.path("versionNumber").asInt()).isEqualTo(1);
        assertThat(publishedSource.path("content").asText()).contains("filtro de ar");

        JsonNode revision = exchange(
            "/api/admin/knowledge/articles/" + articleId + "/revisions",
            HttpMethod.POST,
            Map.of(
                "title",
                "Revisão do óleo",
                "category",
                "manutenção",
                "content",
                "Troque o óleo a cada revisão."
            ),
            adminHeaders,
            HttpStatus.CREATED
        );
        long draftVersionId = revision.path("versions").get(0).path("id").asLong();
        var mixedBatch = List.of(
            new com.wxn.chatbot.autoguia.service.KnowledgeArticleService.IndexedChunk(
                0,
                "Trecho A",
                new com.wxn.chatbot.autoguia.domain.EmbeddingVector(
                    "fixture-provider",
                    "fixture-model-v1",
                    2,
                    List.of(1.0, 0.0)
                )
            ),
            new com.wxn.chatbot.autoguia.service.KnowledgeArticleService.IndexedChunk(
                1,
                "Trecho B",
                new com.wxn.chatbot.autoguia.domain.EmbeddingVector(
                    "other-provider",
                    "fixture-model-v1",
                    2,
                    List.of(0.0, 1.0)
                )
            )
        );
        org.junit.jupiter.api.Assertions.assertThrows(com.wxn.chatbot.common.ApiException.class, () ->
            articles.publish(articleId, draftVersionId, mixedBatch)
        );
        assertThat(articles.get(articleId).article().currentVersionId()).isEqualTo(firstVersionId);

        JsonNode stillPublished = exchange(
            "/api/admin/knowledge/preview",
            HttpMethod.POST,
            Map.of("question", question, "limit", 5),
            adminHeaders,
            HttpStatus.OK
        );
        JsonNode stillPublishedSource = sourceForArticle(stillPublished, articleId);
        assertThat(stillPublishedSource).isNotNull();
        assertThat(stillPublishedSource.path("versionId").asLong()).isEqualTo(firstVersionId);
        assertThat(stillPublishedSource.path("versionNumber").asInt()).isEqualTo(1);

        JsonNode archived = exchange(
            "/api/admin/knowledge/articles/" + articleId + "/archive",
            HttpMethod.POST,
            Map.of(),
            adminHeaders,
            HttpStatus.OK
        );
        assertThat(archived.path("article").path("status").asText()).isEqualTo("ARCHIVED");
        var validDelayedChunks = List.of(
            new com.wxn.chatbot.autoguia.service.KnowledgeArticleService.IndexedChunk(
                0,
                "Trecho atrasado",
                new com.wxn.chatbot.autoguia.domain.EmbeddingVector(
                    "fixture-provider",
                    "fixture-model-v1",
                    2,
                    List.of(1.0, 0.0)
                )
            )
        );
        org.junit.jupiter.api.Assertions.assertThrows(com.wxn.chatbot.common.ApiException.class, () ->
            articles.publish(articleId, draftVersionId, validDelayedChunks)
        );
        assertThat(articles.get(articleId).article().currentVersionId()).isEqualTo(firstVersionId);
        JsonNode archivedPreview = exchange(
            "/api/admin/knowledge/preview",
            HttpMethod.POST,
            Map.of("question", question, "limit", 5),
            adminHeaders,
            HttpStatus.OK
        );
        assertThat(sourceForArticle(archivedPreview, articleId)).isNull();

        long departmentId = jdbc.queryForObject(
            "insert into departments(name, color) values (?, ?) returning id",
            Long.class,
            "RAG fixture " + UUID.randomUUID(),
            "#2563EB"
        );
        var attendant = LocalFlowIntegrationTest.account(
            "Attendant RAG fixture",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(departmentId)
        );
        accountUsers.create(attendant);
        HttpHeaders attendantHeaders = login(attendant);
        assertThat(
            exchangeRaw(
                "/api/admin/knowledge/preview",
                HttpMethod.POST,
                Map.of("question", "Como trocar o filtro de ar?", "limit", 5),
                attendantHeaders
            ).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            exchangeRaw(
                publishPath(articleId, draftVersionId),
                HttpMethod.POST,
                Map.of(),
                attendantHeaders
            ).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            exchangeRaw(
                "/api/admin/knowledge/articles/" + articleId + "/archive",
                HttpMethod.POST,
                Map.of(),
                attendantHeaders
            ).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            exchangeRaw("/api/admin/knowledge/gaps", HttpMethod.GET, null, attendantHeaders).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            exchangeRaw(
                "/api/admin/knowledge/gaps/" + gap.path("id").asLong() + "/draft",
                HttpMethod.POST,
                Map.of("title", "Negado", "category", "x", "content", "x"),
                attendantHeaders
            ).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evaluatesFixedDeterministicRetrievalCorpusAcrossTextVectorAndHybridModes() {
        long authorId = jdbc.queryForObject("select id from users order by id desc limit 1", Long.class);
        String marker = "evaluation" + UUID.randomUUID().toString().replace("-", "");
        List<EvaluationArticle> corpus = List.of(
            new EvaluationArticle(
                "Garantia de fábrica",
                "garantia",
                "A cobertura de garantia de fábrica protege contra defeitos de fabricação no prazo do certificado.",
                vector(1.0, 0.0, 0.0, 0.0)
            ),
            new EvaluationArticle(
                "Revisão preventiva",
                "manutenção",
                "A revisão preventiva inclui troca de óleo e filtro conforme o manual do fabricante.",
                vector(0.0, 1.0, 0.0, 0.0)
            ),
            new EvaluationArticle(
                "Assistência técnica autorizada",
                "assistência",
                "Para conserto de aparelho com defeito, solicite atendimento de assistência técnica autorizada.",
                vector(0.0, 0.0, 1.0, 0.0)
            ),
            new EvaluationArticle(
                "Abertura de chamado",
                "suporte",
                "Para abrir um chamado de suporte, informe o número de série e descreva o problema.",
                vector(0.0, 0.0, 0.0, 1.0)
            )
        );
        List<Long> articleIds = new java.util.ArrayList<>();
        for (EvaluationArticle entry : corpus) {
            var draft = articles.create(
                new com.wxn.chatbot.autoguia.service.KnowledgeArticleService.Input(
                    entry.title() + " " + marker,
                    entry.category(),
                    entry.content()
                ),
                authorId
            );
            long articleId = draft.article().id();
            long versionId = draft.versions().get(0).id();
            articles.publish(
                articleId,
                versionId,
                List.of(
                    new com.wxn.chatbot.autoguia.service.KnowledgeArticleService.IndexedChunk(
                        0,
                        entry.content(),
                        entry.embedding()
                    )
                )
            );
            articleIds.add(articleId);
        }

        List<EvaluationQuestion> questions = List.of(
            new EvaluationQuestion(
                "literal",
                "cobertura garantia fábrica",
                vector(1.0, 0.0, 0.0, 0.0),
                articleIds.get(0)
            ),
            new EvaluationQuestion(
                "paráfrase",
                "produto apresentou defeito após a compra, qual proteção existe",
                vector(1.0, 0.0, 0.0, 0.0),
                articleIds.get(0)
            ),
            new EvaluationQuestion(
                "termo exato",
                "abrir chamado suporte número série",
                vector(0.0, 0.0, 0.0, 1.0),
                articleIds.get(3)
            ),
            new EvaluationQuestion(
                "sem cobertura",
                "segunda via boleto condomínio",
                vector(0.5, 0.5, 0.5, 0.5),
                null
            )
        );

        EvaluationMetrics text = evaluate(questions, Mode.TEXT);
        EvaluationMetrics vector = evaluate(questions, Mode.VECTOR);
        EvaluationMetrics hybrid = evaluate(questions, Mode.HYBRID);

        assertThat(vector.hitAtOne()).isEqualTo(3);
        assertThat(vector.hitAtFive()).isEqualTo(3);
        assertThat(vector.correctAbsence()).isEqualTo(1);
        assertThat(hybrid.hitAtOne()).isEqualTo(3);
        assertThat(hybrid.hitAtFive()).isEqualTo(3);
        assertThat(hybrid.correctAbsence()).isEqualTo(1);
        assertThat(text.correctAbsence()).isEqualTo(1);
        System.out.printf(
            "RAG functional evaluation (deterministic fixture, k=1,5): text=%s vector=%s hybrid=%s%n",
            text,
            vector,
            hybrid
        );
    }

    private EvaluationMetrics evaluate(List<EvaluationQuestion> questions, Mode mode) {
        List<Long> durations = new ArrayList<>();
        int hitAtOne = 0;
        int hitAtFive = 0;
        int correctAbsence = 0;
        for (EvaluationQuestion question : questions) {
            Supplier<
                List<com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository.SourceChunk>
            > search = () ->
                switch (mode) {
                    case TEXT -> retrieval.searchText(question.text(), 5);
                    case VECTOR -> retrieval.searchHybrid(
                        "opaque vector fixture " + question.name(),
                        question.embedding(),
                        5,
                        0.9
                    );
                    case HYBRID -> retrieval.searchHybrid(question.text(), question.embedding(), 5, 0.9);
                };
            List<com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository.SourceChunk> results =
                List.of();
            for (int repeat = 0; repeat < 20; repeat++) {
                long started = System.nanoTime();
                results = search.get();
                durations.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started));
            }
            if (question.expectedArticleId() == null) {
                if (results.isEmpty()) {
                    correctAbsence++;
                }
            } else if (!results.isEmpty() && results.get(0).articleId() == question.expectedArticleId()) {
                hitAtOne++;
            }
            if (
                question.expectedArticleId() != null &&
                results
                    .stream()
                    .limit(5)
                    .anyMatch(result -> result.articleId() == question.expectedArticleId())
            ) {
                hitAtFive++;
            }
        }
        Collections.sort(durations);
        long p50 = durations.get(durations.size() / 2);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        return new EvaluationMetrics(hitAtOne, hitAtFive, correctAbsence, durations.size(), p50, p95);
    }

    private static com.wxn.chatbot.autoguia.domain.EmbeddingVector vector(
        double first,
        double second,
        double third,
        double fourth
    ) {
        return new com.wxn.chatbot.autoguia.domain.EmbeddingVector(
            "evaluation-fixture",
            "deterministic-v1",
            4,
            List.of(first, second, third, fourth)
        );
    }

    private record EvaluationArticle(
        String title,
        String category,
        String content,
        com.wxn.chatbot.autoguia.domain.EmbeddingVector embedding
    ) {}

    private record EvaluationQuestion(
        String name,
        String text,
        com.wxn.chatbot.autoguia.domain.EmbeddingVector embedding,
        Long expectedArticleId
    ) {}

    private record EvaluationMetrics(
        int hitAtOne,
        int hitAtFive,
        int correctAbsence,
        int measuredQueries,
        long p50Micros,
        long p95Micros
    ) {}

    private enum Mode {
        TEXT,
        VECTOR,
        HYBRID,
    }

    private JsonNode awaitCompleted(long articleId, long versionId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        JsonNode status;
        do {
            status = exchange(
                publishPath(articleId, versionId),
                HttpMethod.GET,
                null,
                adminHeaders,
                HttpStatus.OK
            );
            if ("COMPLETED".equals(status.path("status").asText())) {
                return status;
            }
            if ("FAILED".equals(status.path("status").asText())) {
                throw new AssertionError("Indexing job failed: " + status.path("error").asText());
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Indexing job did not complete in the isolated fixture");
    }

    private HttpHeaders login(com.wxn.chatbot.user.dto.UserDtos.Create user) {
        var headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", DEV_KEY);
        var session = http.exchange(
            "/api/auth/session",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        headers.set(
            HttpHeaders.COOKIE,
            session.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]
        );
        headers.set("X-CSRF-Token", session.getBody().path("csrfToken").asText());
        var result = http.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", user.username(), "password", user.password()), headers),
            JsonNode.class
        );
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.set(
            HttpHeaders.COOKIE,
            result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]
        );
        headers.set("X-CSRF-Token", result.getBody().path("csrfToken").asText());
        return headers;
    }

    private JsonNode exchange(
        String path,
        HttpMethod method,
        Object body,
        HttpHeaders headers,
        HttpStatus expected
    ) {
        var response = exchangeRaw(path, method, body, headers);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        return response.getBody();
    }

    private org.springframework.http.ResponseEntity<JsonNode> exchangeRaw(
        String path,
        HttpMethod method,
        Object body,
        HttpHeaders headers
    ) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static String publishPath(long articleId, long versionId) {
        return "/api/admin/knowledge/articles/" + articleId + "/versions/" + versionId + "/publish";
    }

    private static JsonNode sourceForArticle(JsonNode preview, long articleId) {
        for (JsonNode source : preview.path("sources")) {
            if (source.path("articleId").asLong() == articleId) {
                return source;
            }
        }
        return null;
    }
}
