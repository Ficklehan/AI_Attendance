#!/usr/bin/env bash
# 切回本地开发：停 8080 → 以 dev profile 启动后端（不加载 deploy/rendered/*.env）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

echo ">>> 本地开发模式"
echo "    推荐: production.yaml → runtime.mode: local  然后  ./start.sh apply"
echo ""

if command -v node &>/dev/null; then
  echo ">>> 同步小程序 config.runtime.js（与 production.yaml 一致）..."
  node "$PROJECT_DIR/scripts/render-deploy-config.mjs" --env production
  if [ -f "$PROJECT_DIR/deploy/rendered/production.env" ]; then
    # shellcheck source=/dev/null
    source "$PROJECT_DIR/deploy/rendered/production.env"
    if [ "${RUNTIME_MODE:-}" = "public" ]; then
      echo "    警告: production.yaml 为 runtime.mode=public，小程序将走公网 API"
      echo "    若需纯本地，请改 yaml 为 mode: local 后执行 ./start.sh apply"
    fi
    echo "    小程序 API: ${PUBLIC_BASE_URL:-${LOCAL_API_BASE_URL:-http://localhost:8080/clockai/api}}"
  fi
  echo "    PC 前端: http://localhost:5175/clockai/"
  echo ""
fi

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
