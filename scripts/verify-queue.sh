#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
set -a
source .env
set +a

npm run lint

# Separate containers and volumes keep fixtures away from development data.
export POSTGRES_PORT=14003 MONGO_PORT=14004
export POSTGRES_VOLUME=wxn-queue-review_postgres_data
export MONGO_VOLUME=wxn-queue-review_mongo_data
export DATABASE_VOLUMES_EXTERNAL=false
export POSTGRES_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}"
export MONGODB_URI="mongodb://127.0.0.1:${MONGO_PORT}/${MONGO_DB}"
export WXN_INTEGRATION_TEST=true

compose() {
  docker compose -p wxn-queue-review "$@"
}
cleanup() {
  compose stop
}
trap cleanup EXIT
compose config --quiet
compose up -d
ready=false
for attempt in {1..30}; do
  if compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1 &&
    compose exec -T mongodb mongosh --quiet --eval 'if (db.runCommand({ping: 1}).ok !== 1) quit(1)' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo 'Os bancos de teste não ficaram disponíveis.' >&2
  exit 1
fi

maven_args=()
if [[ -n "${MOCKITO_AGENT_JAR:-}" ]]; then
  maven_args+=("-DargLine=-javaagent:${MOCKITO_AGENT_JAR}")
fi
mvn "${maven_args[@]}" clean verify
npm --prefix frontend test
npm --prefix frontend run build
git diff --check
