#!/usr/bin/env bash
# 本地前后端守护启动：释放端口 → setsid 后台运行 → 日志写入 logs/
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
BACKEND_LOG="$LOG_DIR/backend-dev.out"
FRONTEND_LOG="$LOG_DIR/frontend-dev.out"
BACKEND_PID_FILE="$LOG_DIR/backend-dev.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend-dev.pid"

mkdir -p "$LOG_DIR"

stop_port() {
  local port="$1"
  local pids
  pids=$(lsof -ti ":$port" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo ">>> 停止端口 $port 上的进程: $pids"
    kill $pids 2>/dev/null || true
    sleep 1
    kill -9 $pids 2>/dev/null || true
  fi
}

stop_pid_file() {
  local file="$1"
  if [ -f "$file" ]; then
    local pid
    pid=$(cat "$file" 2>/dev/null || true)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      echo ">>> 停止进程组 PGID=$pid ($file)"
      kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 -- -"$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$file"
  fi
}

echo ">>> 停止旧进程..."
stop_pid_file "$BACKEND_PID_FILE"
stop_pid_file "$FRONTEND_PID_FILE"
stop_port 8080
stop_port 5175

echo ">>> 启动后端 (dev)..."
nohup env -i HOME="$HOME" PATH="$PATH" USER="${USER:-}" SHELL="${SHELL:-/bin/bash}" \
  bash -lc "source '$PROJECT_DIR/scripts/env-jdk8.sh' && cd '$PROJECT_DIR/backend' && exec '$PROJECT_DIR/scripts/mvn-jdk8.sh' spring-boot:run -Dspring-boot.run.profiles=dev -DskipTests" \
  >> "$BACKEND_LOG" 2>&1 < /dev/null &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$BACKEND_PID_FILE"

echo ">>> 启动前端..."
nohup env -i HOME="$HOME" PATH="$PATH" USER="${USER:-}" SHELL="${SHELL:-/bin/bash}" \
  bash -lc "cd '$PROJECT_DIR/frontend' && exec npm run dev" \
  >> "$FRONTEND_LOG" 2>&1 < /dev/null &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$FRONTEND_PID_FILE"

echo ""
echo "已后台启动（nohup 守护，关闭终端仍运行）："
echo "  后端 PID: $BACKEND_PID  日志: $BACKEND_LOG"
echo "  前端 PID: $FRONTEND_PID  日志: $FRONTEND_LOG"
echo ""
echo "  后端 API:  http://localhost:8080/attendance/api"
echo "  前端页面:  http://localhost:5175/attendance/"
echo ""
echo "停止: bash $PROJECT_DIR/scripts/stop-dev-daemon.sh"
