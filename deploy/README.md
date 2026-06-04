# AttendanceAgent 部署配置（D+ 方案）

公网域名**只维护** `deploy/environments/production.yaml`。

## 改域名后重启（推荐）

```bash
# 1. 改域名
vim deploy/environments/production.yaml   # public.host

# 2. 重启（自动 render + 加载 env + 启动后端）
./start.sh restart-prod
# 或
npm run restart:prod
```

**无需**再手跑 `npm run render:deploy` 或 `source deploy/rendered/*.env`。

## 服务器密钥

复制模板并填写（勿提交 Git）：

```bash
cp deploy/secrets.example deploy/secrets.env
# 填写 DB_*、JWT_SECRET、FEISHU_APP_ID、FEISHU_APP_SECRET、MIMO_API_KEY
```

启动时加载顺序：`secrets.env`（或 `backend/.env`）→ `deploy/rendered/production.env`（覆盖 URL/域名）。

## 目录

```
deploy/
  environments/
    production.yaml      ← 唯一域名来源
    uat.yaml               ← inherit: production，仅 profile 不同
  secrets.example
  secrets.env              ← 服务器密钥（gitignore）
  rendered/                ← 启动时自动生成
scripts/
  load-deploy-env.sh       ← render + source
  start-backend-prod.sh    ← 生产后端启动
  restart-backend-prod.sh  ← 改域名后重启
```

## 生成物（启动时自动更新）

| 输出 | 用途 |
|------|------|
| `deploy/rendered/production.env` | 域名、飞书回调、CORS、SPRING_PROFILES_ACTIVE |
| `feishu-miniprogram/config.prod.js` | 小程序公网 API |

## 启动命令

| 场景 | 命令 |
|------|------|
| 本地 dev | `./start.sh all` |
| 生产首次启动 | `./start.sh prod` |
| **改域名后** | `./start.sh restart-prod` |
| UAT profile | `ATTENDANCE_DEPLOY_ENV=uat ./scripts/restart-backend-prod.sh` |

## systemd 示例

```ini
[Service]
WorkingDirectory=/opt/AttendanceAgent
ExecStart=/opt/AttendanceAgent/scripts/start-backend-prod.sh
Environment=ATTENDANCE_DEPLOY_ENV=production
Restart=on-failure
```

改域名后：`systemctl restart attendance-backend`（ExecStart 会重新 render）。

## 验证

```bash
curl -s "https://$(grep PUBLIC_HOST deploy/rendered/production.env | cut -d= -f2)/attendance/api/feishu-auth/readiness"
```

## 小程序（手机）

后端重启后，`config.prod.js` 会同步更新，但飞书侧需**重新上传**小程序版本。

1. `config.js` → `USE_PUBLIC_API = true`
2. `./start.sh restart-prod`（或至少 render 一次）
3. 飞书开发者工具上传

## 飞书开放平台

- **重定向 URL**：与 `FEISHU_REDIRECT_URI`（见 `deploy/rendered/production.env`）
- **request 合法域名**：`PUBLIC_HOST`

## CI

见 `.github/workflows/render-deploy-config.yml`。

## 性能优化表 `task_records`（B+）

后端启动会自动建表并对历史任务回填行级记录（考勤列表/导出/重名检测走 DB 分页）。

| 场景 | 说明 |
|------|------|
| 升级已有库 | 重启后端即可（或执行 `backend/config/migration/007_task_records.sql`） |
| 全新 `init.sql` | 已包含 `task_records` 与 `tasks` 组合索引 |
| 列表为空 | 等待日志 `task_records 历史回填完成`，或再执行 `./start.sh restart-prod` |

配置项（`backend/src/main/resources/application.yml`）：

- `attendance.recognition.max-per-user`：单用户并发识别上限（默认 2）
- `attendance.upload.pdf-render-dpi`：PDF 转图 DPI（默认 200）
- `GET /tasks/{id}/progress`：识别轮询轻量接口（不含 raw_data）
- `attendance.storage.type`：`local`（默认）或 `oss`（需 OSS 环境变量）
- `attendance.performance.slow-sql-ms` / `slow-api-ms`：慢查询与慢接口日志阈值

详见运维手册 [5.6 节](../docs/运维手册.md#56-性能表-task_recordsb)。

## 完整运维手册

详见 [docs/运维手册.md](../docs/运维手册.md)（首次部署、Nginx、systemd、备份、故障排查）。
