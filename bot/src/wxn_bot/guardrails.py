"""Guardrails: verificações fora do modelo, antes e depois da chamada.

Entrada (antes de chamar o modelo, sem custo): mensagem longa demais ou com padrões conhecidos de tentativa de
manipulação ("ignore as instruções", "prompt do sistema"...). A detecção por padrões é a primeira barreira; o
próprio modelo sinaliza tentativas que os padrões não pegam (`manipulation_attempt`).

Saída (depois do modelo): resposta vazia, acima do limite do WhatsApp ou com dado sensível (CPF ou número de
cartão válidos). Uma resposta reprovada não chega ao cliente: aciona o próximo passo do fallback.
"""

import re

from wxn_bot.contract import MAX_REPLY_LENGTH

INPUT_TOO_LONG = "input_too_long"
PROMPT_INJECTION = "prompt_injection"
EMPTY_REPLY = "empty_reply"
REPLY_TOO_LONG = "reply_too_long"
SENSITIVE_DATA = "sensitive_data"

INJECTION_PATTERNS = [
    re.compile(pattern, re.IGNORECASE)
    for pattern in (
        r"\bignor\w*\s+(?:\w+\s+){0,3}(?:instru[çc][õo]es|regras|orienta[çc][õo]es)",
        r"\bignore\s+(?:\w+\s+){0,3}(?:instructions|rules)",
        r"\b(?:prompt|instru[çc][õo]es)\s+(?:do|de)\s+sistema",
        r"\bsystem\s+prompt",
        r"\b(?:voc[êe]\s+agora\s+[ée]|a\s+partir\s+de\s+agora\s+voc[êe]\s+[ée])",
        r"\b(?:modo\s+desenvolvedor|developer\s+mode|jailbreak)",
    )
]

CPF_PATTERN = re.compile(r"(?<!\d)(\d{3})\.?(\d{3})\.?(\d{3})-?(\d{2})(?!\d)")
CARD_PATTERN = re.compile(r"(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)")


def input_violation(message: str, max_chars: int) -> str | None:
    if len(message) > max_chars:
        return INPUT_TOO_LONG
    if any(pattern.search(message) for pattern in INJECTION_PATTERNS):
        return PROMPT_INJECTION
    return None


def output_violation(reply: str) -> str | None:
    if not reply.strip():
        return EMPTY_REPLY
    if len(reply) > MAX_REPLY_LENGTH:
        return REPLY_TOO_LONG
    if contains_cpf(reply) or contains_card_number(reply):
        return SENSITIVE_DATA
    return None


def contains_cpf(text: str) -> bool:
    return any(valid_cpf("".join(match.groups())) for match in CPF_PATTERN.finditer(text))


def contains_card_number(text: str) -> bool:
    for match in CARD_PATTERN.finditer(text):
        digits = re.sub(r"\D", "", match.group())
        if 13 <= len(digits) <= 19 and luhn_valid(digits):
            return True
    return False


# Dígitos verificadores do CPF: evitam confundir telefones ou números de pedido com CPF.
def valid_cpf(digits: str) -> bool:
    if len(digits) != 11 or digits == digits[0] * 11:
        return False
    for size in (9, 10):
        weights = range(size + 1, 1, -1)
        total = sum(int(digit) * weight for digit, weight in zip(digits[:size], weights, strict=True))
        check = (total * 10) % 11 % 10
        if check != int(digits[size]):
            return False
    return True


# Algoritmo de Luhn, usado pelas bandeiras de cartão.
def luhn_valid(digits: str) -> bool:
    total = 0
    for index, digit in enumerate(reversed(digits)):
        value = int(digit)
        if index % 2 == 1:
            value *= 2
            if value > 9:
                value -= 9
        total += value
    return total % 10 == 0
