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

```bash
mysql -u root -p < backend/config/init.sql
```

已有库升级（按顺序执行）：

```bash
mysql -u root -p attendance_assistant < backend/config/migration/001_task_sync_and_country.sql
mysql -u root -p attendance_assistant < backend/config/migration/002_task_image_and_anomaly.sql
mysql -u root -p attendance_assistant < backend/config/migration/003_task_status_add_cancelled.sql
```

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

- 前端地址: http://localhost:5173
- 后端API: http://localhost:3000/api
- 默认账号: admin / admin123

## 项目结构

```
├── backend/                    # 后端项目
│   ├── src/
│   │   ├── main/java/
│   │   │   ├── controller/   # 控制器
│   │   │   ├── service/     # 服务层
│   │   │   ├── mapper/      # 数据访问层
│   │   │   ├── entity/      # 实体类
│   │   │   └── util/        # 工具类
│   │   └── resources/
│   │       ├── mapper/      # MyBatis XML
│   │       └── application.yml
│   ├── config/              # 配置文件
│   └── pom.xml
│
├── frontend/                 # 前端项目
│   ├── src/
│   │   ├── api/            # API接口
│   │   ├── components/      # 组件
│   │   ├── composables/    # 组合式函数
│   │   ├── router/        # 路由
│   │   ├── stores/         # 状态管理
│   │   ├── utils/          # 工具函数
│   │   └── views/          # 页面
│   └── package.json
│
├── start.sh                 # Linux启动脚本
├── start.bat                # Windows启动脚本
└── README.md
```

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

### 飞书配置

在 `.env` 文件中配置：

```env
FEISHU_APP_ID=你的飞书应用ID
FEISHU_APP_SECRET=你的飞书应用密钥
```

### AI服务配置

```env
MIMO_API_KEY=你的小米 MiMo API 密钥
MIMO_API_URL=https://api.xiaomimimo.com/v1
MIMO_MODEL=mimo-v2.5
```

## 许可证

MIT License
