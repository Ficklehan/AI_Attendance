# 运维安全事件响应 Playbook

AttendanceAgent 生产环境安全事件处置指南。执行破坏性操作前 **必须获得用户确认**。

---

## 事件分级

| 级别 | 定义 | 示例 | 响应目标 |
|------|------|------|----------|
| **SEV-1** | 活跃入侵或大规模数据泄露 | 数据库被拖库、Webshell、勒索 | 1 小时内遏制 |
| **SEV-2** | 凭据泄露或高危漏洞可利用 | `secrets.env` 入 Git、JWT 泄露 | 4 小时内轮换 |
| **SEV-3** | 可疑活动或配置失误 | 异常登录、CORS 误配 | 24 小时内修复 |
| **SEV-4** | 低风险或预警 | 依赖 CVE、证书即将过期 | 计划内处理 |

---

## 通用流程

```text
1. 确认与分级 — 收集症状、时间线、影响范围
2. 遏制 — 断攻击面、封 IP、下线受影响组件
3. 证据保全 — 复制日志（勿只 truncate）
4. 根除 — 清后门、修漏洞、轮换凭据
5. 恢复 — 验证完整性、逐步放量
6. 复盘 — 时间线、根因、检测改进、文档更新
```

**进度清单：**

```text
Incident Progress:
- [ ] 事件分级与指挥人确认
- [ ] 影响范围（用户/数据/系统）已记录
- [ ] 遏制措施已执行
- [ ] 日志与证据已保全
- [ ] 凭据轮换清单已完成
- [ ] 服务恢复并通过验收
- [ ] 复盘报告已输出
```

---

## 场景 A：密钥 / 凭据泄露

**触发**：`secrets.env` 提交 Git、日志打印 Secret、截图外泄、员工离职未轮换。

### 遏制

1. 若已入 Git：评估是否需 `git filter-repo` / 轮换比清历史更优先
2. 限制仓库访问；暂停可疑 CI
3. 若攻击者可能已用 JWT：准备强制全员重新登录

### 轮换顺序（建议）

| 顺序 | 凭据 | 操作 | 验证 |
|------|------|------|------|
| 1 | `FEISHU_APP_SECRET` | 飞书开放平台重置 → 更新 `secrets.env` | `feishu-auth/readiness` |
| 2 | `JWT_SECRET` | 生成新随机串 → `secrets.env` | 旧 token 401；新登录成功 |
| 3 | `DB_PASSWORD` | MySQL 改密 → 更新 `secrets.env` | 后端启动成功 |
| 4 | `MIMO_API_KEY` | 厂商控制台轮换 | 识别任务成功 |
| 5 | OSS AK/SK | 云控制台禁用旧 AK | 上传/读取正常 |

### 恢复

```bash
vim deploy/secrets.env          # 更新凭据
./start.sh apply                # render + 重启
curl -s "https://<HOST>/clockai/api/feishu-auth/readiness"
```

### 复盘检查

- 根因：如何泄露？谁可访问？
- 检测：gitleaks 是否接入 CI？
- 预防：`chmod 600`、pre-commit hook、密钥扫描

---

## 场景 B：疑似服务器入侵

**触发**：异常进程、未知 cron、CPU 飙高、陌生 SSH 登录、Webshell 文件。

### 遏制

1. **不要**先重启（可能丢内存证据）— 除非正在主动外泄
2. 隔离网络：安全组仅留管理 IP；或下线 Nginx `server` 块
3. 保全：`cp -a backend/logs/`、`/var/log/nginx/`、`*` 进程列表、`crontab -l`

```bash
ps auxf > /tmp/incident-ps.txt
ss -tlnp > /tmp/incident-ports.txt
find /opt/AttendanceAgent -mtime -3 -type f > /tmp/incident-recent-files.txt
```

### 根除

1. 从 **已知干净备份** 或 Git 重新部署（非直接覆盖被篡改文件）
2. 全量轮换场景 A 凭据
3. 检查 `uploads/` 是否含 `.php`、`.jsp` 等可执行上传
4. 审计 SSH 密钥、`authorized_keys`

### 恢复

- 干净镜像 + `git clone` 指定 tag
- `./start.sh apply` + 运维手册验收清单 §5.8.6

---

## 场景 C：数据泄露 / 越权访问

**触发**：导出文件公网可访问、IDOR 报告、审计发现批量下载。

### 遏制

1. Nginx 禁止直接访问 `backend/exports/`、`uploads/`（应仅经 API + JWT）
2. 临时收紧 RBAC 或禁用导出接口（需业务确认）
3. 保全访问日志：Nginx + 应用 `user_id` 审计

### 根除

- 修复授权逻辑 → 叠加 `review-security` 代码审查
- 缩短 `export.retention-days`；清理历史导出

---

## 场景 D：DDoS / 资源耗尽

**触发**：识别接口 429 泛滥、磁盘满、MySQL 连接打满。

### 遏制

1. Nginx `limit_req` / 云 WAF 限速
2. 确认 `attendance.recognition.max-per-user` 生效
3. 磁盘：清理 `exports/`、滚动日志

### 恢复

- 监控恢复后逐步放开限流
- 评估 MiMo API 配额与费用

---

## 场景 E：飞书 / OAuth 异常

**触发**：大规模登录失败、回调 404、小程序 network error。

优先对照 `docs/运维手册.md` §8.2、§5.8.8 — 多为配置非入侵。

**安全相关子场景**：

| 现象 | 安全含义 | 动作 |
|------|----------|------|
| 回调 URL 被劫持 | 账户接管 | 核对 `FEISHU_REDIRECT_URI` 与平台一致 |
| 合法域名被篡改 | 中间人 | 核对开放平台与 `PUBLIC_HOST` |
| state 参数缺失校验 | CSRF | 叠加代码审查 `FeishuAuthController` |

---

## 证据保全清单

| 来源 | 路径 / 命令 | 保留时长建议 |
|------|-------------|--------------|
| 应用日志 | `backend/logs/application.log*` | ≥ 90 天 |
| Nginx 访问 | `/var/log/nginx/access.log*` | ≥ 90 天 |
| Nginx 错误 | `/var/log/nginx/error.log*` | ≥ 90 天 |
| systemd | `journalctl -u attendance-backend --since ...` | 导出存档 |
| MySQL 审计 | 若启用 general/audit log | 按合规 |
| Git 历史 | 泄露 commit 的 hash 与 diff | 永久 |

---

## 恢复验收（通用）

```bash
# API
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://<HOST>/clockai/api/config/current-country"

# 飞书
curl -s "https://<HOST>/clockai/api/feishu-auth/readiness"

# TLS
curl -sI "https://<HOST>/" | grep -i strict-transport

# 进程
lsof -iTCP:8080 -sTCP:LISTEN

# 危险开关（配置）
grep -E 'bootstrap-default-admin|allow-public' \
  backend/src/main/resources/application-deploy.yml
```

全部通过后，通知业务方恢复；SEV-1/2 需书面复盘。
