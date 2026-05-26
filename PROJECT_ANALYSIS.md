# 考勤智能助手 - 完整项目分析文档

## 一、项目概述

### 1.1 项目简介

**考勤智能助手** 是一个基于飞书平台和 AI 视觉识别技术的考勤数据自动化处理系统。系统通过接收用户上传的考勤表格图片，利用 AI 模型自动识别并提取考勤数据，最终将处理后的数据写入飞书多维表格（Bitable）进行存储和管理。

### 1.2 核心功能

1. **AI 智能识别**：通过 MiMo Vision API 流式解析考勤表格图片，逐行提取考勤记录
2. **飞书集成**：与飞书消息、飞书多维表格深度集成
3. **数据管理**：提供任务管理、数据编辑、导出等完整的工作流
4. **配置灵活**：支持按国家/地区配置不同的多维表格和字段映射
5. **审计追溯**：完整的操作审计日志，记录所有关键操作
6. **服务管理**：内置服务管理界面，支持一键启停前后端服务

### 1.3 技术栈

| 层级 | 技术选型 |
|------|----------|
| 后端框架 | Node.js + Express |
| 前端框架 | React 18 + Vite |
| UI 样式 | Tailwind CSS |
| 数据库 | MySQL 8.0+ |
| 缓存层 | MySQL Connection Pool |
| 日志系统 | Winston |
| AI 服务 | MiMo Vision API (流式接口) |
| 飞书 SDK | @larksuiteoapi/node-sdk |
| 认证方式 | JWT Token |
| 文件存储 | 本地文件系统 (uploads 目录) |

---

## 二、系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (React)                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ 首页/上传 │  │ 任务列表 │  │ 数据编辑 │  │ 配置管理 │       │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘       │
└─────────────────────────────────────────────────────────────────┘
                              │
                         HTTP/SSE
                              │
┌─────────────────────────────────────────────────────────────────┐
│                        后端 (Express)                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    API 路由层                             │   │
│  │  /api/auth  /api/tasks  /api/local  /api/config  /api/audit │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    服务层 (Services)                      │   │
│  │  AIParserService | BitableService | FeishuService       │   │
│  │  TaskService | FeishuWebSocketService                   │   │
│  └─────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    数据层 (Models)                        │   │
│  │  TaskModel | UserModel | ConfigModel | AuditLogModel    │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
         │                │                    │
         ▼                ▼                    ▼
┌─────────────┐   ┌─────────────┐     ┌─────────────┐
│   MySQL     │   │ MiMo Vision │     │ 飞书开放平台 │
│   Database  │   │    API      │     │  (Bitable)  │
└─────────────┘   └─────────────┘     └─────────────┘
```

### 2.2 数据流向

```
用户上传图片
     │
     ▼
┌─────────────────┐
│  前端图片压缩    │  ◀── Client-side 智能压缩 (根据大小调整质量和尺寸)
└────────┬────────┘
         │
         ▼ (Base64 / FormData)
┌─────────────────┐
│  后端接收图片    │
│  /api/local/upload-stream
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  创建任务记录    │  ◀── TaskModel.create()
│  保存到 uploads │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  AI 流式解析    │
│  AIParserService│
│  parseImageStreamByLine()
└────────┬────────┘
         │
         ├── SSE 实时推送 ──▶ 前端实时显示识别结果
         │
         ▼
┌─────────────────┐
│  保存原始数据    │
│  TaskModel.updateRawData()
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  用户编辑确认    │
│  前端页面编辑    │
└────────┬────────┘
         │
         ▼ (POST /api/tasks/:taskId/confirm)
