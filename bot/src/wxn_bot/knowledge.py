"""Base de conhecimento: documentos markdown da organização divididos em trechos e guardados no pgvector.

- Ingestão: cada arquivo vira trechos (um por seção, divididos se forem longos); reprocessar um arquivo
  substitui os trechos dele, e arquivos removidos têm os trechos apagados.
- Busca híbrida: por sentido (vetores) e por palavra (texto em português, sem acentos), combinadas por
  Reciprocal Rank Fusion. Uma cobre a falha da outra: sinônimos pela primeira, termos exatos pela segunda.
"""

import re
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import numpy as np
import psycopg
from pgvector.psycopg import register_vector

from wxn_bot.embeddings import Embedder

MAX_CHUNK_CHARS = 1200
HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
# Constante usual do Reciprocal Rank Fusion: suaviza a diferença entre as primeiras posições.
RRF_K = 60
CANDIDATES = 20


@dataclass(frozen=True)
class Chunk:
    source: str
    position: int
    title: str
    content: str

    def embedding_text(self) -> str:
        return f"{self.title}\n\n{self.content}"


@dataclass(frozen=True)
class SearchResult:
    source: str
    title: str
    content: str
    score: float


@dataclass(frozen=True)
class IngestReport:
    sources: int
    chunks: int
    removed: list[str]


class KnowledgeStore(Protocol):
    def sources(self) -> set[str]: ...

    def replace_source(
        self, source: str, chunks: list[Chunk], vectors: list[np.ndarray], model: str
    ) -> None: ...

    def remove_source(self, source: str) -> None: ...


def split_markdown(text: str, source: str) -> list[Chunk]:
    """Um trecho por seção; o título acumula os cabeçalhos ("Entregas > Prazos")."""
    text = HTML_COMMENT.sub("", text)
    headings: list[str] = []
    sections: list[tuple[str, str]] = []
    lines: list[str] = []

    def close_section() -> None:
        body = "\n".join(lines).strip()
        if body:
            sections.append((" > ".join(headings) or Path(source).stem, body))
        lines.clear()

    for line in text.splitlines():
        match = HEADING.match(line)
        if match:
            close_section()
            level = len(match.group(1))
            del headings[level - 1 :]
            headings.append(match.group(2))
        else:
            lines.append(line)
    close_section()

    chunks: list[Chunk] = []
    for title, body in sections:
        for part in split_long(body):
            chunks.append(Chunk(source=source, position=len(chunks), title=title, content=part))
    return chunks


def split_long(body: str) -> list[str]:
    """Divide seções longas por parágrafo, sem cortar um parágrafo no meio."""
    if len(body) <= MAX_CHUNK_CHARS:
        return [body]
    parts: list[str] = []
    current: list[str] = []
    for paragraph in re.split(r"\n\s*\n", body):
        candidate = "\n\n".join([*current, paragraph])
        if current and len(candidate) > MAX_CHUNK_CHARS:
            parts.append("\n\n".join(current))
            current = [paragraph]
        else:
            current.append(paragraph)
    if current:
        parts.append("\n\n".join(current))
    return parts


def ingest(directory: Path, embedder: Embedder, store: KnowledgeStore) -> IngestReport:
    files = sorted(path for path in directory.rglob("*.md") if path.is_file())
    found: set[str] = set()
    total = 0
    for path in files:
        source = path.relative_to(directory).as_posix()
        chunks = split_markdown(path.read_text(encoding="utf-8"), source)
        if not chunks:
            continue
        vectors = embedder.embed([chunk.embedding_text() for chunk in chunks])
        store.replace_source(source, chunks, vectors, embedder.model)
        found.add(source)
        total += len(chunks)
    removed = sorted(store.sources() - found)
    for source in removed:
        store.remove_source(source)
    return IngestReport(sources=len(found), chunks=total, removed=removed)


