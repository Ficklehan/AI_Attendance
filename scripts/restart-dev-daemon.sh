#!/usr/bin/env bash
# 本地前后端重启：先停止 → 再启动
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo ">>> [1/2] 停止前后端..."
bash "$PROJECT_DIR/scripts/stop-dev-daemon.sh"

echo ""
echo ">>> [2/2] 启动前后端..."
bash "$PROJECT_DIR/scripts/start-dev-daemon.sh"
