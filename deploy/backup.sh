#!/usr/bin/env bash
#
# Dump the Postgres database to ./backups, keeping the last 7 days.
# Wire into cron on the VPS:
#
#   crontab -e
#   15 3 * * * /opt/sniplink/backup.sh >> /opt/sniplink/backups/backup.log 2>&1

set -euo pipefail

STACK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$STACK_DIR"

BACKUP_DIR="$STACK_DIR/backups"
RETAIN_DAYS=7

# shellcheck disable=SC1091
set -a && . ./.env && set +a

mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/sniplink-$STAMP.sql.gz"

# Write to a temp name first so a dump interrupted halfway never looks like a
# complete backup to the retention sweep below.
docker compose exec -T postgres \
	pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" |
	gzip >"$OUT.partial"
mv "$OUT.partial" "$OUT"

echo "$(date -Is)  wrote $OUT ($(du -h "$OUT" | cut -f1))"

find "$BACKUP_DIR" -name 'sniplink-*.sql.gz' -mtime +"$RETAIN_DAYS" -delete
find "$BACKUP_DIR" -name '*.partial' -mtime +1 -delete
