#!/usr/bin/env bash
# Restores a backup made by backup.sh into the running Docker stack.
#   ./scripts/restore.sh backups/meridian-20260920-030000.sql.gz
# This REPLACES the current data. Stop the backend first so nothing writes while it runs:
#   docker compose stop backend && ./scripts/restore.sh <file> && docker compose start backend
# Add --yes to skip the confirmation question.
set -euo pipefail
cd "$(dirname "$0")/.."

file="${1:-}"
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "Usage: $0 <backup.sql.gz> [--yes]" >&2
  exit 2
fi
gzip -t "$file" || { echo "$file is not a valid gzip file." >&2; exit 1; }

if [ "${2:-}" != "--yes" ]; then
  read -r -p "This replaces ALL current Meridian data with $file. Continue? [y/N] " answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "Cancelled."; exit 1; }
fi

gunzip -c "$file" | docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"'
echo "Restored $file"
