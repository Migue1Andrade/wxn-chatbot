package com.wxn.chatbot.openbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.generation.KnowledgeAnswerGenerator;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository.SourceChunk;
import com.wxn.chatbot.autoguia.service.KnowledgePreviewService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class OpenBotSupportRagTest {

    @Test
    void ragWithTextSourcePassesSourceMetadataAndContext() {
        var retrieval = Mockito.mock(KnowledgeRetrievalRepository.class);
        when(retrieval.searchText("como revisar", 10)).thenReturn(
            List.of(new SourceChunk(11, 24, 2, 0, "Revisões", "Siga o manual de revisão.", 0.02))
        );
        var service = support(new KnowledgePreviewService(retrieval, null, false, false, 0.25));

        var result = service.retrievalContext("como revisar");

        assertTrue(result.rag());
        assertEquals("TEXT", result.mode());
        assertTrue(result.handoff());
        assertEquals(List.of("Fonte: Revisões\nSiga o manual de revisão."), result.context());
        assertEquals(11L, result.sources().getFirst().get("articleId"));
        assertEquals(24L, result.sources().getFirst().get("versionId"));
        assertEquals(2, result.sources().getFirst().get("versionNumber"));
    }

    @Test
    void ragWithoutSourcesRequiresHuman() {
        var retrieval = Mockito.mock(KnowledgeRetrievalRepository.class);
        when(retrieval.searchText("pergunta desconhecida", 10)).thenReturn(List.of());
        var service = support(new KnowledgePreviewService(retrieval, null, false, false, 0.25));

        var result = service.retrievalContext("pergunta desconhecida");

        assertTrue(result.rag());
        assertTrue(result.handoff());
        assertTrue(result.uncovered());
        assertTrue(result.context().isEmpty());
        assertTrue(result.fallback().isEmpty());
    }

    @Test
    void retrievalFailureRequiresHumanWithoutExposingErrorText() {
        var retrieval = Mockito.mock(KnowledgeRetrievalRepository.class);
        when(retrieval.searchText("pergunta", 10)).thenThrow(new IllegalStateException("private db detail"));
        var service = support(new KnowledgePreviewService(retrieval, null, false, false, 0.25));

        var result = service.retrievalContext("pergunta");

        assertTrue(result.handoff());
        assertTrue(!result.uncovered());
        assertEquals("UNAVAILABLE", result.mode());
        assertTrue(result.context().isEmpty());
        assertTrue(result.fallback().isEmpty());
    }

    @Test
    void explicitHumanRequestIsNotKnowledgeGap() {
        var retrieval = Mockito.mock(KnowledgeRetrievalRepository.class);
        var service = support(new KnowledgePreviewService(retrieval, null, false, false, 0.25));

        var explicit = service.retrievalContext("quero falar com atendente");

        assertTrue(!explicit.uncovered());
    }

    @Test
    @SuppressWarnings("unchecked")
    void embeddingDegradationIsNotRecordedAsKnowledgeGap() {
        var retrieval = Mockito.mock(KnowledgeRetrievalRepository.class);
        when(retrieval.searchText("pergunta", 10)).thenReturn(List.of());
        var provider = Mockito.mock(KnowledgeEmbeddingProvider.class);
        when(provider.embed("pergunta")).thenThrow(new IllegalStateException("provider detail"));
        var providerSource = Mockito.mock(ObjectProvider.class);
        when(providerSource.getIfAvailable()).thenReturn(provider);
        var preview = new KnowledgePreviewService(retrieval, providerSource, true, true, 0.25);

        var result = support(preview).retrievalContext("pergunta");

        assertEquals("TEXT_FALLBACK", result.mode());
        assertTrue(result.handoff());
        assertTrue(!result.uncovered());
    }

    @Test
    @SuppressWarnings("unchecked")
    void relatedSourcesRequireAnExactAnswerExcerpt() {
        String integration = "A WXN integra suas soluções ao ERP da empresa.";
        var sources = List.of(
            new SourceChunk(1, 2, 1, 0, "Migração", "A WXN realiza migração de plataformas.", 0.03),
            new SourceChunk(3, 4, 1, 0, "Integração", integration, 0.02)
        );
        String integrationQuestion = "Dá para ligar meu software de gestão a outro sistema?";
        String priceQuestion = "Qual o preço mensal de hospedagem?";
        String tiresQuestion = "Vocês vendem pneus automotivos?";
        String inventedQuestion = "A integração custa quanto?";
        var repository = Mockito.mock(KnowledgeRetrievalRepository.class);
        var generator = Mockito.mock(KnowledgeAnswerGenerator.class);
        ObjectProvider<KnowledgeAnswerGenerator> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(generator);
        for (String question : List.of(integrationQuestion, priceQuestion, tiresQuestion, inventedQuestion)) {
            when(repository.searchText(question, 10)).thenReturn(sources);
        }
        when(generator.selectEvidence(integrationQuestion, sources)).thenReturn(integration);
        when(generator.selectEvidence(priceQuestion, sources)).thenReturn("SEM_COBERTURA");
        when(generator.selectEvidence(tiresQuestion, sources)).thenReturn("SEM_COBERTURA");
        when(generator.selectEvidence(inventedQuestion, sources)).thenReturn("A integração custa R$ 100.");
        var service = support(
            new KnowledgePreviewService(repository, null, provider, false, false, true, 0.25)
        );

        assertEquals(integration, service.retrievalContext(integrationQuestion).fallback());
        assertTrue(!service.retrievalContext(integrationQuestion).handoff());
        for (String question : List.of(priceQuestion, tiresQuestion, inventedQuestion)) {
            assertTrue(service.retrievalContext(question).handoff());
        }
    }

    private static OpenBotSupportService support(KnowledgePreviewService preview) {
        return new OpenBotSupportService(null, null, null, null, null, null, null, preview, true);
    }
}
