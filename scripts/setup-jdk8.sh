#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JDK_DIR="$PROJECT_DIR/.jdk"
MARKER="$JDK_DIR/.installed"

if [ -f "$MARKER" ] && [ -x "$JDK_DIR/home/bin/java" ]; then
  echo "JDK 8 已就绪: $JDK_DIR/home"
  exit 0
fi

ARCH="$(uname -m)"
case "$ARCH" in
  arm64|aarch64)
    ZULU_PKG="zulu8.94.0.17-ca-jdk8.0.492-macosx_aarch64.tar.gz"
    ;;
  x86_64)
    ZULU_PKG="zulu8.94.0.17-ca-jdk8.0.492-macosx_x64.tar.gz"
    ;;
  *)
    echo "错误: 不支持的 CPU 架构: $ARCH"
    exit 1
    ;;
esac

ZULU_URL="https://cdn.azul.com/zulu/bin/$ZULU_PKG"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo ">>> 下载 JDK 8 ($ZULU_PKG) ..."
curl -fsSL "$ZULU_URL" -o "$TMP_DIR/$ZULU_PKG"

echo ">>> 解压到 $JDK_DIR ..."
mkdir -p "$JDK_DIR"
tar -xzf "$TMP_DIR/$ZULU_PKG" -C "$JDK_DIR"

EXTRACTED="$(find "$JDK_DIR" -maxdepth 1 -type d -name 'zulu8*' | head -1)"
if [ -z "$EXTRACTED" ]; then
  echo "错误: 解压后未找到 Zulu JDK 目录"
  exit 1
fi

if [ -d "$EXTRACTED/Contents/Home" ]; then
  HOME_DIR="$EXTRACTED/Contents/Home"
else
  HOME_DIR="$EXTRACTED"
fi

ln -sfn "$HOME_DIR" "$JDK_DIR/home"
date > "$MARKER"

echo ">>> JDK 8 安装完成"
"$JDK_DIR/home/bin/java" -version
