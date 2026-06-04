# AttendanceAgent 安全审计报告

**审计日期：** 2026-06-03  
**审计范围：** 全栈（Spring Boot 后端、Vue Web 前端、飞书小程序、移动端 H5）  
**审计方法：** 静态代码审计 (SAST)、配置审查、威胁建模、依赖分析、渗透测试思路验证  

---

## 执行摘要

AttendanceAgent 在认证架构（JWT + BCrypt）、任务级授权（TaskAccessService）、MyBatis 参数化查询等方面具备基础安全设计。但本次审计发现 **3 项 Critical**、**9 项 High** 级别问题，主要集中在：**路径遍历导致任意文件读取**、**敏感数据泄露（Git/日志/API）**、**越权访问飞书凭证**、**OAuth 与 Token 传递缺陷**、**生产加固不足**。

建议在上线公网或接入真实考勤数据前，优先修复 Critical/High 项，并完成生产环境配置核查。

---

## Critical（需立即修复）

### [C-01] 路径遍历 — 任意本地文件读取

**位置：** `LocalUploadController.getImage`  
**文件：** `backend/src/main/java/com/attendance/controller/LocalUploadController.java:538-576`

**问题：** 读取图片时未做 `normalize()` + `startsWith(base)` 校验，而同项目的 `TaskService.readUploadedImageBytes` 已正确实现该防护。

```538:543:backend/src/main/java/com/attendance/controller/LocalUploadController.java
    @GetMapping("/image/{fileKey}")
    public void getImage(@PathVariable String fileKey, HttpServletResponse response) throws IOException {
        taskAccessService.requireFileAccess(fileKey);
        try {
            Path uploadPath = Paths.get("./uploads");
            Path filePath = uploadPath.resolve(fileKey);
```

**攻击路径（渗透验证思路）：**
1. 创建任务后，在 `POST /tasks/{taskId}/confirm` 的 `imageUrls` 中注入 `../../../etc/passwd` 等路径（`updateTaskImageUrls` 无路径校验）。
2. `requireFileAccess` 因 `image_urls LIKE` 匹配而通过。
3. 请求 `GET /local/image/../../../etc/passwd?token=JWT` 读取服务器任意可读文件。

**影响：** 已认证用户（含被入侵的普通账号）可读取服务器上的配置文件、`.env`、数据库凭证、其他用户上传的考勤原图等。

**修复建议：** 与 `TaskService.readUploadedImageBytes` 对齐，强制路径规范化；同时在校验 `imageUrls` 时拒绝含 `..`、绝对路径、非预期字符的 key。

---

### [C-02] 考勤导出文件已提交至 Git 仓库

**位置：** `backend/exports/`（已跟踪文件）

```
backend/exports/admin001/9bee299565c74ce5b3ed833e19e7f2ea.xlsx
backend/exports/ea41fb93b0424355a6bcf6c323d61cf6/36dbe75be36e4be6a3418b8fbb2908c1.xlsx
backend/exports/ea41fb93b0424355a6bcf6c323d61cf6/beaaf1650db94f8282fabf6f6a6ef1c8.xlsx
```

**问题：** `.gitignore` 仅忽略 `backend/uploads/`，未忽略 `backend/exports/`。导出 Excel 含员工姓名、工号、考勤时间等 PII。

**影响：** 任何能访问代码仓库的人均可获取真实考勤数据，构成数据泄露与合规风险（GDPR/个人信息保护法）。

**修复建议：** 将 `backend/exports/` 加入 `.gitignore`；从 Git 历史中清除已提交文件（`git filter-repo` 或 BFG）；轮换相关凭证。

---

### [C-03] 生产环境 SQL 明文日志

**位置：** `backend/src/main/resources/application.yml:26-27`

