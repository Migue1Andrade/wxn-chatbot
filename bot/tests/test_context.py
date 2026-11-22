from datetime import UTC, datetime

from wxn_bot.context import build_context, build_system_prompt, estimate_tokens, recent_history
from wxn_bot.contract import Department, HistoryMessage, ReplyRequest

SENT_AT = datetime(2026, 11, 21, 10, 0, tzinfo=UTC)


def message(direction: str, text: str) -> HistoryMessage:
    return HistoryMessage(direction=direction, text=text, sent_at=SENT_AT)


def reply_request(history: list[HistoryMessage], departments: list[Department] | None = None) -> ReplyRequest:
    return ReplyRequest(
        conversation_id="c-1",
        channel_id=1,
        inbound_message_id="m-3",
        message="E o boleto vence quando?",
        history=history,
        departments=departments if departments is not None else [Department(id=3, name="Financeiro")],
    )


def test_context_keeps_stable_prefix_then_history_then_current_message() -> None:
    history = [message("INBOUND", "Meu nome é Ana, CPF final 123."), message("OUTBOUND", "Olá, Ana!")]

    context = build_context(reply_request(history), max_history_tokens=4000)

    assert context.system[0]["cache_control"] == {"type": "ephemeral"}
    assert "id 3: Financeiro" in context.system[0]["text"]
    assert [m["role"] for m in context.messages] == ["user", "assistant", "user"]
    assert context.messages[0]["content"] == "Meu nome é Ana, CPF final 123."
    current = context.messages[-1]["content"][0]
    assert current["text"] == "E o boleto vence quando?"
    assert current["cache_control"] == {"type": "ephemeral"}
    assert context.history_messages == 2


def test_system_prompt_does_not_change_with_the_conversation() -> None:
    first = build_context(reply_request([]), max_history_tokens=4000)
    later = build_context(reply_request([message("INBOUND", "Oi")]), max_history_tokens=4000)

    assert first.system == later.system


def test_history_drops_oldest_messages_first_when_over_the_limit() -> None:
    old = message("INBOUND", "a" * 300)
    recent = [message("OUTBOUND", "b" * 30), message("INBOUND", "c" * 30)]
    limit = sum(estimate_tokens(m.text) for m in recent)

    kept = recent_history([old, *recent], max_tokens=limit)

    assert kept == [recent[1]]


def test_history_always_starts_with_a_customer_message() -> None:
    history = [message("OUTBOUND", "Bom dia!"), message("INBOUND", "Oi"), message("OUTBOUND", "Como ajudo?")]

    kept = recent_history(history, max_tokens=4000)

    assert [m.direction for m in kept] == ["INBOUND", "OUTBOUND"]


def test_zero_budget_sends_only_the_current_message() -> None:
    context = build_context(reply_request([message("INBOUND", "Oi")]), max_history_tokens=0)

    assert len(context.messages) == 1
    assert context.history_messages == 0


def test_system_prompt_says_when_there_are_no_departments() -> None:
    assert "Nenhum setor disponível" in build_system_prompt([])
