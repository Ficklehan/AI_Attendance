#!/usr/bin/env bash
# 已有库按顺序执行增量迁移（全新库请直接用 init.sql）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DB="${1:-attendance_assistant}"
MYSQL_USER="${MYSQL_USER:-root}"

run() {
  local f="$1"
  echo ">>> $(basename "$f")"
  mysql -u "$MYSQL_USER" -p "$DB" < "$f"
}

run "$ROOT/migration/001_task_sync_and_country.sql"
run "$ROOT/migration/002_task_image_and_anomaly.sql"
run "$ROOT/migration/003_task_status_add_cancelled.sql"
run "$ROOT/migration/004_fix_admin_password.sql"
run "$ROOT/migrate_recognition_prompt.sql"
run "$ROOT/migration/005_export_jobs.sql"
run "$ROOT/migration/006_export_jobs_dismissed.sql"

echo "迁移完成。请重启后端以执行提示词播种。"