┌─────────────────┐
│  数据验证        │
│  必填字段检查    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  写入飞书表格    │
│  BitableService │
│  batchWriteRecords()
└─────────────────┘
```

---

## 三、后端详细分析

### 3.1 目录结构

```
backend/
├── src/
│   ├── config/           # 配置管理
│   │   ├── ConfigManager.js    # 配置管理器（支持文件/数据库两种模式）
│   │   └── database.js         # MySQL 连接池配置
│   ├── controllers/       # 控制器层
│   │   ├── AuthController.js
│   │   ├── FeishuWebhookController.js
│   │   ├── TaskController.js
│   │   └── UserController.js
│   ├── middleware/        # 中间件
│   │   └── feishuAuth.js        # 飞书签名验证
│   ├── middlewares/       # 中间件（ES6）
│   │   ├── AuthMiddleware.js    # JWT 认证
│   │   └── sanitizeQuery.js     # 查询参数清理
│   ├── models/           # 数据模型层
│   │   ├── AuditLogModel.js
│   │   ├── ConfigModel.js
│   │   ├── TaskModel.js
│   │   └── UserModel.js
│   ├── routes/           # 路由定义
│   │   ├── auth.js
│   │   ├── config.js
│   │   ├── feishuAuth.js
│   │   ├── local.js
│   │   ├── service.js
│   │   ├── task.js
│   │   ├── users.js
│   │   └── webhook.js
│   ├── services/         # 业务服务层 ⭐ 核心逻辑
│   │   ├── AIParserService.js       # AI 解析服务
│   │   ├── BitableService.js        # 飞书多维表格服务
│   │   ├── FeishuService.js         # 飞书消息服务
│   │   ├── FeishuWebSocketService.js # 飞书 WebSocket 服务
│   │   ├── ServiceManager.js        # 服务管理
│   │   └── TaskService.js          # 任务服务
│   ├── utils/            # 工具类
│   │   ├── FeishuConfigManager.js  # 飞书配置管理器（支持多国家）
│   │   ├── PromptManager.js
│   │   ├── RecordNoGenerator.js    # 任务号生成器
│   │   └── logger.js              # Winston 日志封装
│   └── index.js          # 应用入口
├── config/               # SQL 初始化脚本
│   ├── init.sql
│   ├── init_audit_log.sql
│   └── init_config.sql
├── .env                  # 环境变量配置
├── package.json
└── config.yaml           # YAML 配置文件（文件模式使用）
```

### 3.2 核心服务详解

#### 3.2.1 AIParserService（AI 解析服务）

**文件位置**：[backend/src/services/AIParserService.js](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/src/services/AIParserService.js)

**核心方法**：`parseImageStreamByLine(base64Image, options)`

**功能描述**：调用 MiMo Vision API 对考勤图片进行流式解析，逐行提取考勤记录。

**解析策略**：

```javascript
// 1. 流式调用 AI 接口
// 2. 实时解析 SSE 响应
// 3. 逐行提取单个记录 [...] 模式
// 4. 支持最多 5 轮自动续写（响应截断时）
// 5. 使用 Set 进行 O(1) 重复检测
```

**数据标准化流程**：

```
AI 原始输出: ["1","张三","中介A","08:00","18:00",...]
         │
         ▼ normalizeRecord()
         │
┌────────┴────────┐
│ 1. 数组转对象    │
│ 2. 日期标准化    │  ◀── 支持多种格式 → YYYY-MM-DD
│ 3. 时间标准化    │  ◀── 22H, 22:00, 2200 → 22:00
│ 4. 夜班检测      │  ◀── 到达 18:00+ 且 离开 ≤12:00
│ 5. 风险等级评估   │  ◀── high/medium/none
│ 6. 智能标记生成   │  ◀── 正常/手写/模糊/夜班
└────────┬────────┘
         │
         ▼
标准化后的记录对象
```

**记录字段映射**：

| 数组索引 | 字段名 | 类型 | 说明 |
|----------|--------|------|------|
| 0 | NO | string | 工号（必填） |
| 1 | NOM_PRENOM | string | 姓名 |
| 2 | AGENCE_INTERIMAIRE | string | 中介机构 |
| 3 | HORAIRES_DU_TRAVAIL | string | 班次 |
| 4 | Date | string | 日期（YYYY-MM-DD） |
| 5 | ARRIVEE | string | 到达时间（HH:MM） |
| 6 | DEPAR | string | 离开时间（HH:MM） |
| 7 | PAUSE | number | 休息时间（分钟） |
| 8 | CHECKER | string | 检查器 |
| 9 | Mark | string | 标记 |
| 10 | isDeleted | boolean | 是否删除 |

**生成的计算字段**：

- `ARRIVEE_DATE`：到达日期（夜班时为次日）
- `DEPAR_DATE`：离开日期
- `ARRIVEE_DATETIME`：到达日期时间
- `DEPAR_DATETIME`：离开日期时间
- `SmartMark`：智能标记（正常/手写/模糊/夜班/未出勤）
- `riskLevel`：风险等级（high/medium/none）
- `anomalies`：异常信息数组

#### 3.2.2 BitableService（飞书多维表格服务）

**文件位置**：[backend/src/services/BitableService.js](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/src/services/BitableService.js)

**核心方法**：`batchWriteRecords(records, countryCode)`

**功能描述**：将考勤记录批量写入飞书多维表格。

**字段映射配置**：

```javascript
// 默认字段映射
const DEFAULT_FIELD_MAPPING = [
  { aiField: "NO", feishuField: "NO", type: "string", required: true },
  { aiField: "NOM_PRENOM", feishuField: "NOM PRENOM", type: "string" },
  { aiField: "AGENCE_INTERIMAIRE", feishuField: "AGENCE D'INTERIMAIR", type: "string" },
  { aiField: "HORAIRES_DU_TRAVAIL", feishuField: "HORAIRES DU TRAVAI", type: "string" },
  { aiField: "Date", feishuField: "Date", type: "date" },
  { aiField: "ARRIVEE_DATETIME", feishuField: "ARRIVE", type: "datetime" },
  { aiField: "DEPAR_DATETIME", feishuField: "DEPAR", type: "datetime" },
  { aiField: "PAUSE", feishuField: "PAUS", type: "number" },
  { aiField: "CHECKER", feishuField: "CHECKER", type: "string" },
  { aiField: "SmartMark", feishuField: "Mark", type: "string" }
];
```

**写入流程**：

```
1. 验证记录数量（最多 1000 条/次）
2. 获取字段映射（支持按国家配置）
3. 格式转换：
   - date → Unix timestamp (毫秒)
   - datetime → Unix timestamp (毫秒)
   - number → parseFloat
   - string → String
