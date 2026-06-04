#!/usr/bin/env bash
# 生产/UAT 后端启动：自动 render + 加载 env + Spring Boot
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

# shellcheck source=/dev/null
source "$PROJECT_DIR/scripts/load-deploy-env.sh"

# shellcheck source=/dev/null
source "$PROJECT_DIR/scripts/env-jdk8.sh"

if ! command -v java &>/dev/null; then
  echo "错误: 未检测到 Java 环境" >&2
  exit 1
fi

PROFILE="${SPRING_PROFILES_ACTIVE:-prod}"
echo ">>> 启动后端 (profile: ${PROFILE})..."

cd "$PROJECT_DIR/backend"
exec "$PROJECT_DIR/scripts/mvn-jdk8.sh" spring-boot:run \
  -Dspring-boot.run.profiles="${PROFILE}" \
  -DskipTests
