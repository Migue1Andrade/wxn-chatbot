CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_articles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(240) NOT NULL,
    category VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    current_version_id BIGINT,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT knowledge_articles_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE knowledge_article_versions (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    title VARCHAR(240) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    indexed_at TIMESTAMPTZ,
    CONSTRAINT knowledge_article_versions_number_check CHECK (version_number > 0),
    CONSTRAINT knowledge_article_versions_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT knowledge_article_versions_article_number_unique UNIQUE (article_id, version_number),
    CONSTRAINT knowledge_article_versions_id_article_unique UNIQUE (id, article_id)
);

ALTER TABLE knowledge_articles
    ADD CONSTRAINT knowledge_articles_current_version_fk
    FOREIGN KEY (current_version_id, id)
    REFERENCES knowledge_article_versions (id, article_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE knowledge_article_chunks (
    id BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES knowledge_article_versions(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT knowledge_article_chunks_position_check CHECK (position >= 0),
    CONSTRAINT knowledge_article_chunks_version_position_unique UNIQUE (version_id, position)
);

CREATE INDEX knowledge_articles_status_updated_idx ON knowledge_articles (status, updated_at DESC);
CREATE INDEX knowledge_article_versions_article_created_idx ON knowledge_article_versions (article_id, created_at DESC);
CREATE INDEX knowledge_article_chunks_version_position_idx ON knowledge_article_chunks (version_id, position);
