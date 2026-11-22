from types import SimpleNamespace

import anthropic
import httpx2
import pytest
from anthropic.types import Usage
from pydantic import ValidationError

from wxn_bot.contract import ReplyRequest
from wxn_bot.knowledge import SearchResult
from wxn_bot.responder import (
    BotDecision,
    ClaudeResponder,
    ResponderError,
    generation_from,
)


def decision() -> BotDecision:
    return BotDecision(
        reply="Olá!",
        intent="saudacao",
        needs_human=False,
        department_id=None,
        handoff_reason=None,
        manipulation_attempt=False,
    )


def reply_request() -> ReplyRequest:
    return ReplyRequest.model_validate(
        {
            "conversationId": "c-1",
            "channelId": 1,
            "inboundMessageId": "m-1",
            "message": "Quero falar com o financeiro",
            "departments": [{"id": 3, "name": "Financeiro", "description": "Pagamentos e boletos"}],
        }
    )


def usage(**fields: int) -> Usage:
    values = {"input_tokens": 300, "output_tokens": 40}
    values.update(fields)
    return Usage(**values)


class FakeMessages:
    """Devolve as respostas em sequência, uma por chamada ao modelo."""

    def __init__(self, response: object = None, error: Exception | None = None, responses=None) -> None:
        self.responses = list(responses) if responses else [response]
        self.error = error
        self.calls: list[dict] = []

    def parse(self, **kwargs: object) -> object:
        self.calls.append({**kwargs, "messages": list(kwargs["messages"])})
        if self.error is not None:
            raise self.error
        return self.responses[min(len(self.calls), len(self.responses)) - 1]


class FakeKnowledge:
    def __init__(self, results: list[SearchResult] | None = None, error: Exception | None = None) -> None:
        self.results = results or []
        self.error = error
        self.queries: list[str] = []

    def search(self, query: str) -> list[SearchResult]:
        self.queries.append(query)
        if self.error is not None:
            raise self.error
        return self.results


def responder_with(messages: FakeMessages, knowledge=None, max_tool_steps: int = 3) -> ClaudeResponder:
    return ClaudeResponder(
        SimpleNamespace(messages=messages),
        max_history_tokens=4000,
        knowledge=knowledge or FakeKnowledge(),
        max_tool_steps=max_tool_steps,
    )


def block(**fields: object) -> SimpleNamespace:
    return SimpleNamespace(**fields, to_dict=lambda: {**fields, "parsed_output": None})


def parsed_response(
    stop_reason: str = "end_turn", parsed: BotDecision | None = None, content=None
) -> SimpleNamespace:
    return SimpleNamespace(
        stop_reason=stop_reason,
        parsed_output=parsed,
        content=content or [],
        model="test-model",
        usage=usage(),
        _request_id="req_test123",
    )


def tool_call(query: object, tool_id: str = "toolu_1", name: str = "buscar_conhecimento") -> SimpleNamespace:
    thinking = block(type="thinking", thinking="", signature="sig")
    use = block(type="tool_use", id=tool_id, name=name, input={"consulta": query})
    return parsed_response(stop_reason="tool_use", content=[thinking, use])


DELIVERY = SearchResult(
    source="entregas.md",
    title="Entregas > Dias e horários",
    content="Entregas de segunda a sábado.",
    score=0.03,
)


def handoff(department_id: int | None) -> BotDecision:
    return BotDecision(
        reply="Vou encaminhar você.",
        intent="falar_com_humano",
        needs_human=True,
        department_id=department_id,
        handoff_reason="Cliente pediu um atendente.",
        manipulation_attempt=False,
    )


def test_responder_sends_structured_request_and_returns_decision() -> None:
    messages = FakeMessages(response=parsed_response(parsed=decision()))

    generation = responder_with(messages).respond(reply_request(), "test-model")

    assert generation.decision == decision()
    assert generation.model == "test-model"
    assert generation.request_id == "req_test123"
    call = messages.calls[0]
    assert call["model"] == "test-model"
    assert call["output_format"] is BotDecision
    assert "id 3: Financeiro. Pagamentos e boletos" in call["system"][0]["text"]
    assert call["messages"][-1]["content"][0]["text"] == "Quero falar com o financeiro"


@pytest.mark.parametrize("stop_reason", ["refusal", "max_tokens"])
def test_responder_rejects_unfinished_responses(stop_reason: str) -> None:
    messages = FakeMessages(response=parsed_response(stop_reason=stop_reason, parsed=None))

    with pytest.raises(ResponderError):
        responder_with(messages).respond(reply_request(), "test-model")


def test_responder_wraps_api_errors() -> None:
    request = httpx2.Request("POST", "https://api.anthropic.com/v1/messages")
    messages = FakeMessages(error=anthropic.APITimeoutError(request=request))

    with pytest.raises(ResponderError):
        responder_with(messages).respond(reply_request(), "test-model")


