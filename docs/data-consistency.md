# 任务数据一致性说明

> 系统架构、配置分层与数据库初始化见 [architecture-and-config.md](./architecture-and-config.md)、[backend/config/README.md](../backend/config/README.md)。

## 状态机（数据库 `tasks.status`）

| 状态值 | 业务含义 | 小程序 / PC 展示 |
|--------|----------|------------------|
| `processing` | AI 识别中 | 识别中 |
| `processed` | 已识别，待人工核对 | **待核对** |
| `confirmed` | 已确认提交 | 已完成 |
| `failed` | 识别失败 | 失败 |
| `cancelled` | 已作废 | 已作废 |

## 单一事实来源

所有端的「任务概览」「去核对 (N)」、Tab 标题条数均来自：

```
GET /clockai/api/tasks/summary
```

响应字段：

- `processing` — 识别中数量  
- `review` — 待核对（`processed`）数量  
- `confirmed` — 已完成数量  
- `failed` / `cancelled` — 其它状态  
- `total` — 以上之和  
- `allUsersScope` — 当前是否为管理员全员视图  

列表分页仍使用 `GET /clockai/api/tasks?status=&current=&size=`；筛选 `status=processed` 时，`page.total` 必须等于 `summary.review`。

## 数据范围

| 角色 | 列表 / 汇总 / 导出数据范围 |
|------|---------------------------|
| 普通用户 | 仅 `user_id = 当前登录用户` |
| 管理员 (`role=admin`) | **全部用户** 的任务 |

管理员在 PC 任务列表会看到蓝色提示「管理员视图：包含全部用户的任务」。

### 飞书账号绑定（推荐）

管理员在 **设置 → 用户管理 → 编辑用户** 中填写 **飞书用户 ID**（`open_id`）。  
绑定后，该 PC 账号与飞书小程序登录将共享同一 `user_id` 下的任务。

未绑定时，飞书首次登录会自动注册独立账号，与 PC 密码账号数据分离。

## 客户端约定

- **禁止** 用列表第一页 `records.length` 充当全库统计。  
- **禁止** 对 `size=20/50` 的子集计数作为 CTA 数字。  
- 删除 / 确认任务后应重新请求 `summary` 与当前 Tab 列表。

## 兼容接口

`GET /clockai/api/tasks/stats` 保留给小程序「我的」页，字段已与 `summary` 对齐：

- `pending` / `review` → `summary.review`  
- `processing` → `summary.processing`  
- `completed` / `confirmed` → `summary.confirmed`  

## 导出

异步导出在创建任务时写入 `TaskQuery.allUsersScope` / `listScopeUserId`，后台线程按相同范围导出，避免管理员导出只看到本人数据。
