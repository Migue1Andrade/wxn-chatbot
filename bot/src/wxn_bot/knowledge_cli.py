"""Comandos da base de conhecimento.

python -m wxn_bot.knowledge_cli ingest              # processa os documentos de BOT_KNOWLEDGE_DIR
python -m wxn_bot.knowledge_cli search "pergunta"   # mostra os trechos que a busca encontraria
"""

import argparse
import sys

from wxn_bot.config import get_settings
from wxn_bot.embeddings import FastEmbedEmbedder
from wxn_bot.knowledge import PostgresKnowledgeStore, ingest, search


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m wxn_bot.knowledge_cli")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("ingest", help="processa os documentos markdown da base")
    query = commands.add_parser("search", help="testa a busca")
    query.add_argument("text")
    query.add_argument("--limit", type=int, default=4)
    args = parser.parse_args(argv)

    settings = get_settings()
    embedder = FastEmbedEmbedder(settings.bot_embedding_model)
    store = PostgresKnowledgeStore(settings.bot_database_url.get_secret_value())

    if args.command == "ingest":
        directory = settings.bot_knowledge_dir
        if not directory.is_dir():
            print(f"Pasta da base não encontrada: {directory}", file=sys.stderr)
            return 1
        report = ingest(directory, embedder, store)
        print(f"{report.sources} documentos, {report.chunks} trechos gravados.")
        if report.removed:
            print(f"Removidos: {', '.join(report.removed)}")
        return 0

    for result in search(args.text, embedder, store, args.limit):
        print(f"[{result.score:.4f}] {result.source} | {result.title}\n{result.content}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