SEARCH_SQL = """
WITH query AS (
    -- OU entre as palavras: basta uma aparecer ("dá pra parcelar" encontra "parcelamento").
    SELECT coalesce(
        string_agg(quote_literal(lexeme), ' | ')::tsquery,
        ''::tsquery
    ) AS terms
    FROM unnest(tsvector_to_array(to_tsvector('portuguese_unaccent'::regconfig, %(text)s))) AS lexeme
),
semantic AS (
    SELECT id, row_number() OVER (ORDER BY embedding <=> %(vector)s) AS rank
    FROM knowledge_chunks
    WHERE embedding_model = %(model)s
    ORDER BY embedding <=> %(vector)s
    LIMIT %(candidates)s
),
keyword AS (
    SELECT id, row_number() OVER (ORDER BY ts_rank_cd(search, query.terms) DESC) AS rank
    FROM knowledge_chunks, query
    WHERE search @@ query.terms
    ORDER BY ts_rank_cd(search, query.terms) DESC
    LIMIT %(candidates)s
),
ranked AS (
    SELECT id, sum(1.0 / (%(rrf_k)s + rank)) AS score
    FROM (SELECT * FROM semantic UNION ALL SELECT * FROM keyword) AS candidates
    GROUP BY id
)
SELECT chunk.source, chunk.title, chunk.content, ranked.score
FROM ranked
JOIN knowledge_chunks AS chunk ON chunk.id = ranked.id
ORDER BY ranked.score DESC, chunk.source, chunk.position
LIMIT %(limit)s
"""


class PostgresKnowledgeStore:
    def __init__(self, database_url: str) -> None:
        self._database_url = database_url

    @contextmanager
    def _connect(self) -> Iterator[psycopg.Connection]:
        with psycopg.connect(self._database_url) as connection:
            register_vector(connection)
            yield connection

    def sources(self) -> set[str]:
        with self._connect() as connection:
            rows = connection.execute("SELECT DISTINCT source FROM knowledge_chunks").fetchall()
        return {row[0] for row in rows}

    def replace_source(self, source: str, chunks: list[Chunk], vectors: list[np.ndarray], model: str) -> None:
        # Apagar e inserir na mesma transação: a busca nunca vê o arquivo pela metade.
        with self._connect() as connection, connection.transaction():
            connection.execute("DELETE FROM knowledge_chunks WHERE source = %s", (source,))
            with connection.cursor() as cursor:
                cursor.executemany(
                    """
                    INSERT INTO knowledge_chunks
                        (source, position, title, content, embedding_model, embedding)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    [
                        (chunk.source, chunk.position, chunk.title, chunk.content, model, vector)
                        for chunk, vector in zip(chunks, vectors, strict=True)
                    ],
                )

    def remove_source(self, source: str) -> None:
        with self._connect() as connection:
            connection.execute("DELETE FROM knowledge_chunks WHERE source = %s", (source,))

    def search(self, text: str, vector: np.ndarray, model: str, limit: int = 4) -> list[SearchResult]:
        params = {
            "text": text,
            "vector": vector,
            "model": model,
            "candidates": CANDIDATES,
            "rrf_k": RRF_K,
            "limit": limit,
        }
        with self._connect() as connection:
            rows = connection.execute(SEARCH_SQL, params).fetchall()
        return [
            SearchResult(source=row[0], title=row[1], content=row[2], score=float(row[3])) for row in rows
        ]


class KnowledgeBase:
    """Busca usada pela ferramenta do agente: gera o vetor da consulta e consulta o banco."""

    def __init__(self, embedder: Embedder, store: PostgresKnowledgeStore, limit: int = 4) -> None:
        self._embedder = embedder
        self._store = store
        self._limit = limit

    def search(self, query: str) -> list[SearchResult]:
        return search(query, self._embedder, self._store, self._limit)


def search(
    query: str, embedder: Embedder, store: PostgresKnowledgeStore, limit: int = 4
) -> list[SearchResult]:
    (vector,) = embedder.embed([query])
    return store.search(query, vector, embedder.model, limit)
