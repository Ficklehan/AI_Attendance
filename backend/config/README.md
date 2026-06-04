# 数据库初始化与迁移

本目录包含 MySQL 建库脚本与增量迁移。应用启动时还会通过 Java `ApplicationRunner` 自动补全部分表结构（见下文「应用启动自举」）。

## 全新安装（推荐）

```bash
mysql -u root -p < backend/config/init.sql
```

`init.sql` 会：

- 创建库 `attendance_assistant`（utf8mb4）
- 创建表：`users`、`tasks`、`plugin_config`、`recognition_prompt`、`audit_logs`、`logs`、`export_jobs`
- 插入默认管理员（用户名 `admin`，密码 `admin123`，BCrypt 哈希与 `migration/004` 一致）
- 插入 `plugin_config` 默认项（飞书字段映射模板、`current_working_country` 等占位）
- 创建 `export_jobs`（含 `dismissed_at`，与 migration 006 对齐）

执行后**重启后端**，由 `PromptDatabaseBootstrap` 将内置标准提示词写入 `recognition_prompt` 表。

## 已有库升级（按顺序）

若数据库在较早版本创建，请**按编号顺序**执行，每条脚本幂等或可重复执行时需看注释：

| 顺序 | 文件 | 说明 |
|------|------|------|
| 1 | `migration/001_task_sync_and_country.sql` | `tasks` 增加 `sync_status`、`sync_error`、`prompt_country` |
| 2 | `migration/002_task_image_and_anomaly.sql` | `tasks` 增加 `image_urls`、`anomaly_summary` |
| 3 | `migration/003_task_status_add_cancelled.sql` | `status` 枚举增加 `cancelled` |
| 4 | `migration/004_fix_admin_password.sql` | 修正 `admin` 密码哈希（`admin123`） |
| 5 | `migrate_recognition_prompt.sql` | 创建 `recognition_prompt` 表并写入种子版本号 |
| 6 | `migration/005_export_jobs.sql` | 创建 `export_jobs`（旧库若缺表） |
| 7 | `migration/006_export_jobs_dismissed.sql` | `export_jobs` 增加 `dismissed_at` |

一键执行（需本机已配置 `mysql` 客户端）：

```bash
chmod +x backend/config/migrate_all.sh
./backend/config/migrate_all.sh attendance_assistant
# 或指定 MYSQL_USER：MYSQL_USER=root ./backend/config/migrate_all.sh
```

等价的手动循环见 `migrate_all.sh` 内文件列表。

> 若已执行过完整 `init.sql`（当前版本），通常**无需**再跑 001–006；仅跨版本升级时需要。

## 应用启动自举（无需手跑 SQL 时）

| 组件 | 条件 | 行为 |
|------|------|------|
| `DefaultAdminBootstrap` | `attendance.bootstrap-default-admin=true`（dev 默认开启） | 确保 `admin` 存在且密码为 `attendance.default-admin-password` |
| `ExportJobDatabaseBootstrap` | 始终 | `CREATE TABLE IF NOT EXISTS export_jobs`，并补 `dismissed_at` 列 |
| `PromptDatabaseBootstrap` | `attendance.prompt.seed-on-startup=true` | 创建 `recognition_prompt` 表；从 `base-config/prompts.md` 或内置 canonical 播种 |

生产环境建议关闭 `bootstrap-default-admin`，并修改默认密码。

## 公网部署

数据库初始化后，域名与飞书回调由 `deploy/` 统一管理，见 [deploy/README.md](../../deploy/README.md)：

```bash
npm run render:deploy:all
set -a && source deploy/rendered/production.env && source /secure/path/secrets.env && set +a
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## 表与业务含义（摘要）

| 表 | 用途 |
|----|------|
| `users` | PC 密码登录 + 飞书 `feishu_user_id` 绑定 |
| `tasks` | 识别任务；`raw_data` / `confirmed_data` 存员工 JSON 数组 |
| `recognition_prompt` | **运行时**各国 AI 提示词（主数据源） |
| `plugin_config` | 历史配置表；新部署仍初始化默认行，业务以 `base-config` + `recognition_prompt` 为主 |
| `export_jobs` | 异步导出任务与文件路径 |
| `audit_logs` | 操作审计 |
| `logs` | 任务相关日志（可选） |

任务状态枚举：`processing` → `processed` → `confirmed`；另有 `failed`、`cancelled`。详见 [docs/data-consistency.md](../../docs/data-consistency.md)。
