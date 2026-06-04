#!/usr/bin/env bash
# 从 deploy/environments/*.yaml 渲染并加载环境变量（启动前调用，改域名后重启即可生效）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_ENV="${ATTENDANCE_DEPLOY_ENV:-production}"

if ! command -v node &>/dev/null; then
  echo "错误: 需要 Node.js 执行 render-deploy-config.mjs" >&2
  exit 1
fi

echo ">>> 渲染部署配置 (${DEPLOY_ENV})..."
node "$PROJECT_DIR/scripts/render-deploy-config.mjs" --env "$DEPLOY_ENV"

RENDERED_ENV="$PROJECT_DIR/deploy/rendered/${DEPLOY_ENV}.env"
if [ ! -f "$RENDERED_ENV" ]; then
  echo "错误: 未找到 $RENDERED_ENV" >&2
  exit 1
fi

set -a
if [ -f "$PROJECT_DIR/deploy/secrets.env" ]; then
  # 服务器密钥（推荐，勿提交 Git）
  # shellcheck source=/dev/null
  source "$PROJECT_DIR/deploy/secrets.env"
elif [ -f "$PROJECT_DIR/backend/.env" ]; then
  # 本地/兼容：与 rendered env 合并，URL 类变量以 rendered 为准
  # shellcheck source=/dev/null
  source "$PROJECT_DIR/backend/.env"
fi
# shellcheck source=/dev/null
source "$RENDERED_ENV"
set +a

echo ">>> 已加载: ${RENDERED_ENV}"
echo "    PUBLIC_HOST=${PUBLIC_HOST:-?}"
echo "    SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-?}"
