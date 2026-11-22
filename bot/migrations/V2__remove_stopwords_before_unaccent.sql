-- Palavras comuns ("não", "você", "é") perdiam o acento antes do filtro de stopwords em português e deixavam
-- de ser descartadas; "nao" passava a casar com qualquer trecho que tivesse "não". Agora o filtro vem antes.

CREATE TEXT SEARCH DICTIONARY portuguese_stopwords (
    TEMPLATE = simple,
    STOPWORDS = portuguese,
    ACCEPT = false
);

ALTER TEXT SEARCH CONFIGURATION portuguese_unaccent
    ALTER MAPPING FOR hword, hword_part, word
    WITH portuguese_stopwords, unaccent, portuguese_stem;

-- Recalcula a coluna gerada `search` dos trechos já gravados.
UPDATE knowledge_chunks
SET content = content;
