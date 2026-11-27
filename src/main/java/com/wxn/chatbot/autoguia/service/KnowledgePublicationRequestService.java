package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository.JobStatus;
import com.wxn.chatbot.common.ApiException;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgePublicationRequestService {

    private static final int MAX_ATTEMPTS = 3;

    private final KnowledgeArticleService articles;
    private final KnowledgeIndexJobRepository jobs;

    public KnowledgePublicationRequestService(
        KnowledgeArticleService articles,
        KnowledgeIndexJobRepository jobs
    ) {
        this.articles = articles;
        this.jobs = jobs;
    }

    @Transactional
    public JobStatus request(long articleId, long versionId) {
        var detail = articles.get(articleId);
        if ("ARCHIVED".equals(detail.article().status())) {
            throw ApiException.conflict("Artigo arquivado não pode ser publicado");
        }
        var latest = detail
            .versions()
            .stream()
            .max(Comparator.comparingInt(version -> version.number()))
            .orElse(null);
        if (latest == null || latest.id() != versionId || !"DRAFT".equals(latest.status())) {
            throw ApiException.conflict("Somente a revisão mais recente pode ser enviada para publicação");
        }
        JobStatus status = jobs.enqueue(articleId, versionId, MAX_ATTEMPTS);
        if (status == null) {
            throw ApiException.conflict("A revisão mudou antes de iniciar a indexação");
        }
        return status;
    }

    @Transactional(readOnly = true)
    public JobStatus status(long articleId, long versionId) {
        articles.get(articleId);
        JobStatus status = jobs.findStatus(articleId, versionId);
        if (status == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Trabalho de indexação não encontrado");
        }
        return status;
    }
}