```yaml
mybatis:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

**问题：** 该配置在**基础 profile** 中生效，生产 profile 未覆盖。所有 SQL 及绑定参数（含用户 PII、密码 hash 查询等）会写入 stdout/日志文件。

**影响：** 日志系统、运维人员、日志聚合平台均可接触敏感数据；日志泄露等同数据库部分泄露。

**修复建议：** 仅在 `application-dev.yml` 启用；生产使用 `slf4j` 或关闭 SQL 日志；对日志做脱敏与访问控制。

---

## High（高优先级）

### [H-01] 飞书 Bitable Token 对普通用户泄露

**位置：** `ConfigController.getCountryBundle`  
**文件：** `backend/src/main/java/com/attendance/controller/ConfigController.java:128-132`

**问题：** `GET /config/country-bundle` 无 `requireAdmin()`，返回完整 `CountryConfigBundle`，含 `appToken`、`tableId`、字段映射及 AI 提示词。

**影响：** 任意已登录用户可获取飞书多维表格访问凭证，读写或导出企业考勤底表数据。

**修复建议：** 管理端专用 DTO，对非 admin 仅返回必要字段（国家代码、是否已配置）；敏感 token 仅 admin 可见或完全不返回前端。

---

### [H-02] 任意用户可修改全局工作国家

**位置：** `ConfigController.setCurrentCountry`  
**文件：** `backend/src/main/java/com/attendance/controller/ConfigController.java:101-112`

**问题：** `PUT /config/current-country` 未调用 `requireAdmin()`，任何 authenticated 用户可修改全局 `current_country`（持久化到配置文件）。

**影响：** 影响全员识别规则、飞书同步目标国家，造成业务混乱或数据写入错误国家的 Bitable。

**修复建议：** 增加 admin 校验，或改为 per-user / per-session 国家偏好。

---

### [H-03] JWT 通过 URL Query 传递

**位置：**  
- `JwtAuthenticationFilter.java:66-68`  
- `frontend/src/utils/imageUrl.js:16-17`  
- `feishu-miniprogram/utils/imageUrl.js:34-35`

**问题：** 支持 `?token=` 传 JWT；前端/小程序在图片 URL 中拼接 token。

**影响：** Token 出现在浏览器历史、Referer 头、代理/网关/WAF 日志、CDN 访问日志中，导致会话劫持。

**修复建议：** 使用短期、scoped 的图片访问签名 URL（如 HMAC + 过期时间）；或 Cookie + SameSite（需评估跨域）；禁止 query token 或仅允许一次性 exchange。

---

### [H-04] 飞书 OAuth 未校验 state（登录 CSRF）

**位置：** `FeishuAuthController.java:57-72`

**问题：** `/login` 生成 `state` 但未存入 session/redis；`/callback` 接收 `state` 但不验证。

**攻击路径：** 攻击者完成 OAuth 后将 callback URL 诱使受害者访问，受害者浏览器以攻击者身份登录。

**修复建议：** 服务端存储 state 并校验；callback 使用 POST + PKCE（如飞书支持）。

---

### [H-05] 飞书回调将 JWT 写入 HTML 与 URL

**位置：** `FeishuAuthController.generateLoginSuccessHtml`  
**文件：** `backend/src/main/java/com/attendance/controller/FeishuAuthController.java:126-158`

**问题：** Token 与 userInfo 嵌入 HTML script 并重定向到 `http://localhost:5175/feishu/callback?token=...&userInfo=...`（硬编码 localhost）。

**影响：** Token 经 URL 传递；生产环境 redirect 错误；若 userInfo 被污染存在 XSS 注入面。

**修复建议：** 使用 authorization code 交换模式；redirect URI 配置化；禁止在 URL 传递长期 JWT。

---

### [H-06] CORS 配置过于宽松

**位置：** `WebConfig.java:14-20`

```java
.allowedOriginPatterns("*")
.allowCredentials(true)
```

**问题：** 允许任意 Origin 且携带凭证，在存在 Cookie 会话或浏览器特殊行为时存在跨域风险。

**修复建议：** 生产环境白名单具体域名；JWT 纯 Header 场景可评估 `allowCredentials(false)`。

---

### [H-07] 开放自助注册，无审批/邀请机制

**位置：** `AuthController.register` + `SecurityConfig` `/auth/**` permitAll

**问题：** 任何人可注册 `role=user` 账号并上传/识别考勤表、调用 AI API、访问部分配置接口。

**影响：** 未授权人员进入系统；AI API 费用滥用；内部数据被非员工处理。

**修复建议：** 生产关闭公开注册，改为 admin 邀请、飞书 SSO 限定租户、或企业邮箱域校验。

---

### [H-08] 依赖栈 EOL 与已知漏洞风险

**组件：**
| 组件 | 版本 | 风险 |
|------|------|------|
| Spring Boot | 2.7.18 | OSS 支持已结束 |
| snakeyaml | 1.30 | 多个反序列化 CVE |
| Tomcat embed | 9.0.83 | 需持续跟踪 CVE |
| fastjson | 2.0.45 | 历史安全问题，需锁定版本 |

**修复建议：** 规划升级 Spring Boot 3.x；运行 `mvn org.owasp:dependency-check-maven:check`；启用 Dependabot/Snyk。

---

### [H-09] 默认管理员自动引导（生产误配风险）

**位置：** `application-dev.yml:28-30`，`DefaultAdminBootstrap.java`

**问题：** dev 默认 `bootstrap-default-admin: true`，密码默认 `admin123`。若生产 profile 误用或未显式关闭，将重置/创建弱口令 admin。

**修复建议：** 在 `application-prod.yml` 显式 `bootstrap-default-admin: false`；首次部署强制改密；禁用默认密码。

---

## Medium（中优先级）

### [M-01] `/uploads/**` 公开访问规则

**位置：** `SecurityConfig.java:41`

**问题：** 静态目录规则为 permitAll。当前主要走 `/local/image/{key}`，但若后续增加静态资源映射或 Nginx 直出 uploads，将全部公开。

**修复建议：** 移除 permitAll；统一走鉴权接口。

---

### [M-02] 无 API 速率限制

