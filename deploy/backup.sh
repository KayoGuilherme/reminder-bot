#!/usr/bin/env bash
# Dumps the database to ./backups/ and keeps the last 7 days.
# Run from the project directory (where docker-compose.yml and .env live), e.g. from cron:
#   0 4 * * * cd /home/ubuntu/bot && ./deploy/backup.sh >> backups/backup.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."
DB_USER=$(grep -E '^DB_USER=' .env | cut -d= -f2-)
POSTGRES_DB=$(grep -E '^POSTGRES_DB=' .env | cut -d= -f2- || true)
mkdir -p backups
out="backups/reminder-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose -f docker-compose.yml exec -T db \
  pg_dump -U "$DB_USER" "${POSTGRES_DB:-reminder}" | gzip > "$out"
find backups -name 'reminder-*.sql.gz' -mtime +7 -delete
echo "backup written: $out"
