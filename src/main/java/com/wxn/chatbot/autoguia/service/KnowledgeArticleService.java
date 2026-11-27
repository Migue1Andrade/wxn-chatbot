package com.wxn.chatbot.autoguia.service;

import com.wxn.chatbot.autoguia.domain.EmbeddingVector;
import com.wxn.chatbot.autoguia.repository.KnowledgeArticleRepository;
import com.wxn.chatbot.autoguia.repository.KnowledgeArticleRepository.Article;
import com.wxn.chatbot.autoguia.repository.KnowledgeArticleRepository.Version;
import com.wxn.chatbot.common.ApiException;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeArticleService {

    public record Input(String title, String category, String content) {}

    private final KnowledgeArticleRepository repository;
    private final JdbcTemplate jdbc;

    public KnowledgeArticleService(KnowledgeArticleRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public record Detail(Article article, List<Version> versions) {}

    public record IndexedChunk(int position, String content, EmbeddingVector embedding) {}

    @Transactional
    public Detail create(Input input, long authorId) {
        validate(input);
        long articleId = repository.createArticle(input.title().trim(), input.category().trim(), authorId);
        repository.createVersion(
            articleId,
            1,
            input.title().trim(),
            input.category().trim(),
            input.content().trim(),
            authorId
        );
        return get(articleId);
    }

    @Transactional
    public Detail revise(long articleId, Input input, long authorId) {
        validate(input);
        Article article = repository.findByIdForUpdate(articleId);
        if (article == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Artigo não encontrado");
        }
        if ("ARCHIVED".equals(article.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "Artigo arquivado não aceita novas revisões");
        }
        List<Version> versions = repository.versions(articleId);
        int next = versions.stream().mapToInt(Version::number).max().orElse(0) + 1;
        jdbc.update("update knowledge_articles set updated_at = current_timestamp where id = ?", articleId);
        repository.createVersion(
            articleId,
            next,
            input.title().trim(),
            input.category().trim(),
            input.content().trim(),
            authorId
        );
        return get(articleId);
    }

    @Transactional
    public Detail archive(long articleId) {
        Article article = repository.findByIdForUpdate(articleId);
        if (article == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Artigo não encontrado");
        }
        if (!"ARCHIVED".equals(article.status())) {
            jdbc.update(
                "update knowledge_articles set status = 'ARCHIVED', updated_at = current_timestamp where id = ?",
                articleId
            );
        }
        return get(articleId);
    }

    @Transactional
    public Detail publish(long articleId, long versionId, List<IndexedChunk> chunks) {
        Article article = repository.findByIdForUpdate(articleId);
        if (article == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Artigo não encontrado");
        }
        if ("ARCHIVED".equals(article.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "Artigo arquivado não pode ser publicado");
        }
        Version target = repository.findVersionForUpdate(articleId, versionId).orElse(null);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Versão não encontrada");
        }
        if (article.currentVersionId() != null && versionId == article.currentVersionId()) {
            return get(articleId);
        }
        int newestVersion = repository.versions(articleId).stream().mapToInt(Version::number).max().orElse(0);
        if (!"DRAFT".equals(target.status()) || target.number() != newestVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "Somente a revisão mais recente pode ser publicada");
        }
        if (chunks == null || chunks.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A versão precisa de trechos indexados");
        }
        if (chunks.get(0) == null || chunks.get(0).embedding() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Trechos inválidos ou embeddings incompatíveis");
        }
        EmbeddingVector identity = chunks.get(0).embedding();
        for (int i = 0; i < chunks.size(); i++) {
            IndexedChunk chunk = chunks.get(i);
            if (
                chunk == null ||
                chunk.position() != i ||
                chunk.content() == null ||
                chunk.content().isBlank() ||
                chunk.embedding() == null ||
                !sameEmbeddingIdentity(identity, chunk.embedding())
            ) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Trechos inválidos ou embeddings incompatíveis"
                );
            }
        }
        jdbc.update("delete from knowledge_article_chunks where version_id = ?", versionId);
        for (IndexedChunk chunk : chunks) {
            String vector = chunk.embedding().values().toString();
            jdbc.update(
                "insert into knowledge_article_chunks(version_id, position, content, embedding, embedding_model, " +
                    "embedding_provider, embedding_dimension) values (?, ?, ?, ?::vector, ?, ?, ?)",
                versionId,
                chunk.position(),
                chunk.content(),
                vector,
                chunk.embedding().model(),
                chunk.embedding().provider(),
                chunk.embedding().dimension()
            );
        }
        jdbc.update(
            "update knowledge_article_versions set status = 'ARCHIVED' where article_id = ? and status = 'PUBLISHED'",
            articleId
        );
        jdbc.update(
            "update knowledge_article_versions set status = 'PUBLISHED', indexed_at = current_timestamp " +
                "where id = ? and article_id = ?",
            versionId,
            articleId
        );
        jdbc.update(
            "update knowledge_articles set current_version_id = ?, title = ?, category = ?, status = 'PUBLISHED', " +
                "updated_at = current_timestamp where id = ?",
            versionId,
            jdbc.queryForObject(
                "select title from knowledge_article_versions where id = ?",
                String.class,
                versionId
            ),
            jdbc.queryForObject(
                "select category from knowledge_article_versions where id = ?",
                String.class,
                versionId
            ),
            articleId
        );
        return get(articleId);
    }

    private boolean sameEmbeddingIdentity(EmbeddingVector left, EmbeddingVector right) {
        return (
            Objects.equals(left.provider(), right.provider()) &&
            Objects.equals(left.model(), right.model()) &&
            left.dimension() == right.dimension()
        );
    }

    @Transactional(readOnly = true)
    public List<Article> list() {
        return repository.list();
    }

    @Transactional(readOnly = true)
    public Detail get(long articleId) {
        Article article = repository.findById(articleId);
        if (article == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Artigo não encontrado");
        }
        return new Detail(article, repository.versions(articleId));
    }

    private void validate(Input input) {
        if (
            input == null ||
            input.title() == null ||
            input.title().isBlank() ||
            input.title().length() > 240 ||
            input.category() == null ||
            input.category().isBlank() ||
            input.category().length() > 80 ||
            input.content() == null ||
            input.content().isBlank()
        ) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Título, categoria e conteúdo são obrigatórios");
        }
    }
}
