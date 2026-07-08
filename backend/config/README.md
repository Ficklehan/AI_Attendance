# 数据库初始化与迁移

本目录包含 MySQL 建库脚本与增量迁移。应用启动时还会通过 Java `ApplicationRunner` 自动补全部分表结构（见下文「应用启动自举」）。

## 全新安装（推荐）

```bash
mysql -u root -p < backend/config/init.sql
```

`init.sql` 会：

- 创建库 `attendance_assistant`（utf8mb4）
- 创建**完整**表结构（对齐 `migration/001`–`024` 最终态），含：
  - 核心：`users`、`tasks`、`task_records`、`export_jobs`、`recognition_prompt`、`plugin_config`、`audit_logs`、`logs`
  - RBAC：`system_role`、`user_role`、`role_data_scope`、`role_data_dimension_rule`
  - 提醒：`reminder_rules`、`reminder_rule_users`、`reminder_deliveries`、`user_notifications`、`reminder_feishu_messages`、`reminder_schedules`
  - 其它：`recognition_queue_jobs`、`feishu_country_config`、`employees`、`employee_serial_counters`
- 插入默认管理员（用户名 `admin`，密码 `admin123`）及 `user_role` / `system_role` 种子

执行后**重启后端**，由 `PromptDatabaseBootstrap` / `FeishuCountryConfigDatabaseBootstrap` 等播种运行时配置。

## 已有库升级

使用一键脚本（按**文件名字母序**执行，解决 007/008/009 等同编号多文件问题）：

```bash
chmod +x backend/config/migrate_all.sh
./backend/config/migrate_all.sh attendance_assistant
# 或：MYSQL_USER=root ./backend/config/migrate_all.sh
```

当前脚本包含 **30** 个迁移步骤（001–024 + `migrate_recognition_prompt.sql`）。

### 同编号多文件说明

| 前缀 | 文件数 | 说明 |
|------|--------|------|
| 007 | 2 | `reminder_tables` + `task_records` |
| 008 | 4 | notification / reminder_scope / task_progress / smart_mark |
| 009 | 3 | feishu_messages / supervisor_template / role_data_scope |
| 010–012 | 各 2 | 见 `migrate_all.sh` 内顺序 |

### 遗留库专用（默认不执行）

| 文件 | 何时手动执行 |
|------|----------------|
| `011_reminder_template_locales.sql` | `reminder_feishu_messages` 仍为旧主键结构 |
| `016_reminder_association_tables_drop.sql` + `017_..._create.sql` | 需重建关联表 PK（**会删数据**） |
| `025_user_role_surrogate_id.sql` | `user_role` 仍为旧版复合主键 `(user_id, role_key)`；转为自增 `id` 单列主键（应用启动时 `UserRoleDatabaseBootstrap` 会自愈，通常无需手工执行） |

> 若已执行过完整 `init.sql`（当前版本），通常**无需**再跑增量迁移。

## 应用启动自举

| 组件 | 条件 | 行为 |
|------|------|------|
| `DefaultAdminBootstrap` | `attendance.bootstrap-default-admin=true`（dev 默认） | 确保 `admin` 存在 |
| `ExportJobDatabaseBootstrap` | 始终 | 补全 `export_jobs` |
| `PromptDatabaseBootstrap` | `attendance.prompt.seed-on-startup=true` | 播种 `recognition_prompt` |
| `UserRoleDatabaseBootstrap` | 始终 | 确保 `user_role` 表并回填 |
| `RoleDataScopeDatabaseBootstrap` | 始终 | 确保角色数据范围表 |

生产环境建议关闭 `bootstrap-default-admin`，并修改默认密码。

## 表与业务含义（摘要）

| 表 | 用途 |
|----|------|
| `users` / `user_role` | PC 登录 + 飞书绑定 + 多角色 |
| `system_role` / `role_data_scope` | 可配置角色与数据范围 |
| `tasks` / `task_records` | 识别任务与行级记录 |
| `recognition_prompt` / `feishu_country_config` | 运行时国家配置 |
| `reminder_*` / `user_notifications` | 任务提醒与站内消息 |
| `employees` | 员工主档与工号发号 |
| `export_jobs` | 异步导出 |
| `audit_logs` | 操作审计 |

任务状态与统计约定见 [docs/data-consistency.md](../../docs/data-consistency.md)。
