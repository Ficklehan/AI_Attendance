#!/usr/bin/env bash
# 切回本地开发：停 8080 → 以 dev profile 启动后端（不加载 deploy/rendered/*.env）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

echo ">>> 本地开发模式"
echo "    推荐: production.yaml → runtime.mode: local  然后  ./start.sh apply"
echo "    API:  http://localhost:8080/attendance/api"
echo "    PC 前端: http://localhost:5175/attendance/"
echo ""

PIDS=$(lsof -ti :8080 2>/dev/null || true)
if [ -n "$PIDS" ]; then
  echo ">>> 停止占用 8080 的进程..."
  kill $PIDS 2>/dev/null || true
  sleep 2
  kill -9 $PIDS 2>/dev/null || true
fi

# shellcheck source=/dev/null
source "$PROJECT_DIR/scripts/env-jdk8.sh"
cd "$PROJECT_DIR/backend"
exec "$PROJECT_DIR/scripts/mvn-jdk8.sh" spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -DskipTests
