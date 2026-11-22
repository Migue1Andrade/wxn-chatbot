"""Busca de verdade: banco pgvector e modelo de embeddings reais. Só roda com opt-in:

    BOT_TEST_DATABASE_URL=postgresql://bot:SENHA@localhost:PORTA/bot \
        uv run pytest tests/test_knowledge_search.py

O teste grava e apaga os trechos de uma origem própria (`teste/…`), sem tocar no resto da base.
"""

import os
from pathlib import Path

import pytest

DATABASE_URL = os.environ.get("BOT_TEST_DATABASE_URL")

pytestmark = pytest.mark.skipif(not DATABASE_URL, reason="defina BOT_TEST_DATABASE_URL para rodar")

QUESTIONS = [
    ("vocês entregam no sábado?", "Entregas > Dias e horários"),
    ("dá pra parcelar?", "Pagamentos > Formas de pagamento"),
    ("comprei e não gostei, posso devolver?", "Trocas e devoluções > Prazo para troca ou devolução"),
    ("até que horas vocês atendem?", "Atendimento > Horário de atendimento"),
    ("perdi meu boleto, como pego outro?", "Pagamentos > Segunda via de boleto"),
    ("aceita pix?", "Pagamentos > Formas de pagamento"),
    ("entregam no sabado", "Entregas > Dias e horários"),
]


@pytest.fixture(scope="module")
def knowledge(tmp_path_factory: pytest.TempPathFactory):
    from wxn_bot.config import DEFAULT_EMBEDDING_MODEL
    from wxn_bot.embeddings import FastEmbedEmbedder
    from wxn_bot.knowledge import PostgresKnowledgeStore, split_markdown

    embedder = FastEmbedEmbedder(DEFAULT_EMBEDDING_MODEL)
    store = PostgresKnowledgeStore(DATABASE_URL)
    examples = Path(__file__).resolve().parents[1] / "knowledge"
    sources = []
    for path in sorted(examples.glob("*.md")):
        source = f"teste/{path.name}"
        chunks = split_markdown(path.read_text(encoding="utf-8"), source)
        if chunks:
            store.replace_source(
                source, chunks, embedder.embed([c.embedding_text() for c in chunks]), embedder.model
            )
            sources.append(source)
    yield embedder, store
    for source in sources:
        store.remove_source(source)


@pytest.mark.parametrize(("question", "expected_title"), QUESTIONS)
def test_hybrid_search_finds_the_right_section(knowledge, question: str, expected_title: str) -> None:
    from wxn_bot.knowledge import search

    embedder, store = knowledge

    results = [r for r in search(question, embedder, store, limit=10) if r.source.startswith("teste/")]

    # O bot recebe os melhores trechos, não só o primeiro.
    assert expected_title in [result.title for result in results[:3]]
