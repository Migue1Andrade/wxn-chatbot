package com.wxn.chatbot.autoguia.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeArticleRepository {

    public record Article(long id, String title, String category, String status, Long currentVersionId) {}

    public record Version(
        long id,
        long articleId,
        int number,
        String title,
        String category,
        String content,
        String status
    ) {}

    private final JdbcTemplate jdbc;

    public KnowledgeArticleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long createArticle(String title, String category, long authorId) {
        return jdbc.queryForObject(
            "insert into knowledge_articles(title, category, created_by) values (?, ?, ?) returning id",
            Long.class,
            title,
            category,
            authorId
        );
    }

    public long createVersion(
        long articleId,
        int number,
        String title,
        String category,
        String content,
        long authorId
    ) {
        return jdbc.queryForObject(
            "insert into knowledge_article_versions(article_id, version_number, title, category, content, created_by) " +
                "values (?, ?, ?, ?, ?, ?) returning id",
            Long.class,
            articleId,
            number,
            title,
            category,
            content,
            authorId
        );
    }

    public List<Article> list() {
        return jdbc.query(
            "select id, title, category, status, current_version_id from knowledge_articles " +
                "where status <> 'ARCHIVED' order by updated_at desc, id desc",
            (rs, row) ->
                new Article(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getString("category"),
                    rs.getString("status"),
                    (Long) rs.getObject("current_version_id")
                )
        );
    }

    public Article findById(long articleId) {
        return findById(articleId, false);
    }

    public Article findByIdForUpdate(long articleId) {
        return findById(articleId, true);
    }

    private Article findById(long articleId, boolean forUpdate) {
        return jdbc.query(
            "select id, title, category, status, current_version_id from knowledge_articles where id = ?" +
                (forUpdate ? " for update" : ""),
            rs ->
                rs.next()
                    ? new Article(
                          rs.getLong("id"),
                          rs.getString("title"),
                          rs.getString("category"),
                          rs.getString("status"),
                          (Long) rs.getObject("current_version_id")
                      )
                    : null,
            articleId
        );
    }

    public List<Version> versions(long articleId) {
        return jdbc.query(
            "select id, article_id, version_number, title, category, content, status from knowledge_article_versions " +
                "where article_id = ? order by version_number desc",
            (rs, row) ->
                new Version(
                    rs.getLong("id"),
                    rs.getLong("article_id"),
                    rs.getInt("version_number"),
                    rs.getString("title"),
                    rs.getString("category"),
                    rs.getString("content"),
                    rs.getString("status")
                ),
            articleId
        );
    }

    public boolean exists(long articleId) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                "select exists(select 1 from knowledge_articles where id = ?)",
                Boolean.class,
                articleId
            )
        );
    }

    public Optional<Version> findVersionForUpdate(long articleId, long versionId) {
        return jdbc.query(
            "select id, article_id, version_number, title, category, content, status " +
                "from knowledge_article_versions where id = ? and article_id = ? for update",
            rs ->
                rs.next()
                    ? Optional.of(
                          new Version(
                              rs.getLong("id"),
                              rs.getLong("article_id"),
                              rs.getInt("version_number"),
                              rs.getString("title"),
                              rs.getString("category"),
                              rs.getString("content"),
                              rs.getString("status")
                          )
                      )
                    : Optional.empty(),
            versionId,
            articleId
        );
    }
}
