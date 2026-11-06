CREATE TABLE sla_policy (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    first_response_minutes INTEGER NOT NULL CHECK (first_response_minutes BETWEEN 1 AND 10080),
    next_response_minutes INTEGER NOT NULL CHECK (next_response_minutes BETWEEN 1 AND 10080),
    resolution_minutes INTEGER NOT NULL CHECK (resolution_minutes BETWEEN 1 AND 525600),
    warning_threshold_percent INTEGER NOT NULL CHECK (warning_threshold_percent BETWEEN 1 AND 99),
    timezone VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO sla_policy (id, first_response_minutes, next_response_minutes, resolution_minutes, warning_threshold_percent, timezone)
VALUES (1, 30, 30, 1440, 80, 'UTC');
