#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo "Arquivo .env não encontrado. Copie .env.example para .env primeiro." >&2
  exit 1
fi

set -a
source .env
set +a
exec mvn spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-dev}"