4. 分批写入（100条/批）
5. 失败重试（最多 2 次）
```

#### 3.2.3 FeishuService（飞书消息服务）

**文件位置**：[backend/src/services/FeishuService.js](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/src/services/FeishuService.js)

**核心方法**：

- `getImageUrl(fileKey)`：获取飞书图片临时 URL
- `sendTextMessage(userId, text)`：发送文本消息
- `sendCardMessage(userId, card)`：发送卡片消息
- `sendSuccessNotification(userId, count)`：发送成功通知

#### 3.2.4 TaskService（任务服务）

**文件位置**：[backend/src/services/TaskService.js](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/src/services/TaskService.js)

**核心方法**：

- `processImage(userId, fileKey)`：处理飞书机器人接收的图片
- `processTaskAsync(taskId, userId, fileKey)`：异步处理任务
- `getTask(taskId, page, pageSize)`：获取任务详情
- `confirmTask(taskId, confirmedData, userId, userInfo)`：确认并提交任务

**任务状态机**：

```
创建任务 → processing → processed → confirmed
                │            │
                └────┴────→ failed
```

#### 3.2.5 FeishuConfigManager（飞书配置管理器）

**文件位置**：[backend/src/utils/FeishuConfigManager.js](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/src/utils/FeishuConfigManager.js)

**功能描述**：支持按国家/地区配置不同的飞书多维表格。

**配置文件格式**（`base-config/feishu.md`）：

```markdown
# 飞书多维表配置

## 全局默认配置

```yaml
default:
  bitable_app_token: your_default_app_token
  bitable_table_id: your_default_table_id
  field_mapping:
    - aiField: "NO"
      feishuField: "NO"
      type: "string"
```

### 法国 (FR)

```yaml
FR:
  bitable_app_token: your_france_app_token
  bitable_table_id: your_france_table_id
```

### 中国 (CN)

```yaml
CN:
  bitable_app_token: your_china_app_token
  bitable_table_id: your_china_table_id
```

**获取配置的优先级**：

```
国家专用配置 → 默认配置 → 环境变量
```

### 3.3 路由与控制器

#### 3.3.1 API 路由总览

| 路由 | 方法 | 认证 | 功能 |
|------|------|------|------|
| `/api/auth/register` | POST | 否 | 用户注册 |
| `/api/auth/login` | POST | 否 | 用户登录 |
| `/api/auth/profile` | GET | 是 | 获取用户信息 |
| `/api/auth/change-password` | POST | 是 | 修改密码 |
| `/api/tasks` | GET | 是 | 获取任务列表 |
| `/api/tasks/:taskId` | GET | 是 | 获取任务详情 |
| `/api/tasks/:taskId/confirm` | POST | 是 | 确认并提交任务 |
| `/api/local/upload-stream` | POST | 是 | **上传图片并解析（SSE 流式）** |
| `/api/local/upload` | POST | 是 | 上传图片并解析（旧接口） |
| `/api/local/image/:fileKey` | GET | 是 | 获取图片 URL |
| `/api/local/export/:taskId/csv` | GET | 是 | 导出 CSV |
| `/api/local/debug/:taskId` | GET | 是 | 获取调试信息 |
| `/api/config` | GET/PUT | 是 | 配置管理 |
| `/api/audit` | GET | 是 | 审计日志 |
| `/api/service/*` | 多 | 是 | 服务管理 |
| `/webhook/feishu` | POST | 签名验证 | 飞书 Webhook |

#### 3.3.2 核心路由详解

**1. 流式上传接口** `/api/local/upload-stream`

这是系统的核心接口，支持 SSE（Server-Sent Events）实时推送识别结果。

