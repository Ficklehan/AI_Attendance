# 任务提醒功能 — 开发任务规划

> 需求基线：[task-reminder.md](../requirements/task-reminder.md)  
> 分支：`feature/task-reminder`

## 分期总览

| 阶段 | 目标 | 预估 | 状态 |
|------|------|------|------|
| **P1** | 数据库 + 后端核心（规则 CRUD、调度、站内消息、飞书） | 2–3d | 已完成 |
| **P2** | PC 前端（设置页、铃铛、系统开关持久化） | 1–2d | 已完成 |
| **P3** | 联调验收 + i18n 补全 | 0.5d | 待开始 |

---

## P1 — 后端

### P1.1 数据库

- [x] `backend/config/migration/007_reminder_tables.sql`
- [x] `ReminderDatabaseBootstrap`（启动时 CREATE IF NOT EXISTS）
- 表：`reminder_rules`、`reminder_rule_users`、`reminder_deliveries`、`user_notifications`

### P1.2 实体与 Mapper

- [ ] `ReminderRule`、`ReminderDelivery`、`UserNotification`
- [ ] `ReminderRuleMapper`、`ReminderDeliveryMapper`、`UserNotificationMapper` + XML
- [ ] `PluginConfigMapper`（读写 `notification_enabled`）
- [ ] `TaskMapper.selectByStatuses`（调度扫描）

### P1.3 服务层

- [ ] `PluginConfigService` — 全局通知开关
- [ ] `ReminderRuleService` — CRUD、启用/停用、提醒人维护
- [ ] `ReminderMessageRenderer` — 模板变量替换
- [ ] `UserNotificationService` — 分页、未读数、已读
- [ ] `ReminderSchedulerService` — `@Scheduled` 每 15 分钟，方案 A + 去重 + 飞书

### P1.4 API

| 方法 | 路径 | 权限 |
|------|------|------|
| GET/POST | `/reminder-rules` | `reminderConfig` |
| PUT/PATCH/DELETE | `/reminder-rules/{id}` | `reminderConfig` |
| GET | `/notifications` | 登录用户 |
| GET | `/notifications/unread-count` | 登录用户 |
| PATCH | `/notifications/{id}/read` | 登录用户 |
| POST | `/notifications/read-all` | 登录用户 |
| GET/PUT | `/config/system` | admin（`notificationEnabled`） |

### P1.5 权限

- [ ] `base-config/permissions.json` 增加 `reminderConfig`
- [ ] `PermissionService.defaultRole` / `loadAll` admin 强制 true

---

## P2 — PC 前端

### P2.1 路由与菜单

- [ ] `/settings/reminders` → `ReminderRules.vue`
- [ ] `SettingsLayout` 增加「提醒规则」菜单项（`reminderConfig` 可见）

### P2.2 提醒规则页

- [ ] 规则列表（名称、周期、人数、启用、上次执行摘要）
- [ ] 分步表单：何时提醒 → 提醒谁 → 说什么 → 确认
- [ ] 用户多选（展示飞书/仅站内标识）
- [ ] 「包含任务创建者」开关（默认开）

### P2.3 消息中心

- [ ] `Layout.vue` 顶栏铃铛 + 未读角标
- [ ] 抽屉/Popover：消息列表、单条已读、全部已读、跳转任务

### P2.4 系统配置

- [ ] `Config.vue` `saveSystemConfig` 对接 `/config/system`
- [ ] `api/reminder.js`、`api/notification.js`

### P2.5 i18n

- [ ] `zh-CN.js`、`en-US.js`（提醒规则页 + 消息中心；其他语言可后续补）

---

## P3 — 验收

对照 [task-reminder.md §9](../requirements/task-reminder.md#9-验收标准mvp)：

1. 管理员创建规则（processed / 1天 / ≥1人 / 含创建者 / 自定义文案）
2. 滞后任务触发：规则人 + 创建者收站内；有飞书 ID 另收飞书
2a. 创建者不在规则列表但开关开 → 仍收到
2b. 关闭含创建者 → 仅规则人多选用户
3. 无飞书 ID 不报错
4. 确认后不再提醒
5. 多规则独立发送
6. 关闭「启用通知」全局静默

---

## 技术要点（实现约定）

| 议题 | 约定 |
|------|------|
| `status_entered_at` | MVP 使用 `tasks.updated_at` |
| 周期桶 | `period_bucket = String.valueOf(elapsed / intervalMs)`，`elapsed >= interval` 才投递 |
| 投递对象 | `rule_users ∪ {task.user_id}`（`include_task_creator`），去重，过滤非 active |
| 站内合并 | 同 `(user_id, rule_id, period_bucket)` 更新已有未读通知正文 |
| 飞书 | `FeishuService.sendCardMessage`；失败记 `feishu_status=failed`，不阻塞站内 |
| 调度 | `fixedDelay = 900_000`（15 分钟），`initialDelay = 180_000` |

---

## 依赖与风险

- **飞书凭证**：未配置时站内仍可用，飞书通道跳过并记日志
- **`updated_at` 近似**：状态多次变更会重置计时；二期可加 `status_entered_at` 专用列
- **时区**：与系统 `GMT+8` 一致，周期按绝对时长计算
