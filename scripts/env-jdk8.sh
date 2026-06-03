#!/usr/bin/env bash

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JDK_HOME="$PROJECT_DIR/.jdk/home"

is_java8() {
  local java_bin="$1"
  [ -x "$java_bin" ] || return 1
  local version
  version="$("$java_bin" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
  local major minor
  major="$(echo "$version" | cut -d'.' -f1)"
  minor="$(echo "$version" | cut -d'.' -f2)"
  [ "$major" = "1" ] && [ "$minor" = "8" ]
}

if [ -n "${JAVA_HOME:-}" ] && is_java8 "$JAVA_HOME/bin/java"; then
  export PATH="$JAVA_HOME/bin:$PATH"
  return 0 2>/dev/null || exit 0
fi

if [ -x "$JDK_HOME/bin/java" ] && is_java8 "$JDK_HOME/bin/java"; then
  export JAVA_HOME="$JDK_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  return 0 2>/dev/null || exit 0
fi

if [ -x "$PROJECT_DIR/scripts/setup-jdk8.sh" ]; then
  bash "$PROJECT_DIR/scripts/setup-jdk8.sh"
fi

if [ -x "$JDK_HOME/bin/java" ] && is_java8 "$JDK_HOME/bin/java"; then
  export JAVA_HOME="$JDK_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
  return 0 2>/dev/null || exit 0
fi

echo "错误: 未找到 JDK 8。请运行: bash scripts/setup-jdk8.sh"
return 1 2>/dev/null || exit 1
