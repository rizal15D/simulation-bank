#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required by the Testcontainers integration suite." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but its daemon is not reachable." >&2
  exit 1
fi
if [[ ! -x ./mvnw ]]; then
  echo "The Maven Wrapper is missing or not executable: ${REPO_ROOT}/mvnw" >&2
  exit 1
fi

exec ./mvnw verify -Pintegration "$@"
