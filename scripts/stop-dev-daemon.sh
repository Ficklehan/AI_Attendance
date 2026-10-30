#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
HARNESS_PORT="${DSH_PORT:-3080}"

stop_port() {
  local port="$1"
  local pids
  pids=$(lsof -ti ":$port" 2>/dev/null || true)
  if [ -n "$pids" ]; then
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
      kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 -- -"$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$file"
  fi
}

for file in "$LOG_DIR/backend-dev.pid" "$LOG_DIR/frontend-dev.pid" "$LOG_DIR/harness-dev.pid"; do
  stop_pid_file "$file"
done

# 兜底：按命令行特征杀掉残留 dsh web（勿用过宽的 dsh 子串）
pkill -f '/\.bin/dsh web' 2>/dev/null || true
pkill -f 'npx -y @deepseek-ai/dsh@alpha web' 2>/dev/null || true
pkill -f '@deepseek-ai/dsh/lib/bin\.js web' 2>/dev/null || true

stop_port 8080
stop_port 5175
stop_port "$HARNESS_PORT"
echo "前后端 + DeepSeek Harness 已停止"
