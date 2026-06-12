# 任务提醒功能需求说明

> 状态：**开发中**（`feature/task-reminder`）  
> 分支：`feature/task-reminder`  
> 关联：`docs/data-consistency.md`（任务状态）、`FeishuService`（飞书消息）

## 1. 背景与目标

考勤任务在「待核对」等状态下若长期无人处理，需要可配置的提醒能力，通过**站内消息**与**飞书消息**触达指定人员，减少遗漏，且交互友好、可维护、非硬编码。

## 2. 已确认决策（2026-06）

| 议题 | 结论 |
|------|------|
| 「未确认」对应状态 | **`processed`（待核对）** |
| 提醒周期语义 | **方案 A：滞后提醒** — 任务进入目标状态满 N 个周期单位仍未变更则提醒；之后每满 N 单位再提醒，直至状态改变或任务删除 |
| 提醒人 | **管理员在规则中自由指定**（多选用户）**+ 默认包含任务创建者**（`tasks.user_id`，可配置开关，默认开启） |
| 多条规则同时启用 | **同一任务命中多条规则时，每条规则独立发送**；去重在**单条规则 + 单任务 + 单用户 + 周期桶**维度，规则之间互不影响 |
| 提醒通道 | 站内消息（必发）+ 飞书消息（用户有 `feishu_user_id` 时额外发送）；无飞书 ID 仅站内 |

## 3. 提醒方式

### 3.1 站内消息

- 每条命中任务的**实际提醒对象** = 规则配置的提醒人 ∪ 任务创建者（当「包含任务创建者」开启时）；去重后写入 `user_notifications`
- PC 顶栏铃铛入口 + 未读角标 + 消息列表（已读/未读）
- 点击消息跳转任务详情 `/tasks/{taskId}`（或任务列表并筛选）
- 小程序：**MVP 不做**；飞书用户可通过飞书 App 收消息

### 3.2 飞书消息

- 条件：`users.feishu_user_id` 非空
- 实现：复用 `FeishuService`，推荐**交互卡片**（标题、摘要、跳转链接）
- 失败：记录日志，**不阻塞**站内消息；可写入投递记录 `feishu_status=failed`

### 3.3 全局总开关

- 沿用配置页「启用通知」`notificationEnabled`（`plugin_config` / 系统配置）
- 关闭时：调度器不执行、不发送任何提醒

## 4. 提醒规则配置

### 4.1 入口与权限

- PC：**设置 → 提醒规则**（新 Tab）
- 权限键：`reminderConfig`（建议仅 `admin`，写入 `base-config/permissions.json`）
- 规则 CRUD：创建、编辑、启用/停用、列表查看上次执行摘要

### 4.2 配置项（均为必填）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| 规则名称 | 文本 | `待核对任务提醒` | 同环境内建议唯一 |
| 任务状态 | 多选枚举 | `processed` | 至少 1 项；MVP 以 `processed` 为主，表结构支持扩展 |
| 提醒周期 | 正整数 + 单位 | `1` + `天` | 单位：`hour` / `day` / `week` |
| 提醒人 | 用户多选 | （管理员选择） | 至少 1 人，仅 `status=active` |
| 包含任务创建者 | 开关 | **`true`（开启）** | 开启时，每条命中任务的 `tasks.user_id` 自动加入该次投递对象（与提醒人去重）；创建者已停用/删除时跳过并记日志 |
| 提醒文案 | 多行文本 | 见 §4.4 | 支持变量；表单提供预览与「恢复默认」 |

**提醒人合并规则（运行时）：**

```
实际投递对象 = reminder_rule_users（规则多选）
            ∪ { tasks.user_id }   // 当 include_task_creator = true
            → 按 user_id 去重
            → 过滤 status != active
```

**建议附加（非业务必填，便于运维）：**

| 字段 | 默认 | 说明 |
|------|------|------|
| enabled | `true` | 停用后不参与扫描 |
| description | 空 | 管理员备注，不下发用户 |

### 4.3 方案 A — 滞后提醒算法

1. 定时任务（建议每 **15 分钟**）加载 `enabled=true` 且全局通知开启的规则  
2. 查询 `tasks.status IN (规则配置的状态)`  
3. 计算 **`status_entered_at`**：进入当前状态的时间（优先专用字段；MVP 可用 `updated_at` 或从审计/状态变更推导，实现时需在技术方案中明确）  
4. 解析该任务的**投递对象**：规则提醒人 +（若 `include_task_creator`）任务创建者，去重  
5. 若 `now - status_entered_at >= 周期`，且本周期桶内该 `(rule_id, task_id, user_id)` 未投递，则向每位投递对象发送  
6. 任务状态离开规则目标状态（如 `processed → confirmed`）后，**不再提醒**  
7. 同一规则对同一任务：每满一个周期可再提醒一次（更新文案中的 `pendingCount` 等聚合信息）

### 4.4 默认文案模板

**场景：待核对（`processed`）**

```
【考勤待核对提醒】
您有 {pendingCount} 个任务处于「待核对」状态，已超过 {threshold} 未处理。
最近任务：{latestTaskId}（{latestTaskTime}）
请及时登录系统完成核对。
```

