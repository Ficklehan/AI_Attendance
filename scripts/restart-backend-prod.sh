#!/usr/bin/env bash
# 兼容入口：改 production.yaml 后请用 ./start.sh apply（会按 runtime.mode 选择 dev/prod）
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec "$PROJECT_DIR/scripts/apply-site-config.sh"
