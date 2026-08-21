#!/usr/bin/env bash
# 读取 deploy/environments/production.yaml → render 各端配置 → 按 runtime.mode 重启后端
# 唯一人工维护：deploy/environments/production.yaml（public.host + runtime.mode）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_ENV="${ATTENDANCE_DEPLOY_ENV:-production}"
RENDERED_ENV="$PROJECT_DIR/deploy/rendered/${DEPLOY_ENV}.env"

if ! command -v node &>/dev/null; then
  echo "错误: 需要 Node.js 执行 render-deploy-config.mjs" >&2
  exit 1
fi

echo ">>> 渲染站点配置 (${DEPLOY_ENV})..."
node "$PROJECT_DIR/scripts/render-deploy-config.mjs" --env "$DEPLOY_ENV"

if [ ! -f "$RENDERED_ENV" ]; then
  echo "错误: 未找到 $RENDERED_ENV" >&2
  exit 1
fi

# shellcheck source=/dev/null
source "$RENDERED_ENV"

RUNTIME_MODE="${RUNTIME_MODE:-public}"
ACTIVE_API="${ACTIVE_API_BASE_URL:-${PUBLIC_BASE_URL:-}}"
echo ""
echo ">>> 已同步生成文件（改 yaml 后必须执行本脚本才会更新）"
echo "    deploy/rendered/${DEPLOY_ENV}.env"
echo "    feishu-miniprogram/config.runtime.js"
echo "    feishu-miniprogram/config.prod.js  (公网参考地址，local 模式下不生效)"
echo ""
echo ">>> 当前模式: ${RUNTIME_MODE}"
echo "    生效 API: ${ACTIVE_API}"
echo "    公网参考: ${PUBLIC_BASE_URL:-?}"
echo "    小程序:   ${MINIPROGRAM_USE_PUBLIC_API:-?} (USE_PUBLIC_API)"
echo "    后端:     ${ACTIVE_BACKEND_PROFILE:-?}"
echo ""

if ! grep -q "RUNTIME_MODE: '${RUNTIME_MODE}'" "$PROJECT_DIR/feishu-miniprogram/config.runtime.js" 2>/dev/null; then
  echo "错误: config.runtime.js 未与 production.yaml 同步，请检查 render 是否成功" >&2
  exit 1
fi

echo ">>> 停止占用 8080 的后端..."
PIDS=$(lsof -ti :8080 2>/dev/null || true)
if [ -n "$PIDS" ]; then
  kill $PIDS 2>/dev/null || true
  sleep 2
  kill -9 $PIDS 2>/dev/null || true
fi

if [ "$RUNTIME_MODE" = "local" ]; then
  echo ">>> 本地开发模式：启动 Spring Boot (profile: dev)"
    echo "    PC 前端: http://localhost:5175/clockai/"
    echo "    小程序 API: ${LOCAL_API_BASE_URL:-http://localhost:8080/clockai/api}"
  echo ""
  # shellcheck source=/dev/null
  source "$PROJECT_DIR/scripts/env-jdk8.sh"
  cd "$PROJECT_DIR/backend"
  exec "$PROJECT_DIR/scripts/mvn-jdk8.sh" spring-boot:run \
    -Dspring-boot.run.profiles=dev \
    -DskipTests
fi

echo ">>> 公网模式：启动 Spring Boot (profile: ${SPRING_PROFILES_ACTIVE:-prod})"
exec "$PROJECT_DIR/scripts/start-backend-prod.sh"
