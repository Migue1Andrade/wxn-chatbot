CREATE TABLE knowledge_index_jobs (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    version_id BIGINT NOT NULL REFERENCES knowledge_article_versions(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT knowledge_index_jobs_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT knowledge_index_jobs_attempts_check
        CHECK (attempts >= 0 AND max_attempts > 0 AND attempts <= max_attempts),
    CONSTRAINT knowledge_index_jobs_version_unique UNIQUE (version_id)
);

CREATE INDEX knowledge_index_jobs_claim_idx
    ON knowledge_index_jobs (available_at, id)
    WHERE status = 'PENDING';

CREATE INDEX knowledge_index_jobs_expired_lease_idx
    ON knowledge_index_jobs (lease_until, id)
    WHERE status = 'PROCESSING';
