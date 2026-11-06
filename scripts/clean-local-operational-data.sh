#!/usr/bin/env bash
set -euo pipefail

# Destructive by design: this script only runs after an explicit local-dev confirmation.
if [[ "${SPRING_PROFILES_ACTIVE:-}" == *prod* || "${SPRING_PROFILES_ACTIVE:-}" == *staging* ]]; then
  echo "Refusing cleanup: SPRING_PROFILES_ACTIVE indicates production or staging." >&2
  exit 1
fi

postgres_url="${WXN_POSTGRES_URL:-${POSTGRES_URL:-}}"
mongo_uri="${WXN_MONGODB_URI:-${MONGODB_URI:-}}"
backup_root="${WXN_BACKUP_DIR:-backups}"

if [[ -z "$postgres_url" || -z "$mongo_uri" ]]; then
  echo "Set WXN_POSTGRES_URL/POSTGRES_URL and WXN_MONGODB_URI/MONGODB_URI first." >&2
  exit 1
fi

case "$postgres_url" in
  jdbc:postgresql://localhost:*|jdbc:postgresql://127.0.0.1:*|postgresql://localhost:*|postgresql://127.0.0.1:*) ;;
  *) echo "Refusing cleanup: PostgreSQL URL is not local." >&2; exit 1 ;;
esac
case "$mongo_uri" in
  mongodb://localhost:*|mongodb://127.0.0.1:*|mongodb+srv://*)
    if [[ "$mongo_uri" == mongodb+srv://* ]]; then
      echo "Refusing cleanup: MongoDB SRV URI is not accepted as local." >&2
      exit 1
    fi
    ;;
  *) echo "Refusing cleanup: MongoDB URI is not local." >&2; exit 1 ;;
esac

if [[ "${CONFIRM_WXN_LOCAL_CLEANUP:-}" != "DELETE_OPERATIONAL_DATA" ]]; then
  echo "Dry-run protection is active. Set CONFIRM_WXN_LOCAL_CLEANUP=DELETE_OPERATIONAL_DATA to execute." >&2
  exit 1
fi

if ! command -v pg_dump >/dev/null || ! command -v psql >/dev/null || ! command -v mongodump >/dev/null || ! command -v mongosh >/dev/null; then
  echo "Required commands: pg_dump, psql, mongodump and mongosh." >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="$backup_root/wxn-cleanup-$timestamp"
mkdir -p "$backup_dir"

postgres_dsn="${postgres_url#jdbc:}"
echo "Creating PostgreSQL backup at $backup_dir/postgres.dump"
pg_dump --format=custom --file="$backup_dir/postgres.dump" "$postgres_dsn"
echo "Creating MongoDB backup at $backup_dir/mongo"
mongodump --uri="$mongo_uri" --out="$backup_dir/mongo" >/dev/null

echo "Counts before deletion:"
psql "$postgres_dsn" -v ON_ERROR_STOP=1 -c \
  "SELECT 'work_tasks' AS collection, count(*) FROM work_tasks UNION ALL SELECT 'user_notifications', count(*) FROM user_notifications UNION ALL SELECT 'demo_seed_manifest', count(*) FROM demo_seed_manifest;"
mongosh "$mongo_uri" --quiet --eval '
  const names = ["conversations", "messages", "message_logs", "conversation_events",
    "conversation_reads", "team_entries", "channel_runtime", "channel_operations",
    "message_operations", "webhook_inbox", "conversation_write_leases", "external_contacts"];
  for (const name of names) {
    const count = db.getCollection(name).countDocuments();
    print(`${name}: ${count}`);
  }
'

psql "$postgres_dsn" -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
DELETE FROM user_notifications;
DELETE FROM work_tasks;
DELETE FROM demo_seed_manifest;
DELETE FROM whatsapp_setup
WHERE channel_id IS NULL
   OR channel_id IN (SELECT id FROM whatsapp_channels WHERE provider <> 'EVOLUTION');
UPDATE whatsapp_channels
SET deleted_at = CURRENT_TIMESTAMP,
    status = 'INACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE provider <> 'EVOLUTION' AND deleted_at IS NULL;
COMMIT;
SQL

mongosh "$mongo_uri" --quiet --eval '
  const names = ["conversations", "messages", "message_logs", "conversation_events",
    "conversation_reads", "team_entries", "channel_runtime", "channel_operations",
    "message_operations", "webhook_inbox", "conversation_write_leases", "external_contacts"];
  for (const name of names) {
    if (db.getCollectionNames().includes(name)) db.getCollection(name).deleteMany({});
  }
'

echo "Cleanup completed. Configuration tables and MongoDB indexes were preserved."
echo "Backup: $backup_dir"
