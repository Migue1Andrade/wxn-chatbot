ALTER TABLE whatsapp_channels
    DROP CONSTRAINT whatsapp_channels_provider_check;

ALTER TABLE whatsapp_channels
    ADD CONSTRAINT whatsapp_channels_provider_check
    CHECK (provider IN ('MOCK', 'TWILIO', 'ZAPI'));
