#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_PROJECT="ledgerbank"
APP_BASE_URL="${LEDGERBANK_BASE_URL:-http://localhost:${PORT:-8080}}"
INFRASTRUCTURE_ONLY=false

if [[ "${1:-}" == "--infrastructure-only" ]]; then
  INFRASTRUCTURE_ONLY=true
elif [[ -n "${1:-}" ]]; then
  echo "Usage: ${0##*/} [--infrastructure-only]" >&2
  exit 2
fi

cd "${REPO_ROOT}"

for command_name in docker; do
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "${command_name} is required for the LedgerBank health check." >&2
    exit 1
  fi
done
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (docker compose)." >&2
  exit 1
fi

for service in postgres redis rabbitmq; do
  container_id="$(docker compose --project-name "${COMPOSE_PROJECT}" ps -q "${service}")"
  if [[ -z "${container_id}" ]]; then
    echo "Dependency ${service} is not running." >&2
    exit 1
  fi
  health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${container_id}")"
  if [[ "${health_status}" != "healthy" ]]; then
    echo "Dependency ${service} is not healthy (status: ${health_status})." >&2
    exit 1
  fi
done

if [[ "${INFRASTRUCTURE_ONLY}" == "true" ]]; then
  echo "LedgerBank infrastructure is healthy."
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required to check the LedgerBank application." >&2
  exit 1
fi
if ! curl --fail --silent --show-error --max-time 5 +  "${APP_BASE_URL%/}/actuator/health/liveness" >/dev/null; then
  echo "LedgerBank application liveness check failed at ${APP_BASE_URL%/}." >&2
  exit 1
fi

echo "LedgerBank application and infrastructure are healthy."
