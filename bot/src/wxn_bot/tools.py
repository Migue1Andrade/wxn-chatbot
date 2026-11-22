"""Ferramentas que o modelo pode chamar durante uma resposta (tool calling).

Ferramentas só leem dados; nenhuma altera o estado do backend. O encaminhamento para um humano continua sendo
parte da decisão final (`needs_human`), que o backend executa.
"""

import logging
from dataclasses import dataclass, field
from typing import Any, Protocol

from anthropic.types import ToolParam
from pydantic import BaseModel, Field, ValidationError

from wxn_bot.knowledge import SearchResult

logger = logging.getLogger(__name__)

SEARCH_TOOL_NAME = "buscar_conhecimento"

SEARCH_TOOL: ToolParam = {
    "name": SEARCH_TOOL_NAME,
    "description": (
        "Consulta os documentos internos da organização sobre horários, entregas, pagamentos, trocas, "
        "procedimentos e políticas. Use antes de responder qualquer pergunta sobre a organização, com uma "
        "consulta curta em português. Devolve trechos numerados com título e texto. Se nenhum trecho "
        "responder à pergunta, a organização não informou isso."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "consulta": {
                "type": "string",
                "description": "O que procurar, em poucas palavras (ex.: entregas aos sábados).",
            }
        },
        "required": ["consulta"],
        "additionalProperties": False,
    },
    "strict": True,
}


class KnowledgeSearch(Protocol):
    def search(self, query: str) -> list[SearchResult]: ...


class SearchInput(BaseModel):
    consulta: str = Field(min_length=1, max_length=500)


@dataclass
class ToolLog:
    """O que as ferramentas fizeram nesta resposta, para a telemetria."""

    calls: list[str] = field(default_factory=list)
    retrieved: list[str] = field(default_factory=list)


def run_tool(name: str, tool_input: Any, knowledge: KnowledgeSearch, log: ToolLog) -> tuple[str, bool]:
    """Executa uma ferramenta e devolve (conteúdo, é_erro) para o bloco `tool_result`."""
    if name != SEARCH_TOOL_NAME:
        log.calls.append(f"{name}:desconhecida")
        return f"Ferramenta desconhecida: {name}.", True
    try:
        query = SearchInput.model_validate(tool_input).consulta
    except ValidationError:
        log.calls.append(f"{name}:entrada_invalida")
        return "Entrada inválida: envie uma consulta curta em texto.", True
    try:
        results = knowledge.search(query)
    except Exception as error:  # banco indisponível não pode derrubar a resposta
        logger.warning("Busca na base de conhecimento falhou: %s", type(error).__name__)
        log.calls.append(f"{name}:erro")
        return "A base de conhecimento está indisponível no momento.", True
    log.calls.append(name)
    log.retrieved.extend(f"{result.source} | {result.title}" for result in results)
    return format_results(results), False


def format_results(results: list[SearchResult]) -> str:
    if not results:
        return "Nenhum trecho encontrado."
    return "Uso interno: responda com estas informações, sem citar trechos ou documentos.\n\n" + "\n\n".join(
        f"[{index}] {result.title} ({result.source})\n{result.content}"
        for index, result in enumerate(results, 1)
    )
