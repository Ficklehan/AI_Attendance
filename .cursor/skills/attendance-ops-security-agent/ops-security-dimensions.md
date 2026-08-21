# 运维安全审计维度

AttendanceAgent 专用检查清单。审计时按环境勾选适用项；每项需给出 **证据**（配置片段、命令输出、文件路径）。

---

## 1. 密钥与凭据管理

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 1.1 | `deploy/secrets.env` 未入 Git | `git check-ignore deploy/secrets.env` 或不在 tracked 列表 | 误提交、`.gitignore` 缺口 |
| 1.2 | 文件权限 | `600`，属主为运行用户 | `644` 可被同组读取 |
| 1.3 | JWT 强度 | ≥ 32 字节随机，非 `changeme` / 示例值 | 短密钥、与 dev 共用 |
| 1.4 | 环境隔离 | prod/uat/dev 使用不同 `JWT_SECRET`、DB 密码 | 多环境共用密钥 |
| 1.5 | Render 产物 | `deploy/rendered/*.env` 仅含 URL/域名，无 API 密钥 | 误将 secrets 写入 rendered |
| 1.6 | 仓库扫描 | 无历史泄露（gitleaks / 手动 grep） | `logs/*.out`、截图、旧 commit |
| 1.7 | 轮换策略 | 飞书 Secret、MiMo Key、OSS AK 有轮换记录 | 从未轮换 |
| 1.8 | 默认管理员 | 生产已改 `DEFAULT_ADMIN_PASSWORD` | 仍为 `admin123` |

**关键文件**：`deploy/secrets.example`、`deploy/secrets.env`、`backend/.env`、`application-dev.yml`（默认值）

---

## 2. 网络、TLS 与反向代理

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 2.1 | HTTPS 强制 | 80 → 301 HTTPS；有效证书 | HTTP 明文、过期证书 |
| 2.2 | 后端不直连公网 | 仅 `127.0.0.1:8080` 监听 | `0.0.0.0:8080` 暴露 |
| 2.3 | 代理头 | `X-Forwarded-Proto`、`X-Real-IP` 正确 | OAuth 回调 http/https 错乱 |
| 2.4 | 上传限制 | `client_max_body_size` 合理（≥20m 且不过大） | 无限制导致 DoS |
| 2.5 | 安全响应头 | `HSTS`、`X-Content-Type-Options`、`X-Frame-Options` 等 | 完全缺失 |
| 2.6 | 静态资源 | `/clockai/` 不列出目录、不执行脚本 | `alias` 路径错误导致遍历 |
| 2.7 | 兜底路由 | 旧 `/feishu/callback` 302 到新路径 | OAuth 404 导致 token 泄露尝试 |
| 2.8 | 防火墙 | 仅 22/443（及必要管理口）开放 | MySQL 3306 公网 |

**参考**：`deploy/nginx-production-snippet.conf.example`

---

## 3. 应用运行时安全

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 3.1 | Spring Profile | 生产 `prod` + `application-deploy.yml` | 误用 `dev` profile |
| 3.2 | 危险开关 | 三项 allow/bootstrap 均为 `false` | 模拟识别、公开注册开启 |
| 3.3 | CORS | `CORS_ALLOWED_ORIGIN` 为单一生产域 | `*` 或过多 origin |
| 3.4 | 并发识别限流 | `attendance.recognition.max-per-user` 已配置 | 无限制耗尽显存/API |
| 3.5 | 导出留存 | `export.retention-days` 合理 | 敏感 Excel 长期堆积 |
| 3.6 | 本地存储 | `uploads/`、`exports/` 权限与归属正确 | 全局可读 |
| 3.7 | OSS（若启用） | 桶策略最小权限、前缀隔离、CDN 不回源写 | 公共写桶 |
| 3.8 | Actuator / 调试 | 无公开 `/actuator`、无 `debug=true` | 信息泄露 |

**关键配置**：`application-deploy.yml`、`application-prod.yml`、`application.yml`

---

## 4. 身份、鉴权与飞书集成

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 4.1 | JWT 过期 | `JWT_EXPIRATION` 合理（默认 8h） | 过长或不失效 |
| 4.2 | OAuth 回调 | `FEISHU_REDIRECT_URI` 与开放平台完全一致 | 路径少 `/clockai/api` |
| 4.3 | 小程序合法域名 | 仅 `PUBLIC_HOST` 主机名 | 带 `https://` 或路径 |
| 4.4 | 服务端出站 | 服务器可访问 `open.feishu.cn` | 防火墙阻断 |
| 4.5 | IP 白名单 | 若飞书启用 IP 白名单，含服务器出口 IP | 漏配导致 API 失败 |
| 4.6 | RBAC / 数据范围 | 生产角色与 `base-config/permissions.json` 一致 | 过度授权 |
| 4.7 | 会话失效 | JWT 轮换后旧 token 不可用 | 无失效机制 |

