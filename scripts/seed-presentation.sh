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

if [[ "${SPRING_PROFILES_ACTIVE:-}" == *prod* || "${SPRING_PROFILES_ACTIVE:-}" == *staging* ]]; then
  echo "Seed de apresentação recusado em produção/staging." >&2
  exit 1
fi

: "${DEV_API_KEY:?DEV_API_KEY não configurada}"
: "${WXN_SEED_USERNAME:?WXN_SEED_USERNAME não configurada}"
: "${WXN_SEED_PASSWORD:?WXN_SEED_PASSWORD não configurada}"
: "${WXN_DEMO_PASSWORD:?WXN_DEMO_PASSWORD não configurada}"

node scripts/seed-local-users.mjs
node scripts/seed-demo.mjs --verify-idempotency
