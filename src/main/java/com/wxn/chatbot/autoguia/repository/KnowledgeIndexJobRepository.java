package com.wxn.chatbot.autoguia.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class KnowledgeIndexJobRepository {

    public record Job(long id, long articleId, long versionId, int attempts, int maxAttempts) {}

    public record JobStatus(long id, String status, int attempts, int maxAttempts, String error) {}

    private final JdbcTemplate jdbc;

    public KnowledgeIndexJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JobStatus enqueue(long articleId, long versionId, int maxAttempts) {
        return jdbc
            .query(
                "insert into knowledge_index_jobs(article_id, version_id, max_attempts) " +
                    "select article.id, version.id, ? from knowledge_articles article " +
                    "join knowledge_article_versions version on version.article_id = article.id " +
                    "where article.id = ? and version.id = ? and article.status <> 'ARCHIVED' " +
                    "and version.status = 'DRAFT' and not exists (select 1 from knowledge_article_versions newer " +
                    "where newer.article_id = article.id and newer.version_number > version.version_number) " +
                    "on conflict(version_id) do update set " +
                    "status = case when knowledge_index_jobs.status = 'FAILED' " +
                    "and knowledge_index_jobs.attempts < knowledge_index_jobs.max_attempts then 'PENDING' " +
                    "else knowledge_index_jobs.status end, " +
                    "available_at = case when knowledge_index_jobs.status = 'FAILED' " +
                    "and knowledge_index_jobs.attempts < knowledge_index_jobs.max_attempts then current_timestamp " +
                    "else knowledge_index_jobs.available_at end, updated_at = current_timestamp " +
                    "returning id, status, attempts, max_attempts, last_error",
                (rs, row) ->
                    new JobStatus(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts"),
                        rs.getString("last_error") == null ? null : "Indexing failed"
                    ),
                maxAttempts,
                articleId,
                versionId
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    public JobStatus findStatus(long articleId, long versionId) {
        return jdbc.query(
            "select id, status, attempts, max_attempts, last_error from knowledge_index_jobs " +
                "where article_id = ? and version_id = ?",
            rs ->
                rs.next()
                    ? new JobStatus(
                          rs.getLong("id"),
                          rs.getString("status"),
                          rs.getInt("attempts"),
                          rs.getInt("max_attempts"),
                          rs.getString("last_error") == null ? null : "Indexing failed"
                      )
                    : null,
            articleId,
            versionId
        );
    }

    @Transactional
    public List<Job> claim(int limit, int leaseSeconds) {
        if (limit <= 0 || leaseSeconds <= 0) {
            throw new IllegalArgumentException("Claim limit and lease duration must be positive");
        }
        jdbc.update(
            "update knowledge_index_jobs set status = 'FAILED', lease_until = null, " +
                "last_error = 'Indexing attempts exhausted after lease expiration', " +
                "updated_at = current_timestamp where status = 'PROCESSING' " +
                "and lease_until < current_timestamp and attempts >= max_attempts"
        );
        return jdbc.query(
            "with candidates as (select id from knowledge_index_jobs where " +
                "((status = 'PENDING' and available_at <= current_timestamp) or " +
                "(status = 'PROCESSING' and lease_until < current_timestamp)) and attempts < max_attempts " +
                "order by available_at, id for update skip locked limit ?) " +
                "update knowledge_index_jobs job set status = 'PROCESSING', attempts = attempts + 1, " +
                "lease_until = current_timestamp + (? * interval '1 second'), updated_at = current_timestamp " +
                "from candidates where job.id = candidates.id " +
                "returning job.id, job.article_id, job.version_id, job.attempts, job.max_attempts",
            (rs, row) ->
                new Job(
                    rs.getLong("id"),
                    rs.getLong("article_id"),
                    rs.getLong("version_id"),
                    rs.getInt("attempts"),
                    rs.getInt("max_attempts")
                ),
            limit,
            leaseSeconds
        );
    }

    public boolean complete(long jobId, int attempt) {
        return (
            jdbc.update(
                "update knowledge_index_jobs set status = 'COMPLETED', lease_until = null, " +
                    "last_error = null, updated_at = current_timestamp where id = ? and status = 'PROCESSING' " +
                    "and attempts = ? and lease_until > current_timestamp",
                jobId,
                attempt
            ) == 1
        );
    }

    public boolean fail(long jobId, int attempt, String safeError) {
        return (
            jdbc.update(
                "update knowledge_index_jobs set status = case when attempts < max_attempts then 'PENDING' " +
                    "else 'FAILED' end, available_at = current_timestamp + " +
                    "(least(attempts, 6) * interval '30 seconds'), lease_until = null, last_error = ?, " +
                    "updated_at = current_timestamp where id = ? and status = 'PROCESSING' " +
                    "and attempts = ? and lease_until > current_timestamp",
                safeError == null
                    ? "Indexing failed"
                    : safeError.substring(0, Math.min(500, safeError.length())),
                jobId,
                attempt
            ) == 1
        );
    }

    /** Publication must verify the same attempt and unexpired lease under lock in its transaction. */
}
