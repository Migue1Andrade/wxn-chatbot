import logging
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from functools import lru_cache
from typing import Annotated

import anthropic
from fastapi import Depends, FastAPI

from wxn_bot.auth import require_api_key
from wxn_bot.config import Settings, get_settings
from wxn_bot.contract import ReplyRequest, ReplyResponse, RunTelemetry
from wxn_bot.embeddings import FastEmbedEmbedder
from wxn_bot.knowledge import KnowledgeBase, PostgresKnowledgeStore
from wxn_bot.pipeline import Outcome, ReplyPipeline
from wxn_bot.responder import ClaudeResponder, Responder

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_app: FastAPI) -> AsyncIterator[None]:
    # Configuração ausente ou inválida impede a inicialização em vez de falhar na primeira requisição;
    # o modelo de embeddings também é carregado aqui, e não na primeira mensagem.
    get_settings()
    get_responder()
    yield


app = FastAPI(title="WXN Bot", lifespan=lifespan)


@lru_cache
def get_responder() -> Responder:
    settings = get_settings()
    client = anthropic.Anthropic(
        api_key=settings.anthropic_api_key.get_secret_value(),
        timeout=settings.bot_model_timeout_seconds,
        max_retries=1,
    )
    knowledge = KnowledgeBase(
        FastEmbedEmbedder(settings.bot_embedding_model),
        PostgresKnowledgeStore(settings.bot_database_url.get_secret_value()),
    )
    return ClaudeResponder(client, settings.bot_history_max_tokens, knowledge, settings.bot_max_tool_steps)


def get_pipeline(
    responder: Annotated[Responder, Depends(get_responder)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ReplyPipeline:
    return ReplyPipeline(responder, settings.models(), settings.bot_max_input_chars)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post(
    "/reply",
    response_model=ReplyResponse,
    dependencies=[Depends(require_api_key)],
)
def reply(
    request: ReplyRequest,
    pipeline: Annotated[ReplyPipeline, Depends(get_pipeline)],
) -> ReplyResponse:
    started = time.perf_counter()
    outcome = pipeline.run(request)
    if outcome.stage != "primary":
        logger.warning(
            "Resposta do bot pelo fallback",
            extra={
                "conversation_id": request.conversation_id,
                "stage": outcome.stage,
                "guardrails": outcome.guardrails,
                "failures": outcome.failures,
            },
        )
    return to_response(outcome, round((time.perf_counter() - started) * 1000))


def to_response(outcome: Outcome, latency_ms: int) -> ReplyResponse:
    decision = outcome.decision
    return ReplyResponse(
        reply=decision.reply,
        intent=decision.intent,
        needs_human=decision.needs_human,
        department_id=decision.department_id,
        handoff_reason=decision.handoff_reason,
        run=RunTelemetry(
            model=outcome.model,
            input_tokens=outcome.input_tokens,
            output_tokens=outcome.output_tokens,
            cached_input_tokens=outcome.cached_input_tokens,
            cache_write_input_tokens=outcome.cache_write_input_tokens,
            history_messages=outcome.history_messages,
            latency_ms=latency_ms,
            request_id=outcome.request_id,
            fallback_stage=outcome.stage,
            guardrails=outcome.guardrails,
            failures=outcome.failures,
            tool_calls=outcome.tool_calls,
            retrieved=outcome.retrieved,
        ),
    )
