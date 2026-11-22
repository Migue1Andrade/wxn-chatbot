-- Base de conhecimento do bot: trechos dos documentos da organização, com vetor (busca por sentido) e
-- índice de texto em português sem acentos (busca por palavra). Banco exclusivo do bot.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE EXTENSION IF NOT EXISTS unaccent;

-- Clientes escrevem com e sem acento ("sábado", "sabado"); a busca por palavra ignora a diferença.
CREATE TEXT SEARCH CONFIGURATION portuguese_unaccent (COPY = portuguese);

ALTER TEXT SEARCH CONFIGURATION portuguese_unaccent
    ALTER MAPPING FOR hword, hword_part, word
    WITH unaccent, portuguese_stem;

CREATE TABLE knowledge_chunks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source TEXT NOT NULL,
    position INTEGER NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    -- O modelo fica registrado para que a busca só compare vetores do mesmo modelo.
    embedding_model TEXT NOT NULL,
    embedding VECTOR NOT NULL,
    search TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('portuguese_unaccent'::regconfig, title || ' ' || content)
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT knowledge_chunks_source_position_key UNIQUE (source, position)
);

CREATE INDEX knowledge_chunks_search_idx
    ON knowledge_chunks
    USING gin (search);
