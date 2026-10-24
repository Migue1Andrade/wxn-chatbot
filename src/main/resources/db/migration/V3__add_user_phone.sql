ALTER TABLE users ADD COLUMN phone VARCHAR(20) NULL;
ALTER TABLE users ADD CONSTRAINT users_phone_e164 CHECK (phone IS NULL OR phone ~ '^[+][1-9][0-9]{7,14}$');
CREATE UNIQUE INDEX users_company_phone_unique ON users(company_id, phone) WHERE phone IS NOT NULL;
-- Supports the composite FK that enforces the member/user tenant match.
ALTER TABLE users ADD CONSTRAINT users_company_id_id_unique UNIQUE(company_id, id);
