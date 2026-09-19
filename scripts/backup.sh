#!/usr/bin/env bash
# Backs up the Meridian database from the running Docker stack to a gzipped SQL file.
#   ./scripts/backup.sh                      -> backups/meridian-<date>.sql.gz
#   ./scripts/backup.sh /path/to/file.sql.gz
# Safe to run while the site is up (a single consistent snapshot, no locking).
# To run it nightly, add a cron line such as:
#   0 3 * * *  cd /path/to/Meridian && ./scripts/backup.sh >> backups/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."

out="${1:-backups/meridian-$(date +%Y%m%d-%H%M%S).sql.gz}"
mkdir -p "$(dirname "$out")"
tmp="$out.partial"
trap 'rm -f "$tmp"' EXIT

# The password is passed through the environment, not the command line.
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --single-transaction --routines --no-tablespaces -uroot "$MYSQL_DATABASE"' \
  | gzip > "$tmp"

# Never keep an empty or truncated file that would look like a good backup.
if ! gzip -t "$tmp" || [ "$(gunzip -c "$tmp" | grep -c 'CREATE TABLE')" -lt 1 ]; then
  echo "Backup failed: the dump is empty or damaged. Is the stack running (docker compose ps)?" >&2
  exit 1
fi
mv "$tmp" "$out"
trap - EXIT
echo "Backup written to $out ($(du -h "$out" | cut -f1))"
