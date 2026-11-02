#!/usr/bin/env bash
# 用 Roma 28/10、29/10 两张考勤表跑识别，对照 gold JSON。
# 调试模式写出完整识别日志：logs/recognition-debug/roma-eval-*
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"
echo ">>> Roma 两张表时钟评测（dsh headless extract，对照 gold，debug 完整日志）"
exec "$ROOT_DIR/scripts/mvn-jdk8.sh" -Djava.awt.headless=true -Droma.sheet.eval=true -Dtest=RomaSheetClockEvalIT test
