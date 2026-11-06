#!/usr/bin/env bash
set -euo pipefail

if ! command -v ngrok >/dev/null 2>&1; then
  echo "ngrok não encontrado no PATH." >&2
  exit 1
fi

echo "Copie a URL HTTPS exibida e configure EVOLUTION_WEBHOOK_URL no .env."
echo "URL esperada: https://.../api/webhooks/evolution"
exec ngrok http 4001
