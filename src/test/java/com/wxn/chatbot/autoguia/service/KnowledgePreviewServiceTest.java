package com.wxn.chatbot.autoguia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wxn.chatbot.autoguia.domain.EmbeddingVector;
import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.generation.KnowledgeAnswerGenerator;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class KnowledgePreviewServiceTest {

    @Mock
    private KnowledgeRetrievalRepository retrieval;

    @Mock
    private ObjectProvider<KnowledgeEmbeddingProvider> providerProvider;

    @Mock
    private ObjectProvider<KnowledgeAnswerGenerator> answerGeneratorProvider;

    @Test
    void usesTextWhenEmbeddingFlagsAreOff() {
        when(retrieval.searchText("warranty", 5)).thenReturn(List.of());
        var service = new KnowledgePreviewService(retrieval, providerProvider, false, false, 0.35);

        var result = service.preview("warranty", null);

        assertEquals("TEXT", result.mode());
        assertEquals("NOT_REQUESTED", result.generationStatus());
        verify(retrieval).searchText("warranty", 5);
    }

    @Test
    void fallsBackToTextWhenEmbeddingProviderFails() {
        when(providerProvider.getIfAvailable()).thenReturn(text -> {
            throw new IllegalStateException("provider unavailable");
        });
        when(retrieval.searchText("warranty", 3)).thenReturn(List.of());
        var service = new KnowledgePreviewService(retrieval, providerProvider, true, true, 0.35);

        var result = service.preview("warranty", 3);

        assertEquals("TEXT_FALLBACK", result.mode());
        verify(retrieval).searchText("warranty", 3);
    }

    @Test
    void sendsMinimumSimilarityToHybridSearch() {
        when(providerProvider.getIfAvailable()).thenReturn(text ->
            new EmbeddingVector("test-provider", "test-model", 2, List.of(1.0, 0.5))
        );
        when(
            retrieval.searchHybrid(
                "warranty",
                new EmbeddingVector("test-provider", "test-model", 2, List.of(1.0, 0.5)),
                4,
                0.4
            )
        ).thenReturn(List.of());
        var service = new KnowledgePreviewService(retrieval, providerProvider, true, true, 0.4);

        var result = service.preview("warranty", 4);

        assertEquals("HYBRID", result.mode());
        verify(retrieval).searchHybrid(
            "warranty",
            new EmbeddingVector("test-provider", "test-model", 2, List.of(1.0, 0.5)),
            4,
            0.4
        );
    }

    @Test
    void generatedAnswerIsOptInAndKeepsRetrievedSources() {
        var source = new KnowledgeRetrievalRepository.SourceChunk(
            10,
            20,
            1,
            0,
            "Garantia",
            "Cobertura fictícia da garantia.",
            0.1
        );
        when(retrieval.searchText("Como funciona a garantia?", 5)).thenReturn(List.of(source));
        when(answerGeneratorProvider.getIfAvailable()).thenReturn((question, sources) -> "Resposta de teste");
        var service = new KnowledgePreviewService(
            retrieval,
            providerProvider,
            answerGeneratorProvider,
            false,
            false,
            true,
            0.35
        );

        var ordinaryPreview = service.preview("Como funciona a garantia?", null);
        var generatedPreview = service.preview("Como funciona a garantia?", null, true);

        assertEquals("NOT_REQUESTED", ordinaryPreview.generationStatus());
        assertEquals(null, ordinaryPreview.generatedAnswer());
        assertEquals("GENERATED", generatedPreview.generationStatus());
        assertEquals("Resposta de teste", generatedPreview.generatedAnswer());
        assertEquals(List.of(source), generatedPreview.sources());
        verify(answerGeneratorProvider).getIfAvailable();
    }

    @Test
    void doesNotCallGeneratorWithoutSources() {
        when(retrieval.searchText("sem cobertura", 5)).thenReturn(List.of());
        var service = new KnowledgePreviewService(
            retrieval,
            providerProvider,
            answerGeneratorProvider,
            false,
            false,
            true,
            0.35
        );

        var result = service.preview("sem cobertura", null, true);

        assertEquals("NO_SOURCES", result.generationStatus());
        assertEquals(null, result.generatedAnswer());
        verify(answerGeneratorProvider, never()).getIfAvailable();
    }

    @Test
    void reportsUnavailableGeneratorWithoutReplacingSourcesWithFallbackText() {
        var source = new KnowledgeRetrievalRepository.SourceChunk(
            10,
            20,
            1,
            0,
            "Garantia",
            "Trecho de apoio.",
            0.1
        );
        when(retrieval.searchText("garantia", 5)).thenReturn(List.of(source));
        when(answerGeneratorProvider.getIfAvailable()).thenReturn((question, sources) -> {
            throw new IllegalStateException("fixture failure");
        });
        var service = new KnowledgePreviewService(
            retrieval,
            providerProvider,
            answerGeneratorProvider,
            false,
            false,
            true,
            0.35
        );

        var result = service.preview("garantia", null, true);

        assertEquals("UNAVAILABLE", result.generationStatus());
        assertEquals(null, result.generatedAnswer());
        assertEquals(List.of(source), result.sources());
    }

    @Test
    void reportsNotConfiguredWhenGenerationIsExplicitButNoGeneratorExists() {
        var source = new KnowledgeRetrievalRepository.SourceChunk(
            10,
            20,
            1,
            0,
            "Garantia",
            "Trecho de apoio.",
            0.1
        );
        when(retrieval.searchText("garantia", 5)).thenReturn(List.of(source));
        when(answerGeneratorProvider.getIfAvailable()).thenReturn(null);
        var service = new KnowledgePreviewService(
            retrieval,
            providerProvider,
            answerGeneratorProvider,
            false,
            false,
            true,
            0.35
        );

        var result = service.preview("garantia", null, true);

        assertEquals("NOT_CONFIGURED", result.generationStatus());
        assertEquals(null, result.generatedAnswer());
    }

    @Test
    void generationFlagOffPreventsCallingAnAvailableGenerator() {
        var source = new KnowledgeRetrievalRepository.SourceChunk(
            10,
            20,
            1,
            0,
            "Garantia",
            "Trecho de apoio.",
            0.1
        );
        when(retrieval.searchText("garantia", 5)).thenReturn(List.of(source));
        var service = new KnowledgePreviewService(
            retrieval,
            providerProvider,
            answerGeneratorProvider,
            false,
            false,
            false,
            0.35
        );

        var result = service.preview("garantia", null, true);

        assertEquals("NOT_CONFIGURED", result.generationStatus());
        assertEquals(null, result.generatedAnswer());
        verify(answerGeneratorProvider, never()).getIfAvailable();
    }
}
