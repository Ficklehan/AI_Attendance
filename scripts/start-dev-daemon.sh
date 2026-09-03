#!/usr/bin/env bash
# 本地前后端守护启动（不停止旧进程；重启请用 restart-dev-daemon.sh）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
BACKEND_LOG="$LOG_DIR/backend-dev.out"
FRONTEND_LOG="$LOG_DIR/frontend-dev.out"
BACKEND_PID_FILE="$LOG_DIR/backend-dev.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend-dev.pid"

mkdir -p "$LOG_DIR"

start_detached() {
  local pid_file="$1"
  local log_file="$2"
  local cmd="$3"
  local pid
  pid=$(/bin/bash -c 'nohup /bin/bash -lc "$1" >> "$2" 2>&1 < /dev/null & echo $!' _ "$cmd" "$log_file")
  echo "$pid" > "$pid_file"
  echo "$pid"
}

echo ">>> 启动后端 (dev)..."
BACKEND_PID=$(start_detached "$BACKEND_PID_FILE" "$BACKEND_LOG" \
  "source '$PROJECT_DIR/scripts/env-jdk8.sh' && cd '$PROJECT_DIR/backend' && exec '$PROJECT_DIR/scripts/mvn-jdk8.sh' spring-boot:run -Dspring-boot.run.profiles=dev -DskipTests")

echo ">>> 启动前端..."
FRONTEND_PID=$(start_detached "$FRONTEND_PID_FILE" "$FRONTEND_LOG" \
  "cd '$PROJECT_DIR/frontend' && exec npm run dev")

echo ""
echo "已后台启动（nohup 守护，关闭终端仍运行）："
echo "  后端 PID: $BACKEND_PID  日志: $BACKEND_LOG"
echo "  前端 PID: $FRONTEND_PID  日志: $FRONTEND_LOG"
echo ""
echo "  后端 API:  http://localhost:8080/clockai/api"
echo "  前端页面:  http://localhost:5175/clockai/"
echo ""
echo "停止: bash $PROJECT_DIR/scripts/stop-dev-daemon.sh"
echo "重启: bash $PROJECT_DIR/scripts/restart-dev-daemon.sh"
