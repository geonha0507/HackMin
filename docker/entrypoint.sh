#!/usr/bin/env bash
set -e

if [ "$DB_ENGINE" = "mysql" ]; then
  echo "Waiting for MySQL at ${DB_HOST:-db}:${DB_PORT:-3306}..."
  until nc -z "${DB_HOST:-db}" "${DB_PORT:-3306}"; do
    sleep 1
  done
  echo "MySQL is up."
fi

if [ "$RUN_MIGRATIONS" = "1" ]; then
  echo "Running migrations..."
  python manage.py migrate --noinput
fi

if [ "$SEED_DEMO" = "1" ]; then
  echo "Seeding demo data..."
  python manage.py seed_demo || true
fi

exec "$@"
