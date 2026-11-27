package com.wxn.chatbot.autoguia.repository;

import com.wxn.chatbot.autoguia.domain.EmbeddingVector;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeRetrievalRepository {

    public record SourceChunk(
        long articleId,
        long versionId,
        int versionNumber,
        int position,
        String title,
        String content,
        double score
    ) {}

    private static final int MAX_RESULTS = 50;
    private static final int RRF_K = 60;

    private static final String PUBLISHED_CHUNKS = """
    with published_chunks as materialized (
        select chunk.id, article.id as article_id, version.id as version_id,
               version.version_number,
               chunk.position, version.title, chunk.content, chunk.embedding,
               chunk.embedding_provider, chunk.embedding_model, chunk.embedding_dimension
        from knowledge_articles article
        join knowledge_article_versions version
          on version.id = article.current_version_id
         and version.article_id = article.id
         and version.status = 'PUBLISHED'
        join knowledge_article_chunks chunk on chunk.version_id = version.id
        where article.status = 'PUBLISHED'
    )
    """;

    private static final String TEXT_CANDIDATES = """
    , text_candidates as (
        select published_chunks.*,
               ts_rank_cd(
                   setweight(to_tsvector('portuguese', title), 'A') ||
                   setweight(to_tsvector('portuguese', content), 'B'),
                   websearch_to_tsquery('portuguese', ?)
               ) as relevance
        from published_chunks
        where (setweight(to_tsvector('portuguese', title), 'A') ||
               setweight(to_tsvector('portuguese', content), 'B'))
              @@ websearch_to_tsquery('portuguese', ?)
        order by relevance desc, article_id, position, id
        limit ?
    ), text_ranked as (
        select text_candidates.*, row_number() over (
            order by relevance desc, article_id, position, id
        ) as rank
        from text_candidates
    )
    """;

    private final JdbcTemplate jdbc;

    public KnowledgeRetrievalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SourceChunk> searchText(String query, int limit) {
        validate(query, limit);
        String sql =
            PUBLISHED_CHUNKS +
            TEXT_CANDIDATES +
            """
            select article_id, version_id, version_number, position, title, content,
                   1.0 / (? + rank) as score
            from text_ranked
            order by score desc, rank, article_id, position, id
            limit ?
            """;
        return jdbc.query(sql, this::map, query, query, limit, RRF_K, limit);
    }

    public List<SourceChunk> searchHybrid(String query, EmbeddingVector embedding, int limit) {
        return searchHybrid(query, embedding, limit, 0.0);
    }

    public List<SourceChunk> searchHybrid(
        String query,
        EmbeddingVector embedding,
        int limit,
        double minSimilarity
    ) {
        validate(query, limit);
        if (!Double.isFinite(minSimilarity) || minSimilarity < 0.0 || minSimilarity > 1.0) {
            throw new IllegalArgumentException("Minimum vector similarity must be between zero and one");
        }
        if (embedding == null) {
            return searchText(query, limit);
        }
        String sql =
            PUBLISHED_CHUNKS +
            TEXT_CANDIDATES +
            """
            , compatible_vectors as materialized (
                select * from published_chunks
                where embedding_provider = ? and embedding_model = ?
                  and embedding_dimension = ? and vector_dims(embedding) = ?
            ), vector_scored as materialized (
                select compatible_vectors.*,
                       compatible_vectors.embedding <=> ?::vector as distance
                from compatible_vectors
            ), vector_candidates as (
                select * from vector_scored
                where distance <= (1.0 - ?)
                order by distance, article_id, position, id
                limit ?
            ), vector_ranked as (
                select vector_candidates.*, row_number() over (
                    order by distance, article_id, position, id
                ) as rank
                from vector_candidates
            ), candidate_ranks as (
                select id, article_id, version_id, version_number, position, title, content,
                       rank as text_rank, null::bigint as vector_rank
                from text_ranked
                union all
                select id, article_id, version_id, version_number, position, title, content,
                       null::bigint as text_rank, rank as vector_rank
                from vector_ranked
            ), fused as (
                select id, article_id, version_id, version_number, position, title, content,
                       sum(1.0 / (? + coalesce(text_rank, vector_rank))) as score,
                       min(text_rank) as text_rank, min(vector_rank) as vector_rank
                from candidate_ranks
                group by id, article_id, version_id, version_number, position, title, content
            )
            select article_id, version_id, version_number, position, title, content, score
            from fused
            order by score desc, text_rank nulls last, vector_rank nulls last,
                     article_id, position, id
            limit ?
            """;
        String vector = embedding.values().toString();
        return jdbc.query(
            sql,
            this::map,
            query,
            query,
            limit * 4,
            embedding.provider(),
            embedding.model(),
            embedding.dimension(),
            embedding.dimension(),
            vector,
            minSimilarity,
            limit * 4,
            RRF_K,
            limit
        );
    }

    private SourceChunk map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new SourceChunk(
            rs.getLong("article_id"),
            rs.getLong("version_id"),
            rs.getInt("version_number"),
            rs.getInt("position"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getDouble("score")
        );
    }

    private static void validate(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query is required");
        }
        if (limit <= 0 || limit > MAX_RESULTS) {
            throw new IllegalArgumentException("Search limit is outside supported bounds");
        }
    }
}
