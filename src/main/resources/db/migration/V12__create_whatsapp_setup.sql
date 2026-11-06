CREATE TABLE whatsapp_setup (
    id INTEGER PRIMARY KEY,
    channel_id BIGINT REFERENCES whatsapp_channels(id),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    connected_phone VARCHAR(40),
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE whatsapp_channels
    DROP CONSTRAINT whatsapp_channels_provider_check;

ALTER TABLE whatsapp_channels
    ADD CONSTRAINT whatsapp_channels_provider_check
    CHECK (provider IN ('MOCK', 'TWILIO', 'ZAPI', 'EVOLUTION'));
