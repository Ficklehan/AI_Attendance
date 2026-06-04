# AI考勤智能助手

基于飞书平台和AI视觉识别技术的考勤数据自动化处理系统。

## 技术栈

### 后端
- JDK 1.8
- Spring Boot 2.7.x
- MyBatis 3.5.x
- MySQL 8.0
- JWT认证

### 前端
- Vue 3
- Vite
- Element Plus
- Pinia

## 功能特性

- AI智能识别：MiMo Vision API流式解析考勤表格
- 飞书集成：消息通知、多维表格数据写入
- 任务管理：创建、编辑、确认、删除
- 配置灵活：支持多国家/地区配置
- 审计追溯：完整的操作日志

## 快速开始

### 环境要求

- JDK 1.8（仅支持 Java 8，不支持更高版本）
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+

**JDK 8 自动安装（推荐）：** 项目会在首次启动时下载 Zulu JDK 8 到本地 `.jdk/` 目录，无需手动配置 `JAVA_HOME`：

```bash
bash scripts/setup-jdk8.sh   # 可选：提前安装
bash scripts/mvn-jdk8.sh compile -DskipTests
./start.sh
```

若本机已安装 JDK 8，设置 `JAVA_HOME` 指向 1.8 即可；`start.sh` 会优先使用已有的 JDK 8。

### 1. 初始化数据库

**全新安装：**

```bash
mysql -u root -p < backend/config/init.sql
```

**已有库升级：** 按顺序执行 `backend/config/migration/001`–`006` 及 `migrate_recognition_prompt.sql`，或：

```bash
chmod +x backend/config/migrate_all.sh
./backend/config/migrate_all.sh attendance_assistant
```

详见 [backend/config/README.md](backend/config/README.md) 与 [docs/architecture-and-config.md](docs/architecture-and-config.md)。

### 2. 配置环境变量

```bash
# 复制配置示例
cp backend/.env.example backend/.env

# 编辑配置
vi backend/.env
```

### 3. 启动服务

**Linux/Mac:**
```bash
chmod +x start.sh
./start.sh
```

**Windows:**
```batch
start.bat
```

### 4. 访问系统（本地）

- PC 前端: http://localhost:5175/attendance/（登录页 `/attendance/login`）
- 后端 API: http://localhost:8080/attendance/api（例：`POST .../attendance/api/auth/login`）
- 默认账号: admin / admin123（**仅 dev**；生产请改密并关闭 bootstrap）
- 小程序本地调试: `feishu-miniprogram/config.js` → `USE_PUBLIC_API=false`

### 5. 公网 / 生产

```bash
vim deploy/environments/production.yaml   # 改 public.host
./start.sh restart-prod                   # 自动 render + 重启，无需手跑 render 命令
```

服务器密钥：`cp deploy/secrets.example deploy/secrets.env`。详见 [deploy/README.md](deploy/README.md)。

小程序改域名后需重新上传飞书开发者工具（`USE_PUBLIC_API=true`）。

## 项目结构

```
├── base-config/              # 运行时业务配置（飞书/提示词源/权限）
├── backend/
│   ├── config/               # init.sql、migration/、数据库说明
│   ├── src/main/java/        # Spring Boot 业务代码
│   └── src/main/resources/   # application.yml、MyBatis、canonical 提示词
├── frontend/                 # PC Web（Vite 端口 5175）
├── feishu-miniprogram/       # 飞书小程序（config.js 本地 + config.prod.js render）
├── deploy/                   # 公网域名清单、render 脚本、部署说明
├── docs/                     # 架构、数据一致性、SOP 等
├── package.json              # npm run render:deploy:*
├── start.sh / start.bat      # 启动脚本（含 render-deploy）
└── README.md
```

架构与配置说明：[docs/architecture-and-config.md](docs/architecture-and-config.md)

## API文档

### 认证接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| POST /attendance/api/auth/login | 登录 | 用户登录 |
| POST /attendance/api/auth/register | 注册 | 用户注册 |
| GET /attendance/api/auth/profile | 用户信息 | 获取当前用户信息 |

### 任务接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| GET /attendance/api/tasks | 列表 | 获取任务列表 |
| GET /attendance/api/tasks/:id | 详情 | 获取任务详情 |
| POST /attendance/api/tasks/:id/confirm | 确认 | 确认提交任务 |
| POST /attendance/api/tasks/:id/retry-sync | 重试 | 重试飞书多维表格同步 |

### 上传接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| POST /attendance/api/local/upload-stream | 上传 | SSE流式上传解析 |

## 开发指南

克隆仓库后需本地安装依赖：`backend` 执行 `mvn` 构建，`frontend` 执行 `npm install`。`node_modules`、`target`、日志与上传目录已由 `.gitignore` 排除，不会进入版本库。

### 后端开发

```bash
cd backend
mvn clean compile
mvn spring-boot:run
```

### 前端开发

```bash
cd frontend
npm install
npm run dev
```

### 运行测试

```bash
cd backend
mvn test
```

## 配置说明

| 类型 | 位置 | 文档 |
|------|------|------|
| **公网域名 / 飞书回调 / 小程序 API** | `deploy/environments/production.yaml` | [deploy/README.md](deploy/README.md) |
| 密钥与数据库 | `backend/.env` 或环境变量 | 下文示例 |
| 飞书/提示词/权限 | `base-config/` | [base-config/README.md](base-config/README.md) |
| 识别提示词（运行时） | MySQL `recognition_prompt` | 启动时自动播种 |
| 架构总览 | — | [docs/architecture-and-config.md](docs/architecture-and-config.md) |
| **运维手册** | — | [docs/运维手册.md](docs/运维手册.md) |

### 飞书配置

公网域名与飞书回调 URL 由 render 自动生成；**不要在** `backend/.env` 里写生产域名（本地 dev 除外）。

```bash
cp deploy/secrets.example deploy/secrets.env
```

各国 Bitable Token / 字段映射在 `base-config/feishu.md` 按国家维护。  
后端会在构建时将 `base-config/*.md` 打包到 `resources`，运行时若未找到外部 `base-config/` 会自动从内置资源初始化，无需手动挂载。

### AI 服务配置

```env
MIMO_API_KEY=你的小米 MiMo API 密钥
MIMO_API_URL=https://api.xiaomimimo.com/v1
MIMO_MODEL=mimo-v2.5
```

## 许可证

MIT License
