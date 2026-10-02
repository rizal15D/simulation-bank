#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_PROJECT="ledgerbank"
DATABASE_NAME="ledgerbank"
POSTGRES_VOLUME="ledgerbank_postgres_data"
ACTIVE_ENVIRONMENT="${LEDGERBANK_ENVIRONMENT:-${SPRING_PROFILES_ACTIVE:-local}}"
ASSUME_YES=false

case "${1:-}" in
  "")
    ;;
  --yes)
    ASSUME_YES=true
    ;;
  --help)
    echo "Usage: ${0##*/} [--yes]"
    echo "Resets only the local LedgerBank PostgreSQL development volume."
    exit 0
    ;;
  *)
    echo "Usage: ${0##*/} [--yes]" >&2
    exit 2
    ;;
esac

case "${ACTIVE_ENVIRONMENT}" in
  local|development)
    ;;
  *)
    echo "Refusing database reset for environment '${ACTIVE_ENVIRONMENT}'." >&2
    echo "Set LEDGERBANK_ENVIRONMENT to local or development only." >&2
    exit 1
    ;;
esac

cd "${REPO_ROOT}"

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker with Compose v2 is required." >&2
  exit 1
fi

echo "LedgerBank local database reset target:"
echo "  repository: ${REPO_ROOT}"
echo "  environment: ${ACTIVE_ENVIRONMENT}"
echo "  compose project: ${COMPOSE_PROJECT}"
echo "  database: ${DATABASE_NAME}"
echo "  volume: ${POSTGRES_VOLUME}"
echo "Redis and RabbitMQ volumes will not be changed."

if [[ "${ASSUME_YES}" != "true" ]]; then
  expected="RESET ${COMPOSE_PROJECT}/${DATABASE_NAME}"
  read -r -p "Type '${expected}' to continue: " confirmation
  if [[ "${confirmation}" != "${expected}" ]]; then
    echo "Database reset cancelled."
    exit 1
  fi
fi

if docker volume inspect "${POSTGRES_VOLUME}" >/dev/null 2>&1; then
  volume_project="$(docker volume inspect --format '{{ index .Labels "com.docker.compose.project" }}' "${POSTGRES_VOLUME}")"
  volume_key="$(docker volume inspect --format '{{ index .Labels "com.docker.compose.volume" }}' "${POSTGRES_VOLUME}")"
  if [[ "${volume_project}" != "${COMPOSE_PROJECT}" || "${volume_key}" != "postgres_data" ]]; then
    echo "Refusing to remove volume whose Compose labels do not match LedgerBank PostgreSQL." >&2
    exit 1
  fi
fi

docker compose --project-name "${COMPOSE_PROJECT}" stop postgres
docker compose --project-name "${COMPOSE_PROJECT}" rm --force --stop postgres
if docker volume inspect "${POSTGRES_VOLUME}" >/dev/null 2>&1; then
  docker volume rm "${POSTGRES_VOLUME}"
fi
docker compose --project-name "${COMPOSE_PROJECT}" up -d --wait postgres

echo "LedgerBank local PostgreSQL database has been recreated."
