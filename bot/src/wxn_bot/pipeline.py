"""Caminho de uma resposta: guardrails de entrada, cadeia de modelos (fallback) e guardrails de saída.

O cliente sempre recebe uma resposta segura. Se o modelo principal falhar ou produzir uma resposta
reprovada, o secundário tenta; se nenhum servir, a decisão é encaminhar para um humano com uma mensagem fixa.

Tentativa de manipulação não vai para um humano: o bot recusa e segue disponível. Encaminhar daria a quem
tenta manipular um atendente na hora e permitiria encher a fila. A recusa lembra que o cliente pode pedir uma
pessoa, para o caso de falso positivo.
"""

import logging
from dataclasses import dataclass, field
from typing import Literal

from wxn_bot.contract import ReplyRequest
from wxn_bot.guardrails import PROMPT_INJECTION, input_violation, output_violation
from wxn_bot.responder import BotDecision, Generation, Responder, ResponderError

logger = logging.getLogger(__name__)

# Mesmo texto usado pelo backend quando o serviço do bot está fora do ar.
FALLBACK_MESSAGE = (
    "Vou encaminhar você para um de nossos atendentes. Em instantes alguém continua o atendimento."
)
REFUSAL_MESSAGE = (
    "Desculpe, não posso fazer isso. Posso ajudar com dúvidas sobre nossos serviços, pedidos e atendimento. "
    "Se preferir falar com uma pessoa, é só pedir."
)
NO_MODEL = "nenhum"

Stage = Literal["primary", "secondary", "fixed", "blocked"]
STAGES: tuple[Stage, Stage] = ("primary", "secondary")


@dataclass(frozen=True)
class Outcome:
    decision: BotDecision
    stage: Stage
    model: str = NO_MODEL
    guardrails: list[str] = field(default_factory=list)
    failures: list[str] = field(default_factory=list)
    input_tokens: int = 0
    output_tokens: int = 0
    cached_input_tokens: int = 0
    cache_write_input_tokens: int = 0
    history_messages: int = 0
    request_id: str | None = None
    tool_calls: list[str] = field(default_factory=list)
    retrieved: list[str] = field(default_factory=list)


class ReplyPipeline:
    def __init__(self, responder: Responder, models: list[str], max_input_chars: int) -> None:
        self._responder = responder
        self._models = models
        self._max_input_chars = max_input_chars

    def run(self, request: ReplyRequest) -> Outcome:
        blocked = input_violation(request.message, self._max_input_chars)
        if blocked is not None:
            # Barrado antes do modelo: nada é gasto.
            decision = refusal() if blocked == PROMPT_INJECTION else handoff(blocked)
            return Outcome(decision=decision, stage="blocked", guardrails=[blocked])

        guardrails: list[str] = []
        failures: list[str] = []
        generations: list[Generation] = []
        for stage, model in zip(STAGES, self._models, strict=False):
            try:
                generation = self._responder.respond(request, model)
            except ResponderError as error:
                logger.warning("Falha do modelo %s (%s): %s", model, stage, error)
                failures.append(f"{stage}:{error.code}")
                continue
            generations.append(generation)
            if generation.decision.manipulation_attempt:
                guardrails.append(PROMPT_INJECTION)
                return totals(generations, refusal(), stage, guardrails, failures)
            violation = output_violation(generation.decision.reply)
            if violation is not None:
                guardrails.append(violation)
                failures.append(f"{stage}:{violation}")
                continue
            return totals(generations, generation.decision, stage, guardrails, failures)

        return totals(generations, handoff("model_unavailable"), "fixed", guardrails, failures)


def handoff(reason: str) -> BotDecision:
    """Decisão fixa de encaminhamento, sem setor: o atendente faz a triagem."""
    reasons = {
        "input_too_long": "Mensagem longa demais para o atendimento automático.",
        "model_unavailable": "O bot não conseguiu produzir uma resposta segura.",
    }
    return BotDecision(
        reply=FALLBACK_MESSAGE,
        intent="encaminhamento_automatico",
        needs_human=True,
        department_id=None,
        handoff_reason=reasons.get(reason, reason),
        manipulation_attempt=False,
    )


def refusal() -> BotDecision:
    """Recusa educada a uma tentativa de manipulação; a conversa continua com o bot."""
    return BotDecision(
        reply=REFUSAL_MESSAGE,
        intent="tentativa_de_manipulacao",
        needs_human=False,
        department_id=None,
        handoff_reason=None,
        manipulation_attempt=True,
    )


# Soma o uso de todas as tentativas: as que foram reprovadas também foram cobradas.
def totals(
    generations: list[Generation],
    decision: BotDecision,
    stage: Stage,
    guardrails: list[str],
    failures: list[str],
) -> Outcome:
    last = generations[-1] if generations else None
    return Outcome(
        decision=decision,
        stage=stage,
        model=last.model if last else NO_MODEL,
        guardrails=guardrails,
        failures=failures,
        input_tokens=sum(g.input_tokens for g in generations),
        output_tokens=sum(g.output_tokens for g in generations),
        cached_input_tokens=sum(g.cached_input_tokens for g in generations),
        cache_write_input_tokens=sum(g.cache_write_input_tokens for g in generations),
        history_messages=last.history_messages if last else 0,
        request_id=last.request_id if last else None,
        tool_calls=[call for g in generations for call in g.tool_calls],
        retrieved=last.retrieved if last else [],
    )