**验证**：`GET /feishu-auth/readiness`、`docs/运维手册.md` §5.8

---

## 5. 数据库与数据保护

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 5.1 | MySQL 网络 | 仅本机或内网 VPC | 公网可连 |
| 5.2 | 账户最小权限 | 应用用户非 `root`；仅必要库权限 | root 跑应用 |
| 5.3 | 连接加密 | 生产建议 TLS 或内网隔离 | 明文跨公网 |
| 5.4 | 备份 | 每日 `mysqldump`；加密离线存储 | 无备份、备份与库同机无加密 |
| 5.5 | 恢复演练 | 有文档化恢复步骤 | 从未演练 |
| 5.6 | PII / 考勤数据 | 导出、日志脱敏策略明确 | 日志打印完整手机号 |
| 5.7 | 迁移安全 | `backend/config/migration/` 幂等、无破坏性默认 | 生产误跑 dev 脚本 |

---

## 6. 日志、监控与审计

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 6.1 | 日志路径 | prod → `backend/logs/application.log` | 日志写满磁盘 |
| 6.2 | 滚动策略 | logback 10MB×10 | 单文件无限增长 |
| 6.3 | 敏感字段 | 日志不含 JWT、Secret、完整凭证 | debug 打印 request body |
| 6.4 | 慢查询 / 慢 API | `attendance.performance.*` 已启用 | 无性能异常可见性 |
| 6.5 | 健康检查 | readiness + 端口 + systemd `Restart=on-failure` | 进程挂死无告警 |
| 6.6 | Nginx 访问日志 | 保留足够用于事件追溯 | 未记录或过早删除 |
| 6.7 | 审计轨迹 | 管理操作（用户/角色变更）可追溯 | 无操作人记录 |

---

## 7. 主机与进程安全

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 7.1 | 运行用户 | 非 root 专用 `deploy` 用户 | root 跑 Java |
| 7.2 | systemd | `Restart=on-failure`；`WorkingDirectory` 正确 | 手动 nohup 无守护 |
| 7.3 | SSH | 密钥登录、禁用密码（或强密码+fail2ban） | 弱密码 SSH |
| 7.4 | 补丁 | OS、OpenSSL、MySQL 定期更新 | 长期不更新 |
| 7.5 | 时区与时间 | NTP 同步（JWT/OAuth 依赖） | 时钟漂移 |
| 7.6 | 仓库目录 | `/opt/AttendanceAgent` 权限合理 | 全局可写 |

---

## 8. CI/CD 与供应链

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 8.1 | GitHub Actions 密钥 | 使用 Secrets，非明文 workflow | Token 写在 yml |
| 8.2 | Workflow 权限 | 最小 `permissions` | `contents: write` 过宽 |
| 8.3 | 依赖审计 | `npm audit` / Maven 依赖检查 | 已知 CVE 未处理 |
| 8.4 | 构建产物 | `frontend/dist` 无 source map 泄露内网路径（按需） | 暴露内部结构 |
| 8.5 | Render CI | `.github/workflows/render-deploy-config.yml` 不泄露 prod 密钥 | 误打印 env |

---

## 9. 备份、灾备与业务连续

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 9.1 | MySQL 备份频率 | 每日，保留策略明确 | 无自动化 |
| 9.2 | secrets 备份 | 加密离线，与代码库分离 | 仅存在服务器单点 |
| 9.3 | `base-config/` | Git 版本化或定期快照 | 仅服务器本地 |
| 9.4 | `uploads/` | 与 DB 恢复策略一致 | 备份 DB 丢图片 |
| 9.5 | RTO/RPO | 有目标或隐含期望文档化 | 未定义 |

---

## 10. 变更与配置治理

| # | 检查项 | 通过标准 | 常见违规 |
|---|--------|----------|----------|
| 10.1 | 域名单一来源 | 只改 `production.yaml` + `apply` | 多处硬编码域名 |
| 10.2 | 手改禁止 | 不手改 `rendered/*.env`、`config.prod.js` | 漂移 |
| 10.3 | 服务器 mode | 生产服务器 `runtime.mode: public` | 误用 `local` |
| 10.4 | 变更记录 | 域名/密钥变更有记录 | 无审计 |
| 10.5 | UAT 隔离 | UAT 与 prod 不同 DB/密钥（推荐） | 完全共用 |

---

## 分支 diff 快速映射

| Diff 路径 | 优先维度 |
|-----------|----------|
| `deploy/**` | 1, 2, 10 |
| `scripts/*prod*` `start.sh` | 1, 3, 7 |
| `backend/src/main/resources/application*.yml` | 3, 4 |
| `.github/workflows/**` | 8 |
| `backend/config/migration/**` | 5 |
| `base-config/permissions*.json` | 4 |
| `docs/运维手册.md` | 文档一致性检查 |
