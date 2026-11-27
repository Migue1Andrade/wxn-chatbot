package com.wxn.chatbot.autoguia.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.autoguia.embedding.KnowledgeEmbeddingProvider;
import com.wxn.chatbot.autoguia.repository.KnowledgeArticleRepository.Article;
import com.wxn.chatbot.autoguia.repository.KnowledgeIndexJobRepository.JobStatus;
import com.wxn.chatbot.autoguia.service.KnowledgeArticleService;
import com.wxn.chatbot.autoguia.service.KnowledgeArticleService.Detail;
import com.wxn.chatbot.autoguia.service.KnowledgeArticleService.Input;
import com.wxn.chatbot.autoguia.service.KnowledgePublicationRequestService;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/knowledge/articles")
public class KnowledgeArticleController {

    private final KnowledgeArticleService service;
    private final KnowledgePublicationRequestService publicationRequests;
    private final AccountSessions sessions;
    private final ObjectProvider<KnowledgeEmbeddingProvider> embeddingProvider;
    private final boolean embeddingEnabled;
    private final boolean indexingEnabled;

    public KnowledgeArticleController(
        KnowledgeArticleService service,
        KnowledgePublicationRequestService publicationRequests,
        AccountSessions sessions,
        ObjectProvider<KnowledgeEmbeddingProvider> embeddingProvider,
        @Value("${wxn.autoguia.embedding.enabled:false}") boolean embeddingEnabled,
        @Value("${wxn.autoguia.indexing.enabled:false}") boolean indexingEnabled
    ) {
        this.service = service;
        this.publicationRequests = publicationRequests;
        this.sessions = sessions;
        this.embeddingProvider = embeddingProvider;
        this.embeddingEnabled = embeddingEnabled;
        this.indexingEnabled = indexingEnabled;
    }

    @GetMapping
    public List<Article> list(HttpServletRequest request) {
        requireAdmin(request);
        return service.list();
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable long id, HttpServletRequest request) {
        requireAdmin(request);
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@RequestBody Input input, HttpServletRequest request) {
        return service.create(input, requireAdmin(request).id());
    }

    @PostMapping("/{id}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public Detail revise(@PathVariable long id, @RequestBody Input input, HttpServletRequest request) {
        return service.revise(id, input, requireAdmin(request).id());
    }

    @PostMapping("/{id}/archive")
    public Detail archive(@PathVariable long id, HttpServletRequest request) {
        requireAdmin(request);
        return service.archive(id);
    }

    @PostMapping("/{articleId}/versions/{versionId}/publish")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobStatus requestPublication(
        @PathVariable long articleId,
        @PathVariable long versionId,
        HttpServletRequest request
    ) {
        requireAdmin(request);
        if (!embeddingEnabled || !indexingEnabled || embeddingProvider.getIfAvailable() == null) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Indexação da base de conhecimento indisponível"
            );
        }
        return publicationRequests.request(articleId, versionId);
    }

    @GetMapping("/{articleId}/versions/{versionId}/publish")
    public JobStatus publicationStatus(
        @PathVariable long articleId,
        @PathVariable long versionId,
        HttpServletRequest request
    ) {
        requireAdmin(request);
        return publicationRequests.status(articleId, versionId);
    }

    private AccountSessions.Identity requireAdmin(HttpServletRequest request) {
        var identity = sessions.require(request.getSession(false));
        if (identity.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso administrativo necessário");
        }
        return identity;
    }
}