**请求**：
```http
POST /api/local/upload-stream
Authorization: Bearer <token>
Content-Type: multipart/form-data

------WebKitFormBoundary
Content-Disposition: form-data; name="image"; filename="attendance.jpg"
Content-Type: image/jpeg

<binary data>
------WebKitFormBoundary
Content-Disposition: form-data; name="taskId" (可选)
<existing taskId>
```

**SSE 响应事件**：

```
event: start
data: {"taskId":"20260519_001","imagePreviewUrl":"/api/local/uploads/xxx.jpg"}

event: status
data: {"message":"AI 识别中..."}

event: record
data: {"index":1,"record":{...},"totalRecords":1}
data: {"index":2,"record":{...},"totalRecords":2}

event: complete
data: {"taskId":"20260519_001","rowCount":10}
```

**2. 任务确认接口** `/api/tasks/:taskId/confirm`

```javascript
// 请求体
{
  "data": [
    {
      "NO": "1",
      "NOM_PRENOM": "张三",
      "Date": "2026-05-19",
      "ARRIVEE_DATETIME": "2026-05-19 08:00",
      "DEPAR_DATETIME": "2026-05-19 18:00",
      "PAUSE": 60,
      "SmartMark": "正常"
    }
  ]
}
```

---

## 四、数据库结构

### 4.1 数据库概览

```sql
Database: attendance_assistant
Charset: utf8mb4
Collation: utf8mb4_unicode_ci
```

### 4.2 表结构详解

#### 4.2.1 tasks（任务表）

**SQL 定义**：[backend/config/init.sql](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/config/init.sql)

```sql
CREATE TABLE tasks (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '创建者用户ID',
    file_key VARCHAR(128) NOT NULL COMMENT '原始文件key或JSON数组',
    status ENUM('processing','processed','confirmed','failed') 
           NOT NULL DEFAULT 'processing' COMMENT '任务状态',
    raw_data JSON NULL COMMENT 'AI解析的原始数据',
    confirmed_data JSON NULL COMMENT '用户确认后的数据',
    ai_raw_output TEXT NULL COMMENT 'AI原始输出（用于调试）',
    processed_by VARCHAR(512) NULL COMMENT '处理人信息（JSON）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | VARCHAR(64) | 主键，格式如 `20260519_001` |
| user_id | VARCHAR(64) | 关联用户 ID |
| file_key | VARCHAR(128) | 文件 key 或 JSON 数组（支持多图片） |
| status | ENUM | 任务状态 |
| raw_data | JSON | AI 解析结果数组 |
| confirmed_data | JSON | 用户确认后的数据 |
| ai_raw_output | TEXT | AI 原始输出，便于调试 |
| processed_by | JSON | 处理人信息对象 |

#### 4.2.2 logs（任务日志表）

```sql
CREATE TABLE logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NULL,
    log_type VARCHAR(32) NOT NULL COMMENT '日志类型',
    content TEXT NOT NULL COMMENT 'JSON格式的日志内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_log_type (log_type)
);
```

**日志类型**：

- `TASK_START`：任务开始
- `IMAGE_URL_GOT`：获取图片 URL
- `AI_PARSE_RESULT`：AI 解析结果
- `UPLOAD_START`：上传开始
- `AI_PARSE_SUCCESS`：AI 解析成功
- `AI_PARSE_FAILED`：AI 解析失败
- `CONFIRM_START`：确认开始
- `CONFIRM_SUCCESS`：确认成功
- `BITABLE_WRITE_FAILED`：写入飞书失败
- `TASK_ERROR`：任务错误

#### 4.2.3 users（用户表）

```sql
CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('admin','user') DEFAULT 'user',
    real_name VARCHAR(100),
    employee_id VARCHAR(50),
    status ENUM('active','inactive','deleted') DEFAULT 'active',
    last_login_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_email (email),
    INDEX idx_username (username)
);
```

#### 4.2.4 user_sessions（用户会话表）

```sql
CREATE TABLE user_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token_hash (token_hash),
    INDEX idx_expires_at (expires_at)
);
```

#### 4.2.5 plugin_config（配置表）

```sql
CREATE TABLE plugin_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    config_type ENUM('string','json','number','boolean') DEFAULT 'string',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**关键配置项**：

| config_key | config_type | 说明 |
|------------|-------------|------|
| ai_prompt | string | AI 识别提示词 |
| continue_prompt | string | AI 续写提示词 |
| feishu_app_token | string | 飞书多维表 App Token |
| feishu_table_id | string | 飞书多维表 Table ID |
| field_mapping | json | 字段映射配置 |

#### 4.2.6 audit_logs（审计日志表）

```sql
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(128) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id VARCHAR(64),
    details JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
);
```

---

## 五、前端详细分析

### 5.1 目录结构

