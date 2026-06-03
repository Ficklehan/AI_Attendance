#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT_DIR/scripts/env-jdk8.sh"

cd "$ROOT_DIR/backend"
exec mvn "$@"
