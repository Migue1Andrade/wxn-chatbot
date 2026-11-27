package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.repository.KnowledgeGapRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeGapRepository.Gap;
import com.wxn.chatbot.common.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGapService {

    public record GapPage(List<Gap> items, int page, int size, long total) {}

    public record DraftConversion(Gap gap, KnowledgeArticleService.Detail draft, boolean created) {}

    private final KnowledgeGapRepository repository;
    private final KnowledgeArticleService articles;

    public KnowledgeGapService(KnowledgeGapRepository repository, KnowledgeArticleService articles) {
        this.repository = repository;
        this.articles = articles;
    }

    @Transactional
    public void recordNoSource(String requestId, String conversationId) {
        if (
            requestId == null ||
            requestId.isBlank() ||
            requestId.length() > 120 ||
            conversationId == null ||
            conversationId.isBlank() ||
            conversationId.length() > 80
        ) {
            throw new IllegalArgumentException("Invalid knowledge-gap identifiers");
        }
        repository.insertIfAbsent(requestId, conversationId);
    }

    @Transactional(readOnly = true)
    public GapPage page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("page deve ser >= 0 e size entre 1 e 100");
        }
        long offset = (long) page * size;
        return new GapPage(repository.page(size, offset), page, size, repository.count());
    }

    @Transactional
    public DraftConversion convertToDraft(
        long gapId,
        KnowledgeArticleService.Input suggestion,
        long authorId
    ) {
        Gap gap = repository.findForUpdate(gapId);
        if (gap == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lacuna não encontrada");
        }
        if (gap.draftArticleId() != null) {
            return new DraftConversion(gap, articles.get(gap.draftArticleId()), false);
        }
        if (!"OPEN".equals(gap.status())) {
            throw ApiException.conflict("Lacuna não pode mais ser convertida em rascunho");
        }
        KnowledgeArticleService.Detail draft = articles.create(suggestion, authorId);
        repository.attachDraft(gapId, draft.article().id());
        Gap converted = repository.findForUpdate(gapId);
        return new DraftConversion(converted, draft, true);
    }
}
