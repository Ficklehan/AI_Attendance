# 运维安全审计 — 输出示例

---

## 示例 1：生产快速体检（config + infra）

```markdown
# 运维安全审计 — 生产环境快速体检

## 执行摘要
- **P0×1**：`deploy/secrets.env` 权限为 `644`，同组用户可读数据库与 JWT 密钥。
- **P1×2**：Nginx 未配置 HSTS；`backend/exports/` 目录权限过宽。
- 飞书集成与 Spring 生产安全开关配置正确；readiness 返回 `feishuConfigured: true`。

## 范围与假设
- 环境：生产（`otws-de.igofoex.com`）
- 静态审计 + 用户提供的 `stat` / `curl` 输出
- 未执行破坏性变更

## 信任边界
[略 — Mermaid 见 SKILL.md]

## 发现

### P0 — Critical
| ID | 位置 | 影响 | 证据 | 建议 | 验证 |
|----|------|------|------|------|------|
| OPS-001 | `deploy/secrets.env` | 密钥泄露给同服务器其他用户 | `stat` 显示 `644` | `chmod 600 deploy/secrets.env` | `stat` 显示 `600` |

### P1 — High
| ID | 位置 | 影响 | 证据 | 建议 | 验证 |
|----|------|------|------|------|------|
| OPS-002 | Nginx server 块 | 降级攻击、会话劫持风险 | 响应头无 `Strict-Transport-Security` | 添加 `add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;` | `curl -sI` 可见 HSTS |
| OPS-003 | `backend/exports/` | 本地用户可读导出 Excel | `ls -la` 显示 `drwxr-xr-x` | `chmod 750` + 确保运行用户属主 | 非属主用户无法 `ls` |

## 加固路线图
| 阶段 | 时间 | 项目 |
|------|------|------|
| 立即 | 今天 | OPS-001 secrets 权限 |
| 本周 | 3 天内 | OPS-002 Nginx HSTS；OPS-003 exports 权限 |
| 本月 | — | 建立密钥季度轮换记录；MySQL 备份加密验证 |

## 运维验证清单
- [ ] `chmod 600 deploy/secrets.env`
- [ ] Nginx `nginx -t && reload`
- [ ] `curl` readiness 200
- [ ] 管理员密码非 `admin123`（抽样登录验证）

## 开放问题
- [ ] MySQL 是否仅监听 `127.0.0.1`？（需服务器 `ss -tlnp` 确认）
```

---

## 示例 2：部署变更 branch 审查

```markdown
# 运维安全审计 — branch `feat/oss-storage`

## 执行摘要
变更引入 OSS 存储，整体方向正确；需补充桶策略最小权限说明，并确保 `OSS_ACCESS_KEY_SECRET` 仅来自 `secrets.env`。

## 发现

### P1 — High
| ID | 位置 | 影响 | 证据 | 建议 |
|----|------|------|------|------|
| OPS-010 | `application.yml` | OSS 密钥经 env 注入，但文档未说明桶 ACL | 新增 `ATTENDANCE_STORAGE_TYPE=oss` 无运维手册章节 | 在 `docs/运维手册.md` §5.7 补充：桶禁止公共写、仅后端 AK 读写在 `attendance/` 前缀 |

### P2 — Medium
| ID | 位置 | 影响 | 证据 | 建议 |
|----|------|------|------|------|
| OPS-011 | `deploy/secrets.example` | 示例 AK 占位符易被误当作真实值 | 使用 `YOUR_OSS_ACCESS_KEY_ID` 明确占位 | 保持与 `secrets.example` 其他字段风格一致 |

## What went well
- 密钥仍通过环境变量注入，未硬编码在 yml
- 默认 `local` 存储，生产需显式开启 OSS

## Test plan
- [ ] 本地 `local` 模式上传仍正常
- [ ] 生产 OSS 桶策略：禁止 `s3:PutObject` 匿名
- [ ] 轮换 AK 后 `./start.sh apply` 恢复服务
```

---

## 示例 3：密钥泄露事件（incident 模式）

```markdown
# 安全事件处置 — FEISHU_APP_SECRET 疑似泄露（SEV-2）

## 时间线
- 14:00 发现内部文档截图含完整 `FEISHU_APP_SECRET`
- 14:15 确认截图已外传，启动轮换

## 已执行遏制
- [x] 飞书开放平台重置 App Secret
- [x] 更新 `deploy/secrets.env`
- [x] `./start.sh apply` 重启后端

## 待执行
- [ ] 审查 14:00 前 Nginx 日志异常 `feishu-auth` 调用
- [ ] 通知管理员观察异常登录（JWT 未轮换，可选）

## 验证
```bash
curl -s "https://otws-de.igofoex.com/attendance/api/feishu-auth/readiness"
# feishuConfigured: true
```

## 复盘改进项
1. 禁止在飞书文档粘贴 `secrets.env` 全文
2. CI 增加 gitleaks
3. 季度密钥轮换日历
```
