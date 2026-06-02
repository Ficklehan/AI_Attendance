#!/usr/bin/env bash
# 创建 GitHub 仓库并推送 main 分支（需先 gh auth login 或设置 GH_TOKEN）
set -euo pipefail

REPO_NAME="${1:-AttendanceAgent}"
VISIBILITY="${2:-private}"
GH_BIN="${GH_BIN:-/tmp/gh_2.93.0_macOS_arm64/bin/gh}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

cd "$ROOT"

auth_ok() {
  if [[ -n "${GH_TOKEN:-}" ]]; then
    echo "$GH_TOKEN" | "$GH_BIN" auth login --with-token 2>/dev/null || true
  fi
  "$GH_BIN" auth status >/dev/null 2>&1
}

if ! command -v git >/dev/null 2>&1; then
  echo "错误: 未找到 git"
  exit 1
fi

if [[ ! -x "$GH_BIN" ]]; then
  echo "错误: 未找到 GitHub CLI，请安装 gh 或设置 GH_BIN"
  exit 1
fi

if ! auth_ok; then
  echo "未登录 GitHub。请先执行其一："
  echo "  export GH_TOKEN=你的_Personal_Access_Token"
  echo "  $GH_BIN auth login -h github.com -p https -w"
  exit 1
fi

OWNER="$("$GH_BIN" api user -q .login)"
REMOTE="https://github.com/${OWNER}/${REPO_NAME}.git"

if ! "$GH_BIN" repo view "${OWNER}/${REPO_NAME}" >/dev/null 2>&1; then
  echo "创建仓库 ${OWNER}/${REPO_NAME} (${VISIBILITY})..."
  "$GH_BIN" repo create "$REPO_NAME" --"${VISIBILITY}" --source=. --remote=origin --push=false
else
  echo "仓库已存在: ${OWNER}/${REPO_NAME}"
  if ! git remote get-url origin >/dev/null 2>&1; then
    git remote add origin "$REMOTE"
  fi
fi

echo "推送到 origin main..."
git push -u origin main

echo "完成: https://github.com/${OWNER}/${REPO_NAME}"
