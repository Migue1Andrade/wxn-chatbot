#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo "Arquivo .env não encontrado." >&2
  exit 1
fi

set -a
source .env
set +a

: "${EVOLUTION_API_URL:?EVOLUTION_API_URL não configurada}"
: "${EVOLUTION_API_KEY:?EVOLUTION_API_KEY não configurada}"
: "${EVOLUTION_INSTANCE_NAME:?EVOLUTION_INSTANCE_NAME não configurada}"

curl --fail-with-body -sS -i \
  -H "apikey: $EVOLUTION_API_KEY" \
  "$EVOLUTION_API_URL/instance/fetchInstances?instanceName=$EVOLUTION_INSTANCE_NAME"
