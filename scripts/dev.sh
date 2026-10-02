#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run LedgerBank development infrastructure." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required (docker compose)." >&2
  exit 1
fi
if [[ ! -x ./mvnw ]]; then
  echo "The Maven Wrapper is missing or not executable: ${REPO_ROOT}/mvnw" >&2
  exit 1
fi

echo "Starting PostgreSQL, Redis, and RabbitMQ for local development..."
docker compose up -d --wait postgres redis rabbitmq

echo "Infrastructure is healthy. Starting LedgerBank on the local profile..."
exec ./mvnw spring-boot:run "$@"
