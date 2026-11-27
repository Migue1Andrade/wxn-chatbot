package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.generation.KnowledgeAnswerGenerator;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository.SourceChunk;
import com.wxn.chatbot.common.ApiException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class KnowledgePreviewService {

    public record Preview(
        String mode,
        List<SourceChunk> sources,
        String generationStatus,
        String generatedAnswer
    ) {}

    private static final int MAX_PREVIEW_RESULTS = 10;
    private final KnowledgeRetrievalRepository retrieval;
    private final ObjectProvider<KnowledgeEmbeddingProvider> embeddingProvider;
    private final ObjectProvider<KnowledgeAnswerGenerator> answerGenerator;
    private final boolean embeddingEnabled;
    private final boolean indexingEnabled;
    private final boolean generationEnabled;
    private final double minSimilarity;

    @Autowired
    public KnowledgePreviewService(
        KnowledgeRetrievalRepository retrieval,
        ObjectProvider<KnowledgeEmbeddingProvider> embeddingProvider,
        ObjectProvider<KnowledgeAnswerGenerator> answerGenerator,
        @Value("${wxn.autoguia.embedding.enabled:false}") boolean embeddingEnabled,
        @Value("${wxn.autoguia.indexing.enabled:false}") boolean indexingEnabled,
        @Value("${wxn.autoguia.generation.enabled:false}") boolean generationEnabled,
        @Value("${wxn.autoguia.retrieval.minimum-similarity:0.25}") double minSimilarity
    ) {
        if (!Double.isFinite(minSimilarity) || minSimilarity < 0.0 || minSimilarity > 1.0) {
            throw new IllegalArgumentException("Minimum vector similarity must be between zero and one");
        }
        this.retrieval = retrieval;
        this.embeddingProvider = embeddingProvider;
        this.answerGenerator = answerGenerator;
        this.embeddingEnabled = embeddingEnabled;
        this.indexingEnabled = indexingEnabled;
        this.generationEnabled = generationEnabled;
        this.minSimilarity = minSimilarity;
    }

    public KnowledgePreviewService(
        KnowledgeRetrievalRepository retrieval,
        ObjectProvider<KnowledgeEmbeddingProvider> embeddingProvider,
        boolean embeddingEnabled,
        boolean indexingEnabled,
        double minSimilarity
    ) {
        this(retrieval, embeddingProvider, null, embeddingEnabled, indexingEnabled, false, minSimilarity);
    }

    public String approvedEvidence(String question, List<SourceChunk> sources) {
        if (!generationEnabled || answerGenerator == null || sources.isEmpty()) {
            return "";
        }
        var generator = answerGenerator.getIfAvailable();
        if (generator == null) {
            return "";
        }
        String evidence = generator.selectEvidence(question, sources).strip();
        if (evidence.equals("SEM_COBERTURA") || !evidence.matches("(?s).+[.!?]")) {
            return "";
        }
        return sources
            .stream()
            .anyMatch(source ->
                (" " + source.content().strip().replaceAll("\\s+", " ") + " ").matches(
                    "(?s).*(?:^|[.!?]) " +
                        java.util.regex.Pattern.quote(evidence.replaceAll("\\s+", " ")) +
                        " .*"
                )
            )
            ? evidence
            : "";
    }

    public Preview preview(String question, Integer requestedLimit) {
        return preview(question, requestedLimit, false);
    }

    public Preview preview(String question, Integer requestedLimit, boolean generateAnswer) {
        if (question == null || question.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe uma pergunta para a prévia");
        }
        int limit = requestedLimit == null ? 5 : requestedLimit;
        if (limit <= 0 || limit > MAX_PREVIEW_RESULTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O limite da prévia deve estar entre um e dez");
        }
        List<SourceChunk> sources;
        String mode;
        if (!embeddingEnabled || !indexingEnabled) {
            mode = "TEXT";
            sources = retrieval.searchText(question, limit);
        } else {
            KnowledgeEmbeddingProvider provider = embeddingProvider.getIfAvailable();
            if (provider == null) {
                mode = "TEXT";
                sources = retrieval.searchText(question, limit);
            } else {
                try {
                    var vector = provider.embed(question);
                    mode = "HYBRID";
                    sources = retrieval.searchHybrid(question, vector, limit, minSimilarity);
                } catch (RuntimeException unavailable) {
                    mode = "TEXT_FALLBACK";
                    sources = retrieval.searchText(question, limit);
                }
            }
        }
        if (!generateAnswer) {
            return new Preview(mode, sources, "NOT_REQUESTED", null);
        }
        if (sources.isEmpty()) {
            return new Preview(mode, sources, "NO_SOURCES", null);
        }
        if (!generationEnabled) {
            return new Preview(mode, sources, "NOT_CONFIGURED", null);
        }
        KnowledgeAnswerGenerator generator =
            answerGenerator == null ? null : answerGenerator.getIfAvailable();
        if (generator == null) {
            return new Preview(mode, sources, "NOT_CONFIGURED", null);
        }
        try {
            return new Preview(mode, sources, "GENERATED", generator.generate(question, sources));
        } catch (RuntimeException unavailable) {
            return new Preview(mode, sources, "UNAVAILABLE", null);
        }
    }
}
