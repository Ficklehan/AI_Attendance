#!/usr/bin/env bash
# 本地前后端 + DeepSeek Harness 守护启动（不停止旧进程；重启请用 restart-dev-daemon.sh）
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"
BACKEND_LOG="$LOG_DIR/backend-dev.out"
FRONTEND_LOG="$LOG_DIR/frontend-dev.out"
HARNESS_LOG="$LOG_DIR/harness-dev.out"
BACKEND_PID_FILE="$LOG_DIR/backend-dev.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend-dev.pid"
HARNESS_PID_FILE="$LOG_DIR/harness-dev.pid"
HARNESS_PORT="${DSH_PORT:-3080}"
SKIP_HARNESS="${SKIP_HARNESS:-0}"

mkdir -p "$LOG_DIR"

# 切分支后 target 会留下旧分支的 Mapper XML，MyBatis 会按文件名解析并因缺实体类启动失败。
SRC_MAPPER="$PROJECT_DIR/backend/src/main/resources/mapper"
OUT_MAPPER="$PROJECT_DIR/backend/target/classes/mapper"
if [ -d "$OUT_MAPPER" ]; then
  for f in "$OUT_MAPPER"/*.xml; do
    [ -f "$f" ] || continue
    base="$(basename "$f")"
    if [ ! -f "$SRC_MAPPER/$base" ]; then
      echo ">>> 清理过期 Mapper: $base"
      rm -f "$f"
    fi
  done
fi

start_detached() {
  local pid_file="$1"
  local log_file="$2"
  local cmd="$3"
  local pid
  # 独立会话（macOS 无 setsid 命令，用 Python os.setsid），避免父 shell 结束时被连带杀掉
  pid=$(
    python3 - "$pid_file" "$log_file" "$cmd" <<'PY'
import os, sys
pid_file, log_path, cmd = sys.argv[1], sys.argv[2], sys.argv[3]
pid = os.fork()
if pid > 0:
    with open(pid_file, "w") as f:
        f.write(str(pid))
    print(pid)
    sys.exit(0)
os.setsid()
log = open(log_path, "a", buffering=1)
devnull = open(os.devnull, "r")
os.dup2(devnull.fileno(), 0)
os.dup2(log.fileno(), 1)
os.dup2(log.fileno(), 2)
os.execl("/bin/bash", "bash", "-c", cmd)
PY
  )
  echo "$pid"
}

resolve_dsh() {
  if [ -n "${DSH_BIN:-}" ] && [ -x "$DSH_BIN" ]; then
    echo "$DSH_BIN"
    return 0
  fi
  if command -v dsh >/dev/null 2>&1; then
    command -v dsh
    return 0
  fi
  local cached
  cached=$(ls -t "$HOME"/.npm/_npx/*/node_modules/.bin/dsh 2>/dev/null | head -1 || true)
  if [ -n "$cached" ] && [ -x "$cached" ]; then
    echo "$cached"
    return 0
  fi
  local src_bin="$HOME/.cache/oneai-dsh/deepseek-harness/node_modules/.bin/dsh"
  if [ -x "$src_bin" ]; then
    echo "$src_bin"
    return 0
  fi
  if command -v npx >/dev/null 2>&1; then
    echo "npx:-y:@deepseek-ai/dsh@alpha"
    return 0
  fi
  return 1
}

echo ">>> 启动后端 (dev)..."
BACKEND_PID=$(start_detached "$BACKEND_PID_FILE" "$BACKEND_LOG" \
  "source '$PROJECT_DIR/scripts/env-jdk8.sh' && cd '$PROJECT_DIR/backend' && exec '$PROJECT_DIR/scripts/mvn-jdk8.sh' spring-boot:run -Dspring-boot.run.profiles=dev -DskipTests")

echo ">>> 启动前端..."
FRONTEND_PID=$(start_detached "$FRONTEND_PID_FILE" "$FRONTEND_LOG" \
  "cd '$PROJECT_DIR/frontend' && exec npm run dev")

HARNESS_PID=""
if [ "$SKIP_HARNESS" = "1" ]; then
  echo ">>> 跳过 DeepSeek Harness（SKIP_HARNESS=1）"
else
  if DSH_RESOLVED=$(resolve_dsh); then
    echo ">>> 启动 DeepSeek Harness (dsh web :$HARNESS_PORT)..."
    if [[ "$DSH_RESOLVED" == npx:* ]]; then
      HARNESS_CMD="npx -y @deepseek-ai/dsh@alpha web --no-open --port '$HARNESS_PORT'"
    else
      HARNESS_CMD="'$DSH_RESOLVED' web --no-open --port '$HARNESS_PORT'"
    fi
    HARNESS_PID=$(start_detached "$HARNESS_PID_FILE" "$HARNESS_LOG" "$HARNESS_CMD")
  else
    echo ">>> 警告: 未找到 dsh / npx，跳过 DeepSeek Harness"
    echo "    安装: npm i -g @deepseek-ai/dsh@alpha  或保证 PATH 有 npx"
    rm -f "$HARNESS_PID_FILE"
  fi
fi

echo ""
echo "已后台启动（nohup 守护，关闭终端仍运行）："
echo "  后端 PID: $BACKEND_PID  日志: $BACKEND_LOG"
echo "  前端 PID: $FRONTEND_PID  日志: $FRONTEND_LOG"
if [ -n "$HARNESS_PID" ]; then
  echo "  Harness PID: $HARNESS_PID  日志: $HARNESS_LOG"
fi
echo ""
echo "  后端 API:     http://localhost:8080/clockai/api"
echo "  前端页面:     http://localhost:5175/clockai/"
if [ -n "$HARNESS_PID" ]; then
  echo "  DeepSeek Harness: http://127.0.0.1:$HARNESS_PORT/"
fi
echo ""
echo "停止: bash $PROJECT_DIR/scripts/stop-dev-daemon.sh"
echo "重启: bash $PROJECT_DIR/scripts/restart-dev-daemon.sh"
