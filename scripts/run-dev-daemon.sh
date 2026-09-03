#!/usr/bin/env bash
# 本地前后端守护：先停止旧进程 → 再启动（同 restart-dev-daemon.sh）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$PROJECT_DIR/scripts/restart-dev-daemon.sh"
