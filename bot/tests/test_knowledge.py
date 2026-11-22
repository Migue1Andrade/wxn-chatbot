from pathlib import Path

import numpy as np

from wxn_bot.knowledge import MAX_CHUNK_CHARS, Chunk, ingest, split_markdown


def test_sections_become_chunks_with_nested_titles_and_comments_are_dropped() -> None:
    text = """<!-- EXEMPLO: não deve virar conteúdo -->
# Entregas

## Prazo

Até 5 dias úteis.

### Interior

Até 8 dias úteis.
"""

    chunks = split_markdown(text, "entregas.md")

    assert [chunk.title for chunk in chunks] == ["Entregas > Prazo", "Entregas > Prazo > Interior"]
    assert chunks[0].content == "Até 5 dias úteis."
    assert [chunk.position for chunk in chunks] == [0, 1]
    assert all("EXEMPLO" not in chunk.content for chunk in chunks)


def test_long_sections_are_split_by_paragraph() -> None:
    paragraph = "palavra " * 100
    text = "# Manual\n\n" + "\n\n".join([paragraph.strip()] * 5)

    chunks = split_markdown(text, "manual.md")

    assert len(chunks) > 1
    assert all(len(chunk.content) <= MAX_CHUNK_CHARS for chunk in chunks)
    assert all(chunk.title == "Manual" for chunk in chunks)


def test_file_without_sections_produces_no_chunks() -> None:
    assert split_markdown("<!-- só instruções para quem edita -->\n", "README.md") == []


class FakeEmbedder:
    model = "fake-model"

    def embed(self, texts: list[str]) -> list[np.ndarray]:
        return [np.array([float(len(text)), 1.0]) for text in texts]


class FakeStore:
    def __init__(self, stored: dict[str, list[Chunk]] | None = None) -> None:
        self.stored: dict[str, list[Chunk]] = stored or {}
        self.models: set[str] = set()

    def sources(self) -> set[str]:
        return set(self.stored)

    def replace_source(self, source: str, chunks: list[Chunk], vectors: list[np.ndarray], model: str) -> None:
        assert len(chunks) == len(vectors)
        self.stored[source] = chunks
        self.models.add(model)

    def remove_source(self, source: str) -> None:
        del self.stored[source]


def write(directory: Path, name: str, text: str) -> None:
    path = directory / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def test_ingest_replaces_each_source_and_removes_deleted_files(tmp_path: Path) -> None:
    write(tmp_path, "entregas.md", "# Entregas\n\n## Prazo\n\nAté 5 dias úteis.\n")
    write(tmp_path, "faq/pagamentos.md", "# Pagamentos\n\nPix e boleto.\n")
    write(tmp_path, "README.md", "<!-- instruções -->\n")
    old = Chunk(source="antigo.md", position=0, title="Antigo", content="Removido")
    store = FakeStore({"antigo.md": [old], "entregas.md": [old, old, old]})

    report = ingest(tmp_path, FakeEmbedder(), store)

    assert report.sources == 2
    assert report.chunks == 2
    assert report.removed == ["antigo.md"]
    assert set(store.stored) == {"entregas.md", "faq/pagamentos.md"}
    assert store.stored["entregas.md"][0].content == "Até 5 dias úteis."
    assert store.models == {"fake-model"}


def test_reingesting_the_same_files_does_not_duplicate(tmp_path: Path) -> None:
    write(tmp_path, "entregas.md", "# Entregas\n\nSegunda a sábado.\n")
    store = FakeStore()

    ingest(tmp_path, FakeEmbedder(), store)
    ingest(tmp_path, FakeEmbedder(), store)

    assert len(store.stored["entregas.md"]) == 1
