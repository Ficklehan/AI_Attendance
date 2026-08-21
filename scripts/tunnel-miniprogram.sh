#!/usr/bin/env bash
# 为飞书小程序真机调试暴露本地后端（需已安装 cloudflared）
# 用法: bash scripts/tunnel-miniprogram.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${BACKEND_PORT:-8080}"
CONTEXT="/clockai/api"

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "未安装 cloudflared。安装: brew install cloudflared"
  echo "或使用 ngrok: ngrok http ${PORT}"
  exit 1
fi

if ! curl -sf "http://127.0.0.1:${PORT}${CONTEXT}/config/current-country" >/dev/null; then
  echo "警告: 本地后端 http://127.0.0.1:${PORT}${CONTEXT} 不可达，请先 ./start.sh 或启动 backend"
fi

echo "启动 cloudflared 隧道 → localhost:${PORT}"
echo "隧道就绪后，将 HTTPS 地址写入 deploy/environments/production.yaml 的 public.host"
echo "然后: 写入 production.yaml 的 public.host，执行 ./start.sh apply"
echo "或在开发者工具 Console 执行:"
echo "  tt.setStorageSync('apiBaseUrlOverride', 'https://YOUR-TUNNEL${CONTEXT}')"
echo

exec cloudflared tunnel --url "http://127.0.0.1:${PORT}"
