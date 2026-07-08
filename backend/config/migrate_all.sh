#!/usr/bin/env bash
# 已有库按顺序执行增量迁移（全新库请直接用 init.sql）
# 同编号前缀（007/008/009…）按文件名字母序执行；016/017/011_reminder_template_locales 仅用于遗留库修复
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DB="${1:-attendance_assistant}"
MYSQL_USER="${MYSQL_USER:-root}"

run() {
  local f="$1"
  echo ">>> $(basename "$f")"
  mysql -u "$MYSQL_USER" -p "$DB" < "$f"
}

MIGRATIONS=(
  "$ROOT/migration/001_task_sync_and_country.sql"
  "$ROOT/migration/002_task_image_and_anomaly.sql"
  "$ROOT/migration/003_task_status_add_cancelled.sql"
  "$ROOT/migration/004_fix_admin_password.sql"
  "$ROOT/migrate_recognition_prompt.sql"
  "$ROOT/migration/005_export_jobs.sql"
  "$ROOT/migration/006_export_jobs_dismissed.sql"
  "$ROOT/migration/007_reminder_tables.sql"
  "$ROOT/migration/007_task_records.sql"
  "$ROOT/migration/008_notification_feishu_message_id.sql"
  "$ROOT/migration/008_reminder_rule_scope.sql"
  "$ROOT/migration/008_task_progress.sql"
  "$ROOT/migration/008_task_records_smart_mark.sql"
  "$ROOT/migration/009_reminder_feishu_messages.sql"
  "$ROOT/migration/009_reminder_supervisor_template.sql"
  "$ROOT/migration/009_role_data_scope.sql"
  "$ROOT/migration/010_reminder_interval_decimal.sql"
  "$ROOT/migration/010_system_roles.sql"
  "$ROOT/migration/011_recognition_checkpoint.sql"
  # 011_reminder_template_locales.sql — 仅当 reminder_feishu_messages 仍为旧主键时手动执行
  "$ROOT/migration/012_notification_content_vars.sql"
  "$ROOT/migration/012_recognition_queue.sql"
  "$ROOT/migration/013_reminder_schedule_hour.sql"
  "$ROOT/migration/014_feishu_country_config.sql"
  "$ROOT/migration/015_user_working_country.sql"
  # 016/017 — 仅遗留库 PK 修复，默认跳过（见 backend/config/README.md）
  "$ROOT/migration/018_reminder_schedules.sql"
  "$ROOT/migration/019_task_status_entered_at.sql"
  "$ROOT/migration/020_employees.sql"
  "$ROOT/migration/021_task_records_confirmed_only.sql"
  "$ROOT/migration/022_agency_billing_index.sql"
  "$ROOT/migration/023_user_roles.sql"
  "$ROOT/migration/024_role_data_scope_work_region.sql"
  # 025_user_role_surrogate_id.sql — 仅当 user_role 仍为旧版复合主键时手动执行
  #   （应用启动时 UserRoleDatabaseBootstrap 会自愈，通常无需手工执行；已是自增 id 会报错，跳过即可）
)

for f in "${MIGRATIONS[@]}"; do
  run "$f"
done

echo "迁移完成（共 ${#MIGRATIONS[@]} 个脚本）。请重启后端以执行提示词/飞书配置播种。"
