#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

if [[ ! -x ./mvnw ]]; then
  echo "The Maven Wrapper is missing or not executable: ${REPO_ROOT}/mvnw" >&2
  exit 1
fi

exec ./mvnw test "$@"