**变量**

| 变量 | 含义 |
|------|------|
| `{pendingCount}` | 当前规则下、满足滞后条件、且与提醒人相关的任务数（实现细则：按规则扫描范围统计） |
| `{threshold}` | 如 `1 天`、`12 小时` |
| `{taskStatus}` | 状态中文名 |
| `{latestTaskId}` | 最近一条命中任务号 |
| `{latestTaskTime}` | 该任务进入当前状态时间 |
| `{recipientName}` | 提醒人姓名 |
| `{taskCreatorName}` | 任务创建者姓名（文案中可选展示） |

## 5. 交互设计要点

### 5.1 规则列表

- 展示：名称、状态、周期、提醒人数、启用状态、上次执行时间、命中/发送数量
- 操作：新建、编辑、启用/停用

### 5.2 新建/编辑（分步表单）

1. **何时提醒**：状态多选 + 周期数值与单位 + 方案 A 说明文案  
2. **提醒谁**：用户多选，展示「飞书+站内 / 仅站内」标识；**「同时提醒任务创建者」开关默认开启**（说明：每条命中任务会额外通知其 `user_id` 对应账号）  
3. **说什么**：文案编辑 + 变量插入 + 实时预览 + 恢复默认  
4. **确认**：摘要后保存

校验：必填项未填禁止保存，字段级错误提示。

### 5.3 站内消息中心

- 顶栏铃铛 + 未读数
- 列表：今天/更早分组；未读蓝点
- 操作：单条已读、全部已读；点击跳转任务
- 同规则同周期内对同一用户：**可合并更新**一条站内消息（更新 `pendingCount`），避免列表刷屏

## 6. 数据模型（草案）

```sql
-- 提醒规则
reminder_rules (
  id, name, description,
  task_statuses JSON,          -- ["processed"]
  interval_value INT,          -- >= 1
  interval_unit ENUM,            -- hour|day|week
  message_template TEXT,
  include_task_creator TINYINT DEFAULT 1,  -- 1=默认包含 tasks.user_id
  enabled TINYINT,
  created_by, created_at, updated_at
)

-- 规则 ↔ 提醒人
reminder_rule_users (
  rule_id, user_id, PRIMARY KEY(rule_id, user_id)
)

-- 投递去重（单规则维度）
reminder_deliveries (
  id, rule_id, task_id, user_id,
  period_bucket VARCHAR,         -- 如 2026-06-02-day-1
  channel_site TINYINT,
  channel_feishu TINYINT,
  feishu_status VARCHAR,
  created_at
)

-- 站内消息
user_notifications (
  id, user_id, rule_id,
  title, body, link,
  read_at, created_at, updated_at
)
```

## 7. API 草案

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/reminder-rules` | 规则列表（admin） |
| POST | `/reminder-rules` | 创建规则 |
| PUT | `/reminder-rules/{id}` | 更新规则 |
| PATCH | `/reminder-rules/{id}/enabled` | 启用/停用 |
| DELETE | `/reminder-rules/{id}` | 删除（软删或硬删，实现时定） |
| GET | `/notifications` | 当前用户站内消息分页 |
| GET | `/notifications/unread-count` | 未读数 |
| PATCH | `/notifications/{id}/read` | 标记已读 |
| POST | `/notifications/read-all` | 全部已读 |

调度：后端 `@Scheduled`，不暴露 HTTP。

## 8. 实现分期

### MVP（`feature/task-reminder`）

- [x] 数据库迁移与实体  
- [x] 规则 CRUD API + PC 设置页  
- [x] 调度器 + 方案 A 逻辑 + 投递去重  
- [x] 站内消息 API + PC 铃铛与列表  
- [x] 飞书卡片消息（有 `feishu_user_id` 时）  
- [x] `reminderConfig` 权限  
- [x] 默认文案与中英文 i18n（配置页与消息标题）

### 二期（后续）

- 多状态组合（如 `sync_failed`）  
- 小程序消息只读页  
- 静默时段、投递报表  

## 9. 验收标准（MVP）

1. 管理员可创建一条规则：状态=`processed`，周期=1天，指定≥1提醒人，**「包含任务创建者」默认开启**，自定义文案。  
2. 存在超过 1 天未确认的 `processed` 任务时，调度执行后**规则提醒人 + 任务创建者**（去重）收到站内消息；有飞书 ID 的另收飞书消息。  
2a. 创建者不在规则提醒人列表中、但开关开启时，创建者仍应收到提醒。  
2b. 关闭「包含任务创建者」后，仅向规则多选用户发送。  
3. 无飞书 ID 的用户仅收站内消息，系统不报错。  
4. 任务确认后不再收到该任务提醒。  
5. 两条规则同时命中同一任务时，两条规则各自向各自提醒人发送（各自去重）。  
6. 关闭「启用通知」后全局不发送。  

## 10. 相关文档

- [data-consistency.md](../data-consistency.md) — 任务状态定义  
- [architecture-and-config.md](../architecture-and-config.md) — 系统架构  
- [base-config/permissions.json](../../base-config/permissions.json) — 权限扩展  
