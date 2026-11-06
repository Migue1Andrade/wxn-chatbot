ALTER TABLE departments ADD COLUMN description VARCHAR(500);
ALTER TABLE departments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE'));
ALTER TABLE departments ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE departments ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE departments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE demo_seed_manifest (
    seed_key VARCHAR(150) PRIMARY KEY,
    resource_type VARCHAR(60) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ADMIN may operate without a department, but domain actions require its own HUMAN member.
-- Preserve existing members, account status, passwords and department associations.
INSERT INTO members (user_id, name, type, status, created_at, updated_at)
SELECT u.id, u.name, 'HUMAN', u.account_status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
WHERE u.role = 'ADMIN' AND u.deleted_at IS NULL
    AND NOT EXISTS (
        SELECT 1 FROM members m
        WHERE m.user_id = u.id AND m.type = 'HUMAN' AND m.deleted_at IS NULL
    );
