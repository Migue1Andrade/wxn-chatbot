package com.wxn.chatbot.autoguia.repository;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeGapRepository {

    public record Gap(
        long id,
        String requestId,
        String conversationId,
        String reason,
        String status,
        Long draftArticleId,
        OffsetDateTime createdAt
    ) {}

    private final JdbcTemplate jdbc;

    public KnowledgeGapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertIfAbsent(String requestId, String conversationId) {
        jdbc.update(
            "insert into knowledge_gaps(request_id, conversation_id, reason) values (?, ?, 'NO_SOURCE') " +
                "on conflict (request_id, conversation_id) do nothing",
            requestId,
            conversationId
        );
    }

    public List<Gap> page(int limit, long offset) {
        return jdbc.query(
            "select id, request_id, conversation_id, reason, status, draft_article_id, created_at " +
                "from knowledge_gaps order by created_at desc, id desc limit ? offset ?",
            (rs, row) ->
                new Gap(
                    rs.getLong("id"),
                    rs.getString("request_id"),
                    rs.getString("conversation_id"),
                    rs.getString("reason"),
                    rs.getString("status"),
                    (Long) rs.getObject("draft_article_id"),
                    rs.getObject("created_at", OffsetDateTime.class)
                ),
            limit,
            offset
        );
    }

    public long count() {
        return jdbc.queryForObject("select count(*) from knowledge_gaps", Long.class);
    }

    public Gap findForUpdate(long id) {
        return jdbc.query(
            "select id, request_id, conversation_id, reason, status, draft_article_id, created_at " +
                "from knowledge_gaps where id = ? for update",
            rs ->
                rs.next()
                    ? new Gap(
                          rs.getLong("id"),
                          rs.getString("request_id"),
                          rs.getString("conversation_id"),
                          rs.getString("reason"),
                          rs.getString("status"),
                          (Long) rs.getObject("draft_article_id"),
                          rs.getObject("created_at", OffsetDateTime.class)
                      )
                    : null,
            id
        );
    }

    public void attachDraft(long gapId, long articleId) {
        jdbc.update(
            "update knowledge_gaps set status = 'CONVERTED', draft_article_id = ? where id = ?",
            articleId,
            gapId
        );
    }
}
