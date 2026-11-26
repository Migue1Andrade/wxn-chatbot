"""Deliver one OpenBot support response to the local WXN backend."""
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

payload = json.load(sys.stdin)
if not isinstance(payload.get("requestId"), str) or not re.fullmatch(
    r"[a-zA-Z0-9-]{1,80}", payload["requestId"]
):
    raise SystemExit("Identificador inválido.")
if not isinstance(payload.get("handoff"), bool) or not isinstance(payload.get("reply"), str):
    raise SystemExit("Resposta inválida.")

fields = ("requestId", "reply", "handoff")
if "askResolution" in payload:
    if not isinstance(payload["askResolution"], bool):
        raise SystemExit("Confirmação de resolução inválida.")
    fields += ("askResolution",)

env_file = Path(__file__).resolve().parent.parent / ".env"
token = next(
    (line.partition("=")[2].strip() for line in env_file.read_text().splitlines()
     if line.startswith("OPENBOT_SUPPORT_SERVICE_TOKEN=")),
    "",
)
if not token:
    raise SystemExit("Token de retorno indisponível.")
request = urllib.request.Request(
    "http://127.0.0.1:4001/api/openbot/support/replies",
    json.dumps({key: payload[key] for key in fields}).encode(),
    {"Content-Type": "application/json", "X-OpenBot-Service-Token": token},
)
try:
    with urllib.request.urlopen(request, timeout=10) as response:
        if response.status != 204:
            raise SystemExit(f"Retorno inesperado: HTTP {response.status}")
        print("Callback HTTP 204")
except urllib.error.HTTPError as error:
    raise SystemExit(f"Callback HTTP {error.code}") from None
