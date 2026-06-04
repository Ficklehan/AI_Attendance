#!/usr/bin/env bash
# 改 deploy/environments/production.yaml 后执行本脚本：停旧进程 → render → 启动
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo ">>> 停止占用 8080 的后端..."
PIDS=$(lsof -ti :8080 2>/dev/null || true)
if [ -n "$PIDS" ]; then
  kill $PIDS 2>/dev/null || true
  sleep 2
  kill -9 $PIDS 2>/dev/null || true
fi

exec "$PROJECT_DIR/scripts/start-backend-prod.sh"
