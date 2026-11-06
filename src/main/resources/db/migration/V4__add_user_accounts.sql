ALTER TABLE users
    ADD COLUMN username VARCHAR(100),
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN cpf VARCHAR(11),
    ADD COLUMN password_hash VARCHAR(100),
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ATTENDANT',
    ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE';

ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'ATTENDANT')),
    ADD CONSTRAINT users_account_status_check CHECK (account_status IN ('ACTIVE', 'INACTIVE')),
    ADD CONSTRAINT users_username_check CHECK (username IS NULL OR username ~ '^[A-Za-z0-9_.-]{3,100}$'),
    ADD CONSTRAINT users_email_check CHECK (
        email IS NULL OR (email = lower(btrim(email)) AND email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$')
    ),
    ADD CONSTRAINT users_cpf_check CHECK (cpf IS NULL OR cpf ~ '^[0-9]{11}$'),
    ADD CONSTRAINT users_active_account_check CHECK (
        account_status <> 'ACTIVE' OR
        (username IS NOT NULL AND email IS NOT NULL AND cpf IS NOT NULL AND password_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX users_username_unique ON users(lower(btrim(username)));
CREATE UNIQUE INDEX users_email_unique ON users(lower(btrim(email)));
CREATE UNIQUE INDEX users_cpf_unique ON users(cpf);

-- Old rows remain intact; every new or updated row must contain a complete identity.
ALTER TABLE users ADD CONSTRAINT users_required_identity_check CHECK (
    username IS NOT NULL AND email IS NOT NULL AND cpf IS NOT NULL AND password_hash IS NOT NULL
) NOT VALID;
ALTER TABLE users ADD CONSTRAINT users_password_hash_check CHECK (
    password_hash IS NULL OR password_hash ~ '^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$'
);
