"""Contrato HTTP entre o backend e o serviço do bot.

O JSON trafega em camelCase, como no restante da API; o código Python usa snake_case.
"""

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

MAX_MESSAGE_LENGTH = 4096
MAX_REPLY_LENGTH = 1600


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")


class HistoryMessage(ApiModel):
    direction: Literal["INBOUND", "OUTBOUND"]
    text: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)
    sent_at: datetime


class Department(ApiModel):
    id: int = Field(gt=0)
    # Mesmos limites do cadastro de setores no backend.
    name: str = Field(min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=500)


class ReplyRequest(ApiModel):
    conversation_id: str = Field(min_length=1, max_length=64)
    channel_id: int = Field(gt=0)
    inbound_message_id: str = Field(min_length=1, max_length=128)
    message: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)
    history: list[HistoryMessage] = Field(default_factory=list, max_length=200)
    departments: list[Department] = Field(default_factory=list, max_length=100)


class RunTelemetry(ApiModel):
    model: str
    input_tokens: int = Field(ge=0)
    output_tokens: int = Field(ge=0)
    cached_input_tokens: int = Field(ge=0)
    cache_write_input_tokens: int = Field(default=0, ge=0)
    history_messages: int = Field(default=0, ge=0)
    latency_ms: int = Field(ge=0)
    request_id: str | None = Field(default=None, max_length=200)
    # Quem produziu a decisão: modelo principal, secundário, encaminhamento fixo ou bloqueio na entrada.
    fallback_stage: Literal["primary", "secondary", "fixed", "blocked"] = "primary"
    guardrails: list[str] = Field(default_factory=list)
    failures: list[str] = Field(default_factory=list)
    # Ferramentas chamadas e trechos da base usados (RAG), para auditar de onde veio a resposta.
    tool_calls: list[str] = Field(default_factory=list)
    retrieved: list[str] = Field(default_factory=list)


class ReplyResponse(ApiModel):
    reply: str = Field(min_length=1, max_length=MAX_REPLY_LENGTH)
    intent: str = Field(min_length=1, max_length=50)
    needs_human: bool
    department_id: int | None
    handoff_reason: str | None
    run: RunTelemetry
