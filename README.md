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

- JDK 1.8+
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+

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

### 4. 访问系统

- 前端地址: http://localhost:5175
- 后端 API: http://localhost:3000/api
- 默认账号: admin / admin123（开发环境；生产请改密并关闭 `attendance.bootstrap-default-admin`）

## 项目结构

```
├── base-config/              # 运行时业务配置（飞书/提示词源/权限）
├── backend/
│   ├── config/               # init.sql、migration/、数据库说明
│   ├── src/main/java/        # Spring Boot 业务代码
│   └── src/main/resources/   # application.yml、MyBatis、canonical 提示词
├── frontend/                 # PC Web（Vite 端口 5175）
├── feishu-miniprogram/       # 飞书小程序
├── docs/                     # 架构、数据一致性、SOP 等
├── start.sh / start.bat      # 启动脚本
└── README.md
```

架构与配置说明：[docs/architecture-and-config.md](docs/architecture-and-config.md)

## API文档

### 认证接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| POST /api/auth/login | 登录 | 用户登录 |
| POST /api/auth/register | 注册 | 用户注册 |
| GET /api/auth/profile | 用户信息 | 获取当前用户信息 |

### 任务接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| GET /api/tasks | 列表 | 获取任务列表 |
| GET /api/tasks/:id | 详情 | 获取任务详情 |
| POST /api/tasks/:id/confirm | 确认 | 确认提交任务 |
| POST /api/tasks/:id/retry-sync | 重试 | 重试飞书多维表格同步 |

### 上传接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| POST /api/local/upload-stream | 上传 | SSE流式上传解析 |

## 开发指南

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
| 密钥与数据库 | `backend/.env` 或环境变量 | 下文示例 |
| 飞书/提示词/权限 | `base-config/` | [base-config/README.md](base-config/README.md) |
| 识别提示词（运行时） | MySQL `recognition_prompt` | 启动时自动播种 |
| 架构总览 | — | [docs/architecture-and-config.md](docs/architecture-and-config.md) |

### 飞书配置（环境变量 + feishu.md）

```env
FEISHU_APP_ID=你的飞书应用ID
FEISHU_APP_SECRET=你的飞书应用密钥
```

各国 Bitable Token / 字段映射在 `base-config/feishu.md` 按国家维护。

### AI 服务配置

```env
MIMO_API_KEY=你的小米 MiMo API 密钥
MIMO_API_URL=https://api.xiaomimimo.com/v1
MIMO_MODEL=mimo-v2.5
```

## 许可证

MIT License