**影响面：** `/auth/login`、`/auth/register`、上传识别、`/chat/image`、导出任务。

**影响：** 暴力破解、注册垃圾账号、MiMo API 费用耗尽（DoS/成本攻击）。

**修复建议：** 网关或 Spring 层限流（Bucket4j/Redis）；登录失败锁定；AI 接口 per-user quota。

---

### [M-03] Bitable 连接验证接口滥用

**位置：** `BitableController` — 任意 authenticated 用户可传入任意 `appToken/tableId`，后端使用**租户** Feishu 凭证探测。

**影响：** 滥用企业 Feishu API 配额；探测第三方 Bitable 是否存在（信息泄露）。

**修复建议：** 限制为 admin；或仅允许验证已配置在本系统的 token。

---

### [M-04] JWT 无吊销机制，有效期 7 天

**位置：** `application.yml` — `jwt.expiration: 604800000`

**影响：** Token 泄露后无法主动失效（改密不影响旧 token）。

**修复建议：** 缩短过期时间 + Refresh Token；或 Redis 黑名单；敏感操作重新验证密码。

---

### [M-05] JWT Secret 弱密钥填充

**位置：** `JwtUtil.getSigningKey` — 不足 32 字节时零填充。

**影响：** 弱 secret 可被暴力破解伪造 JWT。

**修复建议：** 启动时校验 secret 长度 ≥256 bit；缺失则拒绝启动。

---

### [M-06] 权限矩阵对普通用户可读

**位置：** `PermissionController.getRolePermissions` — 无 admin 校验。

**影响：** 信息泄露，辅助攻击者了解权限模型。

---

### [M-07] 敏感信息写入 INFO 日志

**位置：** `FeishuAuthController` — 完整飞书用户信息 JSON 打日志。

**修复建议：** 生产 DEBUG 关闭；日志脱敏（open_id、email）。

---

### [M-08] Token 存储于 localStorage

**位置：** `frontend/src/utils/auth.js`

**影响：** 若出现 XSS，token 可被窃取。当前未发现 `v-html` 使用，风险可控但仍高于 HttpOnly Cookie。

---

### [M-09] 开发服务器绑定 0.0.0.0

**位置：** `frontend/vite.config.js:21`

**影响：** 局域网可访问开发实例，若同时运行后端可能暴露调试环境。

---

## Low / Informational

| ID | 发现 | 说明 |
|----|------|------|
| L-01 | CSRF 禁用 | JWT 无 Cookie 场景可接受，需确保不使用 Cookie 认证 |
| L-02 | `/auth/profile` 等在 permitAll 下 | 实际依赖 Filter 注入身份，建议收紧为 authenticated |
| L-03 | H2 依赖在 runtime | 确保生产不会误连 H2 |
| L-04 | 前端 admin 路由仅客户端校验 | 后端 admin API 多数有 `requireAdmin()`，设计合理 |
| L-05 | 无 CSP / X-Frame-Options | 需在 Nginx/网关配置 |
| L-06 | `allow-simulated-recognition` | 确保生产为 false，避免假数据进入业务流程 |
| I-01 | BCrypt 密码哈希 | ✅ 良好实践 |
| I-02 | MyBatis `#{}` 参数化 | ✅ 未发现 SQL 注入 |
| I-03 | TaskAccessService 任务归属校验 | ✅ 核心 IDOR 防护到位 |
| I-04 | ImageUploadValidator 魔数校验 | ✅ 上传类型基础校验 |
| I-05 | 随机 UUID 任务/用户 ID | ✅ 避免可猜测 ID |

---

## 渗透测试验证清单（Post-fix Retest）

| # | 测试项 | 预期结果 |
|---|--------|----------|
| 1 | `GET /local/image/..%2F..%2Fetc%2Fpasswd` | 403/404 |
| 2 | 普通用户 `GET /config/country-bundle` | 不含 appToken |
| 3 | 普通用户 `PUT /config/current-country` | 403 |
| 4 | 无 token `GET /tasks` | 401 |
| 5 | 用户 A 访问用户 B 的 taskId | 403 |
| 6 | 100 次连续 login 失败 | 429 或锁定 |
| 7 | 生产日志 | 无 SQL 明文、无 PII |
| 8 | OAuth callback 伪造 state | 拒绝 |
| 9 | Git 仓库 | 无 exports/uploads 数据文件 |

---

## 修复优先级路线图

```
Week 1 (Critical):  C-01 路径遍历 → C-02 Git 清理 → C-03 日志
Week 2 (High):      H-01/H-02 越权 → H-03/H-04/H-05 Token/OAuth
Week 3 (High):      H-06/H-07/H-09 配置加固 → 依赖扫描
Week 4 (Medium):    限流、JWT 吊销、Bitable 接口收紧
```

---

*本报告由静态审计生成。运行时 WAF、反向代理、K8s NetworkPolicy 等基础设施控制未在仓库中验证，部署时需另行核查。*