```
frontend/
├── src/
│   ├── components/       # 公共组件
│   │   ├── Header.jsx
│   │   ├── Layout.jsx
│   │   └── ProtectedRoute.jsx
│   ├── contexts/         # React Context
│   │   └── AuthContext.jsx
│   ├── pages/           # 页面组件
│   │   ├── HomePage.jsx       # 首页/上传
│   │   ├── TaskPage.jsx       # 数据编辑页 ⭐
│   │   ├── TaskListPage.jsx  # 任务列表
│   │   ├── LoginPage.jsx
│   │   ├── RegisterPage.jsx
│   │   ├── UserManagementPage.jsx
│   │   ├── ConfigPage.jsx
│   │   ├── AuditLogPage.jsx
│   │   └── ServicePage.jsx
│   ├── services/        # API 服务
│   │   └── api.js             # Axios 封装
│   ├── utils/           # 工具函数
│   │   ├── auth.js           # 认证工具
│   │   └── sse.js            # SSE 解析工具
│   ├── App.jsx          # 路由配置
│   ├── main.jsx
│   └── index.css
├── package.json
├── vite.config.js
├── tailwind.config.js
└── postcss.config.js
```

### 5.2 核心页面分析

#### 5.2.1 HomePage（首页/上传）

**文件位置**：[frontend/src/pages/HomePage.jsx](file:///Users/apple/Desktop/智能体/考勤智能助手/frontend/src/pages/HomePage.jsx)

**功能**：

1. **图片选择与压缩**
   - 支持多选图片
   - 基于 SHA-256 去重
   - 智能压缩（根据文件大小调整质量和尺寸）

2. **流式上传与实时显示**
   - 使用 SSE 实时接收识别结果
   - 逐条显示识别到的记录
   - 支持多图片顺序处理

3. **智能标记展示**
   - 正常（绿色）
   - 手写（蓝色）
   - 模糊（橙色）
   - 夜班（紫色）
   - 已删除（红色）

**压缩策略**：

```javascript
const compressImage = async (file) => {
  // 根据文件大小调整压缩参数
  if (sizeKB < 500) {
    maxWidth = 2000; quality = 0.9;
  } else if (sizeKB < 1000) {
    maxWidth = 1600; quality = 0.85;
  } else if (sizeKB < 2000) {
    maxWidth = 1200; quality = 0.8;
  } else if (sizeKB < 5000) {
    maxWidth = 1000; quality = 0.7;
  } else {
    maxWidth = 800; quality = 0.6;
  }
};
```

#### 5.2.2 TaskPage（数据编辑页）

**文件位置**：[frontend/src/pages/TaskPage.jsx](file:///Users/apple/Desktop/智能体/考勤智能助手/frontend/src/pages/TaskPage.jsx)

**功能**：

1. **数据编辑**
   - 表格形式展示识别结果
   - 支持编辑所有可编辑字段
   - 必填字段（NO, Date, ARRIVEE, DEPAR, PAUSE）高亮提示

2. **记录操作**
   - **标记删除**：灰色背景 + 删除按钮
   - **标记未出勤**：到达和离开时间为空
   - **恢复正常**：取消删除状态

3. **风险提示**
   - 黄色背景表示必填字段缺失
   - 显示缺失的具体字段名

4. **提交验证**
   - 检查必填字段完整性
   - 过滤已删除记录
   - 确认后写入飞书

5. **调试视图**
   - 显示 AI 原始输出
   - 显示解析后的 JSON 数据
   - 显示确认后的数据

**表格列定义**：

```javascript
const COLUMNS = [
  { key: 'NO', label: '工号', required: true },
  { key: 'NOM_PRENOM', label: '姓名' },
  { key: 'AGENCE_INTERIMAIRE', label: '中介机构' },
  { key: 'HORAIRES_DU_TRAVAIL', label: '工作时间' },
  { key: 'Date', label: '日期', required: true },
  { key: 'ARRIVEE_DATETIME', label: '到达时间', required: true },
  { key: 'DEPAR_DATETIME', label: '离开时间', required: true },
  { key: 'PAUSE', label: '休息(分钟)', required: true },
  { key: 'CHECKER', label: '状态' },
  { key: 'SmartMark', label: '标记', readOnly: true },
];
```

#### 5.2.3 TaskListPage（任务列表）

**文件位置**：[frontend/src/pages/TaskListPage.jsx](file:///Users/apple/Desktop/智能体/考勤智能助手/frontend/src/pages/TaskListPage.jsx)

**功能**：

1. **任务列表展示**
   - 分页显示（20条/页）
   - 显示任务 ID、状态、处理人、时间

2. **筛选功能**
   - 按状态筛选
   - 按日期范围筛选
   - 关键词搜索

3. **图片预览**
   - 点击文件名预览图片
   - 支持多图片展开

#### 5.2.4 ServicePage（服务管理）

**文件位置**：[frontend/src/pages/ServicePage.jsx](file:///Users/apple/Desktop/智能体/考勤智能助手/frontend/src/pages/ServicePage.jsx)

**功能**：

1. **服务状态监控**
   - 后端服务（端口 3000）
   - 前端服务（端口 5175）
   - 每 5 秒自动刷新

2. **服务控制**
   - 一键启动/停止所有服务
   - 单独启动/停止后端
   - 单独启动/停止前端

3. **手动启动提示**
   - 后端不可用时提供手动启动命令

---

## 六、配置系统详解

### 6.1 配置模式

系统支持两种配置模式，通过 `USE_DATABASE` 环境变量切换：

| 模式 | USE_DATABASE | 配置存储 | 适用场景 |
|------|-------------|----------|----------|
| 数据库模式 | `true` | MySQL | 生产环境，需要管理界面 |
| 文件模式 | `false` | `config.yaml` | 开发/轻量部署 |

### 6.2 环境变量配置

**文件位置**：[backend/.env.example](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/.env.example)

```env
# 飞书应用配置
FEISHU_APP_ID=cli_xxxxxx
FEISHU_APP_SECRET=xxxxxx
FEISHU_ENCRYPT_KEY=xxxxxx
FEISHU_VERIFICATION_TOKEN=xxxxxx

# 飞书多维表格配置
BITABLE_APP_TOKEN=xxxxxx
BITABLE_TABLE_ID=xxxxxx

# AI 服务配置
MIMO_API_KEY=xxxxxx
MIMO_API_URL=https://api.mimo.com/v2/vision

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=12345678
DB_NAME=attendance_assistant

# 配置模式
USE_DATABASE=true
CONFIG_FILE=./config.yaml

# 服务器配置
PORT=3000
NODE_ENV=development
FRONTEND_URL=http://localhost:5173
```

### 6.3 AI 提示词配置

**文件位置**：[backend/config.yaml](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/config.yaml)

```yaml
ai_prompt: |
  识别法国考勤表格，逐行返回单个 JSON 数组。

  规则：
  1. 只返回真实数据，禁止编造
  2. 仔细观察工号和姓名列，识别记录质量：
     - 如果内容是手写的，标记为"手写"
     - 如果内容模糊不清楚（如???），标记为"模糊"
     - 如果内容清晰可辨认，标记为"正常"
  3. 根据到达时间和离开时间判断是否为夜班（法国出勤规则）：
     - 到达时间在20:00之后，或离开时间在06:00之前
     - 跨越午夜的班次（如22:00到06:00）
     - 如果是夜班，添加"夜班"标记
  4. 第10个字段（标记）使用分号分隔多个标记，如"正常;夜班"或"手写"或"模糊;夜班"
  5. 删除线标记第11个字段设为 true，否则 false
  6. 每一行就是一条记录，格式为：[NO,姓名,中介,班次,日期,到达,离开,休息,检查器,标记,已删除]
  7. 不要把所有记录包在一个大数组里！
  8. 时间格式必须转换为标准的 HH:MM 格式
  9. 日期格式必须为 YYYY-MM-DD 格式

continue_prompt: |
  请接续上文继续输出，不要重复已有内容，保持相同格式。
```

### 6.4 飞书多维表配置

**文件位置**：[base-config/feishu.md](file:///Users/apple/Desktop/智能体/考勤智能助手/base-config/feishu.md)

支持按国家配置不同的飞书多维表格，适用于跨国企业考勤管理。

---

## 七、业务流程详解

### 7.1 完整业务流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                         飞书机器人接收                               │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  方式一：WebSocket 长连接（推荐）                                    │
│  FeishuWebSocketService                                             │
│  └── 监听 im.message.receive_v1 事件                                │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  方式二：Webhook HTTP 回调                                          │
│  /webhook/feishu                                                   │
│  └── URL 验证 → 消息处理                                            │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  TaskService.processImage()                                        │
│  1. 创建任务记录                                                    │
│  2. 获取图片 URL                                                    │
│  3. 调用 AI 解析                                                    │
│  4. 保存结果                                                        │
│  5. 发送卡片消息（点击跳转前端）                                     │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  前端编辑确认                                                       │
│  TaskPage.jsx                                                       │
│  1. 查看/编辑识别结果                                               │
│  2. 标记删除/未出勤                                                 │
│  3. 提交确认                                                        │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  BitableService.batchWriteRecords()                                 │
│  1. 字段映射转换                                                    │
│  2. 数据格式转换                                                    │
│  3. 分批写入飞书                                                    │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 本地上传流程

```
┌─────────────────────────────────────────────────────────────────────┐
│  前端上传（HomePage.jsx）                                           │
│  1. 选择图片                                                        │
│  2. SHA-256 去重                                                    │
│  3. 智能压缩                                                        │
│  4. SSE 流式上传                                                    │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  后端处理（/api/local/upload-stream）                                │
│  1. 保存图片到 uploads/                                             │
│  2. 创建任务记录                                                    │
│  3. AI 流式解析                                                    │
│  4. SSE 实时推送结果                                                │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│  跳转到编辑页面                                                     │
│  TaskPage.jsx                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 八、关键算法与规则

### 8.1 任务号生成规则

**文件位置**：[backend/src/utils/RecordNoGenerator.js](file:///Users/apple/Desktop/智能体/考勤智能助手/backend/src/utils/RecordNoGenerator.js)

格式：`YYYYMMDD_NNN`

示例：`20260519_001`、`20260519_002`

### 8.2 夜班检测规则

```javascript
// 夜班判定条件：
// 1. 到达时间 ≥ 18:00 且 离开时间 ≤ 12:00
// 2. 或 到达时间在 20:00 之后
// 3. 或 离开时间在 06:00 之前

const detectShiftType = (arriveTime, departTime) => {
  const arriveHour = parseInt(normalizeTime(arriveTime).split(':')[0]);
  const departHour = parseInt(normalizeTime(departTime).split(':')[0]);
  
  if (arriveHour >= 20 || departHour < 6) {
    return '夜班';
  }
  if (arriveHour >= 6 && arriveHour < 12 && departHour >= 12 && departHour < 18) {
    return '早班';
  }
  // ...
};
```

### 8.3 智能标记生成规则

```javascript
const generateSmartMark = (record) => {
  // 1. 未出勤判定
  if (isAbsent) {
    return '未出勤';
  }
  
  // 2. 记录质量判定
  if (detectRecordQuality(record).isHandwritten) {
    qualityMark = '手写';
  } else if (detectRecordQuality(record).isBlurry) {
    qualityMark = '模糊';
  } else {
    qualityMark = '正常';
  }
  
  // 3. 夜班标记
  if (shiftType === '夜班') {
    return `${qualityMark};夜班`;
  }
  
  return qualityMark;
};
```

### 8.4 风险等级评估

```javascript
const detectAnomalies = (record) => {
  const anomalies = [];
  
  // 必填字段检查
  if (!record.NO) anomalies.push('工号未识别');
  if (!record.Date) anomalies.push('日期未识别');
  if (!record.ARRIVEE) anomalies.push('到达时间未识别');
  if (!record.DEPAR) anomalies.push('离开时间未识别');
  if (!record.PAUSE) anomalies.push('休息时间未识别');
  
  if (record.isDeleted) {
    anomalies.push('记录已删除');
  }
  
  return {
    riskLevel: anomalies.length > 0 ? 'high' : 'none',
    anomalies
  };
};
```

---

## 九、API 完整列表

### 9.1 认证相关 `/api/auth`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/register` | POST | 否 | 用户注册 |
| `/login` | POST | 否 | 用户登录 |
| `/profile` | GET | 是 | 获取用户信息 |
| `/change-password` | POST | 是 | 修改密码 |
| `/logout` | POST | 是 | 登出 |
| `/verify` | GET | 是 | 验证 Token |

### 9.2 任务相关 `/api/tasks`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/` | GET | 是 | 获取任务列表 |
| `/:taskId` | GET | 是 | 获取任务详情 |
| `/:taskId/confirm` | POST | 是 | 确认并提交 |

### 9.3 本地上传 `/api/local`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/upload-stream` | POST | 是 | **流式上传解析（SSE）** |
| `/upload` | POST | 是 | 普通上传解析 |
| `/image/:fileKey` | GET | 是 | 获取图片 URL |
| `/export/:taskId/csv` | GET | 是 | 导出 CSV |
| `/debug/:taskId` | GET | 是 | 获取调试信息 |

### 9.4 配置相关 `/api/config`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/` | GET | 是 | 获取所有配置 |
| `/` | PUT | 是 | 更新配置 |
| `/batch` | PUT | 是 | 批量更新 |
| `/:key` | GET | 是 | 获取单个配置 |
| `/mode` | GET/PUT | 是 | 配置模式管理 |

### 9.5 审计相关 `/api/audit`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/` | GET | 是 | 获取审计日志列表 |

### 9.6 服务管理 `/api/service`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/status` | GET | 是 | 获取服务状态 |
| `/backend/start` | POST | 是 | 启动后端 |
| `/backend/stop` | POST | 是 | 停止后端 |
| `/frontend/start` | POST | 是 | 启动前端 |
| `/frontend/stop` | POST | 是 | 停止前端 |
| `/start-all` | POST | 是 | 启动所有 |
| `/stop-all` | POST | 是 | 停止所有 |

### 9.7 Webhook `/webhook`

| 接口 | 方法 | 认证 | 说明 |
|------|------|------|------|
| `/feishu` | POST | 签名验证 | 飞书事件回调 |

---

## 十、安全机制

### 10.1 认证机制

1. **JWT Token 认证**
   - 登录成功后生成 Token
   - Token 有效期 7 天
   - 所有 API 请求携带 `Authorization: Bearer <token>`

2. **Token 存储**
   - 存储在 LocalStorage
   - 响应拦截器处理 401 自动跳转登录

### 10.2 飞书签名验证

```javascript
// 中间件：verifyFeishuSignature
// 验证飞书请求的签名，确保来源可信
```

### 10.3 输入安全

1. **查询参数清理**（`sanitizeQuery.js`）
   - 防止 SQL 注入
   - 限制字符串长度

2. **文件上传限制**
   - 仅允许图片格式
   - 存储在独立目录

---

## 十一、日志系统

### 11.1 日志框架

使用 Winston 进行日志管理。

**日志级别**：

- `error`：错误日志
- `warn`：警告日志
- `info`：信息日志（默认）
- `debug`：调试日志

### 11.2 日志输出

```javascript
// 文件输出
logs/
├── combined.log   // 所有日志
└── error.log      // 仅错误日志
```

### 11.3 日志格式

```javascript
{
  timestamp: '2026-05-19T10:00:00.000Z',
  level: 'info',
  message: 'Task created',
  taskId: '20260519_001',
  userId: 'user123'
}
```

---

## 十二、部署架构

### 12.1 开发环境

```bash
# 启动后端
cd backend
npm install
npm run dev

# 启动前端
cd frontend
npm install
npm run dev
```

### 12.2 生产环境建议

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx                                 │
│  ┌──────────────────┐    ┌──────────────────┐              │
│  │  /api/* → 后端   │    │  /* → 前端静态    │              │
│  └──────────────────┘    └──────────────────┘              │
└─────────────────────────────────────────────────────────────┘
                    │                    │
                    ▼                    ▼
         ┌─────────────────┐    ┌─────────────────┐
         │  Node.js 后端   │    │   React 构建    │
         │   (端口 3000)   │    │   (端口 5173)   │
         └────────┬────────┘    └─────────────────┘
                  │
       ┌──────────┼──────────┐
       │          │          │
       ▼          ▼          ▼
  ┌─────────┐ ┌─────────┐ ┌─────────┐
  │  MySQL  │ │  飞书   │ │ MiMo AI │
  │ Database│ │  平台   │ │   API   │
  └─────────┘ └─────────┘ └─────────┘
```

---

## 十三、技术债务与改进建议

### 13.1 当前架构优点

1. **清晰的分层架构**：Controller → Service → Model
2. **配置灵活**：支持文件和数据库两种模式
3. **SSE 实时推送**：用户体验良好
4. **多国家支持**：可配置不同国家的飞书表格

### 13.2 改进建议

1. **数据库**
   - 考虑引入 Redis 缓存热点数据
   - 添加数据库连接池监控

2. **AI 服务**
   - 添加更多 AI 模型支持（可配置）
   - 支持批量图片并行识别

3. **前端**
   - 添加状态管理（Redux/Zustand）
   - 组件库升级（如 Ant Design）
   - 单元测试

4. **DevOps**
   - Docker 容器化
   - CI/CD 流水线
   - 监控告警系统（Prometheus + Grafana）

5. **安全性**
   - 添加 Rate Limiting
   - 操作日志脱敏
   - API 版本管理

---

## 十四、总结

### 14.1 项目特点

1. **AI 驱动的考勤处理**：利用视觉 AI 自动识别表格数据，大幅提升效率
2. **深度飞书集成**：与飞书消息、卡片、多维表格无缝对接
3. **灵活的配置系统**：支持多国家/多表格配置
4. **实时用户体验**：SSE 流式推送，实时显示识别进度
5. **完整的审计追溯**：所有操作有记录可查

### 14.2 适用场景

- 跨国企业考勤管理
- 需要处理大量纸质考勤表格的组织
- 已使用飞书作为办公协作平台的企业

### 14.3 技术亮点

- **流式 AI 解析**：逐行提取记录，实时显示
- **智能数据标准化**：处理多种日期时间格式
- **多图片合并上传**：一次选择多张图片，自动合并到同一任务
- **客户端智能压缩**：减少上传时间和带宽占用

---

*文档生成时间：2026-05-20*
