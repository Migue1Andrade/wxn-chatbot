ALTER TABLE user_notifications ADD COLUMN deduplication_key VARCHAR(240);
CREATE UNIQUE INDEX user_notifications_deduplication_unique
    ON user_notifications (deduplication_key) WHERE deduplication_key IS NOT NULL;