def test_responder_wraps_invalid_decisions() -> None:
    with pytest.raises(ValidationError) as invalid:
        BotDecision.model_validate_json("{}")
    messages = FakeMessages(error=invalid.value)

    with pytest.raises(ResponderError):
        responder_with(messages).respond(reply_request(), "test-model")


def test_generation_counts_cached_input_as_part_of_total_input() -> None:
    generation = generation_from(
        decision(),
        "test-model",
        [usage(cache_read_input_tokens=1024, cache_creation_input_tokens=100)],
    )

    assert generation.input_tokens == 300 + 1024 + 100
    assert generation.output_tokens == 40
    assert generation.cached_input_tokens == 1024


def test_generation_treats_missing_cache_usage_as_zero() -> None:
    generation = generation_from(decision(), "test-model", [usage()])

    assert generation.input_tokens == 300
    assert generation.cached_input_tokens == 0


def test_handoff_requires_reason() -> None:
    with pytest.raises(ValidationError):
        BotDecision(
            reply="Vou encaminhar.",
            intent="falar_com_humano",
            needs_human=True,
            department_id=3,
            handoff_reason=None,
            manipulation_attempt=False,
        )


def test_handoff_to_listed_department_is_accepted() -> None:
    messages = FakeMessages(response=parsed_response(parsed=handoff(3)))

    generation = responder_with(messages).respond(reply_request(), "test-model")

    assert generation.decision.department_id == 3


@pytest.mark.parametrize("department_id", [99, None])
def test_handoff_must_use_a_listed_department_when_there_are_departments(department_id: int | None) -> None:
    messages = FakeMessages(response=parsed_response(parsed=handoff(department_id)))

    with pytest.raises(ResponderError):
        responder_with(messages).respond(reply_request(), "test-model")


def test_handoff_without_department_is_accepted_when_none_is_available() -> None:
    request = reply_request().model_copy(update={"departments": []})
    messages = FakeMessages(response=parsed_response(parsed=handoff(None)))

    generation = responder_with(messages).respond(request, "test-model")

    assert generation.decision.needs_human is True
    assert generation.decision.department_id is None


def test_agent_searches_the_knowledge_base_and_answers_with_it() -> None:
    final = parsed_response(parsed=decision())
    messages = FakeMessages(responses=[tool_call("entregas sábado"), final])
    knowledge = FakeKnowledge([DELIVERY])

    generation = responder_with(messages, knowledge).respond(reply_request(), "test-model")

    assert knowledge.queries == ["entregas sábado"]
    assert generation.tool_calls == ["buscar_conhecimento"]
    assert generation.retrieved == ["entregas.md | Entregas > Dias e horários"]
    assert generation.input_tokens == 600
    assert generation.output_tokens == 80
    assert messages.calls[0]["tools"][0]["name"] == "buscar_conhecimento"
    second = messages.calls[1]["messages"]
    assistant, tool_results = second[-2], second[-1]
    assert assistant["role"] == "assistant"
    assert [b["type"] for b in assistant["content"]] == ["thinking", "tool_use"]
    assert all("parsed_output" not in b for b in assistant["content"])
    result = tool_results["content"][0]
    assert result["tool_use_id"] == "toolu_1"
    assert result["is_error"] is False
    assert "Entregas de segunda a sábado." in result["content"]


def test_last_step_disables_tools_so_the_model_must_decide() -> None:
    messages = FakeMessages(responses=[tool_call("entregas"), parsed_response(parsed=decision())])

    responder_with(messages, FakeKnowledge([DELIVERY]), max_tool_steps=1).respond(
        reply_request(), "test-model"
    )

    assert messages.calls[0]["tool_choice"] == {"type": "auto"}
    assert messages.calls[1]["tool_choice"] == {"type": "none"}


def test_unavailable_knowledge_base_is_reported_to_the_model_as_a_tool_error() -> None:
    messages = FakeMessages(responses=[tool_call("entregas"), parsed_response(parsed=handoff(3))])

    generation = responder_with(messages, FakeKnowledge(error=OSError("db down"))).respond(
        reply_request(), "test-model"
    )

    result = messages.calls[1]["messages"][-1]["content"][0]
    assert result["is_error"] is True
    assert generation.tool_calls == ["buscar_conhecimento:erro"]
    assert generation.decision.needs_human is True


@pytest.mark.parametrize(
    ("query", "name", "call"),
    [
        (123, "buscar_conhecimento", "buscar_conhecimento:entrada_invalida"),
        ("x", "apagar_tudo", "apagar_tudo:desconhecida"),
    ],
)
def test_invalid_tool_calls_return_errors_without_running_anything(
    query: object, name: str, call: str
) -> None:
    knowledge = FakeKnowledge([DELIVERY])
    messages = FakeMessages(responses=[tool_call(query, name=name), parsed_response(parsed=decision())])

    generation = responder_with(messages, knowledge).respond(reply_request(), "test-model")

    assert knowledge.queries == []
    assert messages.calls[1]["messages"][-1]["content"][0]["is_error"] is True
    assert generation.tool_calls == [call]
