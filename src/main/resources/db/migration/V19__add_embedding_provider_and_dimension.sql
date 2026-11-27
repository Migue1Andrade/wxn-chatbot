ALTER TABLE knowledge_article_chunks
    ADD COLUMN embedding_provider VARCHAR(160) NOT NULL DEFAULT 'legacy';

ALTER TABLE knowledge_article_chunks
    ADD COLUMN embedding_dimension INTEGER NOT NULL DEFAULT 1;

ALTER TABLE knowledge_article_chunks
    ADD CONSTRAINT knowledge_article_chunks_embedding_dimension_check CHECK (embedding_dimension > 0);

ALTER TABLE knowledge_article_chunks
    ALTER COLUMN embedding_provider DROP DEFAULT,
    ALTER COLUMN embedding_dimension DROP DEFAULT;
