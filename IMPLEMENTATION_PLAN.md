# AI考勤智能助手 - 技术栈迁移实施计划

## 文档信息

- **项目名称**：AI考勤智能助手（Attendance Assistant）
- **目标技术栈**：JDK 1.8 + Spring Boot 2.7.x + MyBatis 3.5.x + MySQL 8.0
- **前端技术栈**：Vue 3 + Vite + Element Plus
- **文档版本**：v1.0
- **创建日期**：2026-05-20
- **状态**：规划中

---

## 一、项目概述

### 1.1 项目背景

AI考勤智能助手是一个基于飞书平台和AI视觉识别技术的考勤数据自动化处理系统。本文档详细规划将现有技术栈（Node.js + Express + React）迁移至新技术栈（JDK 1.8 + Spring Boot + MyBatis + Vue 3）的实施方案。

### 1.2 核心业务功能

| 功能模块 | 功能描述 | 优先级 |
|---------|---------|--------|
| AI智能识别 | 通过MiMo Vision API流式解析考勤表格图片，逐行提取考勤记录 | P0 |
| 飞书集成 | 与飞书消息、飞书多维表格深度集成 | P0 |
| 用户认证 | JWT Token认证，支持用户注册、登录、权限管理 | P0 |
| 任务管理 | 创建任务、AI解析、数据编辑、提交确认的完整工作流 | P0 |
| 配置管理 | 支持按国家/地区配置不同的多维表格和字段映射 | P1 |
| 审计日志 | 完整的操作审计日志，记录所有关键操作 | P1 |
| 服务管理 | 内置服务管理界面，支持一键启停前后端服务 | P2 |

### 1.3 数据流向

```
用户上传图片 → 前端压缩 → SSE流式上传 → 后端接收
                                        ↓
                               AI流式解析（MiMo API）
                                        ↓
                               SSE实时推送结果
                                        ↓
                               用户编辑确认
                                        ↓
                               写入飞书多维表格
```

---

## 二、技术栈对比分析

### 2.1 后端技术栈对比

| 维度 | 现有技术栈 | 目标技术栈 | 迁移说明 |
|-----|-----------|-----------|---------|
| 运行环境 | Node.js 18+ | JDK 1.8 | Java生态系统成熟，生态丰富 |
| Web框架 | Express.js | Spring Boot 2.7.x | 全栈框架，内置大量starter |
| ORM框架 | 原生SQL | MyBatis 3.5.x | SQL精细控制，XML/注解双支持 |
| 认证机制 | JWT (jsonwebtoken) | JWT + Spring Security | Spring Security提供完整安全解决方案 |
| 日志系统 | Winston | SLF4J + Logback | Spring Boot默认集成 |
| 配置管理 | dotenv + YAML | application.yml + @ConfigurationProperties | Spring Boot强类型配置 |
| 依赖管理 | npm | Maven/Gradle | 企业级依赖管理 |
| 线程模型 | 单线程异步 | 多线程/响应式 | Java并发优势 |

### 2.2 前端技术栈对比

| 维度 | 现有技术栈 | 目标技术栈 | 迁移说明 |
|-----|-----------|-----------|---------|
| 框架 | React 18 | Vue 3 | Composition API保持响应式开发体验 |
| 构建工具 | Vite | Vite | 保持快速开发体验 |
| 状态管理 | React Context | Pinia/Vuex 4 | Vue官方推荐，TypeScript友好 |
| UI组件库 | Tailwind CSS | Element Plus | 企业级组件库，功能完善 |
| 路由 | React Router 6 | Vue Router 4 | Vue官方路由方案 |
| HTTP客户端 | Axios | Axios | 保持一致，axios统一封装 |
| 样式方案 | Tailwind CSS | SCSS + Element Plus | 保持样式开发灵活性 |

### 2.3 数据库技术栈对比

| 维度 | 现有技术栈 | 目标技术栈 | 说明 |
|-----|-----------|-----------|------|
| 数据库 | MySQL 8.0+ | MySQL 8.0+ | 保持不变 |
| 连接池 | mysql2 Connection Pool | Druid/HikariCP | Java成熟连接池方案 |
| SQL编写 | 原生SQL + 模型层 | MyBatis XML/注解 | MyBatis SQL精细控制 |
| 事务管理 | 手动管理 | @Transactional声明式事务 | Spring声明式事务更简洁 |

---

## 三、后端架构设计

### 3.1 项目结构设计

```
backend/
├── src/main/java/com/attendance/
│   ├── AttendanceApplication.java          # Spring Boot启动类
│   │
│   ├── config/                            # 配置层
│   │   ├── WebConfig.java                 # Web配置（CORS、拦截器）
│   │   ├── SecurityConfig.java            # Spring Security配置
│   │   ├── MyBatisConfig.java             # MyBatis配置
│   │   └── FeishuConfig.java              # 飞书配置
│   │
│   ├── controller/                         # 控制器层 ⭐
│   │   ├── AuthController.java            # 认证接口
│   │   ├── TaskController.java            # 任务管理接口
│   │   ├── LocalUploadController.java      # 本地上传接口（SSE）
│   │   ├── ConfigController.java          # 配置管理接口
│   │   ├── AuditController.java           # 审计日志接口
│   │   ├── ServiceController.java         # 服务管理接口
│   │   └── FeishuWebhookController.java   # 飞书Webhook接口
│   │
│   ├── service/                            # 业务服务层 ⭐ 核心
│   │   ├── AIParserService.java           # AI解析服务
│   │   ├── BitableService.java            # 飞书多维表格服务
│   │   ├── FeishuService.java             # 飞书消息服务
│   │   ├── TaskService.java               # 任务服务
│   │   ├── UserService.java               # 用户服务
│   │   ├── ConfigService.java             # 配置服务
│   │   └── AuditLogService.java           # 审计日志服务
│   │
│   ├── mapper/                             # 数据访问层
│   │   ├── TaskMapper.java                # 任务Mapper
│   │   ├── UserMapper.java                # 用户Mapper
│   │   ├── ConfigMapper.java              # 配置Mapper
│   │   ├── AuditLogMapper.java            # 审计日志Mapper
│   │   └── LogMapper.java                 # 日志Mapper
│   │
│   ├── entity/                             # 实体类
│   │   ├── Task.java                      # 任务实体
│   │   ├── User.java                      # 用户实体
│   │   ├── PluginConfig.java              # 配置实体
│   │   ├── AuditLog.java                  # 审计日志实体
│   │   └── Log.java                       # 日志实体
│   │
│   ├── dto/                                # 数据传输对象
│   │   ├── request/                       # 请求DTO
│   │   └── response/                      # 响应DTO
│   │
│   ├── common/                             # 通用组件
│   │   ├── Result.java                    # 统一响应封装
│   │   ├── Constants.java                 # 常量定义
│   │   ├── exception/                     # 异常处理
│   │   └── interceptor/                   # 拦截器
│   │
│   └── util/                               # 工具类
│       ├── JwtUtil.java                   # JWT工具
│       ├── RecordNoGenerator.java          # 任务号生成器
│       ├── FeishuConfigManager.java        # 飞书配置管理器
│       └── PasswordEncoder.java            # 密码加密
│
├── src/main/resources/
│   ├── mapper/                             # MyBatis XML映射文件
│   │   ├── TaskMapper.xml
│   │   ├── UserMapper.xml
│   │   ├── ConfigMapper.xml
│   │   └── AuditLogMapper.xml
│   │
│   ├── application.yml                     # 主配置文件
│   ├── application-dev.yml                 # 开发环境配置
│   └── application-prod.yml               # 生产环境配置
│
├── src/test/java/                          # 测试代码
│
├── config/                                  # SQL初始化脚本
│   ├── init.sql
│   ├── init_audit_log.sql
│   └── init_config.sql
│
├── pom.xml                                  # Maven依赖配置
└── README.md
```

### 3.2 核心服务映射关系

| Node.js Service | Spring Boot Service | 说明 |
|----------------|---------------------|------|
| AIParserService | AIParserService | AI解析服务，保持核心逻辑 |
| BitableService | BitableService | 飞书多维表格服务，SDK更换 |
| FeishuService | FeishuService | 飞书消息服务，SDK更换 |
| TaskService | TaskService | 任务服务，逻辑迁移 |
| FeishuConfigManager | FeishuConfigManager | 配置管理器，迁移 |
| ServiceManager | ServiceManager | 服务管理，需适配Java进程管理 |

### 3.3 数据库表结构（保持兼容）

```sql
-- 任务表
CREATE TABLE tasks (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '创建者用户ID',
    file_key VARCHAR(128) NOT NULL COMMENT '原始文件key或JSON数组',
    status ENUM('processing','processed','confirmed','failed') NOT NULL DEFAULT 'processing',
    raw_data JSON NULL COMMENT 'AI解析的原始数据',
    confirmed_data JSON NULL COMMENT '用户确认后的数据',
    ai_raw_output TEXT NULL COMMENT 'AI原始输出',
    processed_by VARCHAR(512) NULL COMMENT '处理人信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 配置表
CREATE TABLE plugin_config (
    id INT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    config_type ENUM('string','json','number','boolean') DEFAULT 'string',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审计日志表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 日志表
CREATE TABLE logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NULL,
    log_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_log_type (log_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 四、后端详细实施计划

### 4.1 模块拆分与任务列表

#### 阶段一：基础架构搭建（第1周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B001 | 项目初始化 | 创建Spring Boot项目，配置pom.xml | 4h | 无 |
| T-B002 | 配置文件设置 | 配置application.yml，多环境配置 | 2h | T-B001 |
| T-B003 | 数据库连接配置 | 配置Druid连接池，MyBatis集成 | 4h | T-B001 |
| T-B004 | 实体类创建 | 根据数据库表创建Entity类 | 4h | T-B003 |
| T-B005 | 统一响应封装 | 创建Result类，统一响应格式 | 2h | T-B001 |
| T-B006 | 全局异常处理 | 创建全局异常处理器 | 4h | T-B005 |
| T-B007 | CORS跨域配置 | 配置跨域资源共享 | 2h | T-B001 |

#### 阶段二：认证模块（第2周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B008 | 用户实体和Mapper | 创建User实体和UserMapper | 4h | T-B004 |
| T-B009 | 用户注册功能 | 实现用户注册接口 | 4h | T-B008 |
| T-B010 | 用户登录功能 | 实现JWT登录接口 | 6h | T-B008 |
| T-B011 | 密码加密处理 | 实现BCrypt密码加密 | 2h | T-B008 |
| T-B012 | JWT工具类 | 实现JWT生成和验证工具 | 4h | T-B010 |
| T-B013 | 认证拦截器 | 实现JWT认证拦截器 | 4h | T-B012 |
| T-B014 | 用户信息接口 | 实现获取和修改用户信息接口 | 4h | T-B013 |

#### 阶段三：任务管理模块（第3周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B015 | 任务实体和Mapper | 创建Task实体和TaskMapper | 4h | T-B004 |
| T-B016 | 任务号生成器 | 实现任务号自动生成（YYYYMMDD_NNN） | 4h | T-B015 |
| T-B017 | 任务列表接口 | 实现任务分页查询接口 | 6h | T-B015 |
| T-B018 | 任务详情接口 | 实现任务详情查询接口 | 4h | T-B015 |
| T-B019 | 任务确认接口 | 实现任务确认和提交接口 | 8h | T-B015 |

#### 阶段四：文件上传和AI解析模块（第4周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B020 | 文件上传配置 | 配置上传文件大小限制和存储路径 | 2h | T-B001 |
| T-B021 | 文件上传接口 | 实现普通文件上传接口 | 4h | T-B020 |
| T-B022 | SSE流式上传接口 | 实现SSE流式上传和实时推送 | 12h | T-B021 |
| T-B023 | AI解析服务 | 迁移AIParserService核心逻辑 | 16h | T-B022 |
| T-B024 | 数据标准化服务 | 实现时间、日期、夜班检测等标准化逻辑 | 8h | T-B023 |
| T-B025 | 飞书SDK集成 | 集成飞书SDK配置 | 4h | T-B001 |

#### 阶段五：飞书集成模块（第5周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B026 | 飞书消息服务 | 实现FeishuService发送消息 | 8h | T-B025 |
| T-B027 | 多维表格服务 | 实现BitableService批量写入 | 12h | T-B026 |
| T-B028 | Webhook接收 | 实现飞书Webhook签名验证和接收 | 8h | T-B027 |
| T-B029 | 多国家配置 | 实现FeishuConfigManager多配置支持 | 6h | T-B025 |

#### 阶段六：配置和审计模块（第6周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B030 | 配置管理接口 | 实现配置的CRUD接口 | 8h | T-B004 |
| T-B031 | AI提示词配置 | 实现AI提示词动态配置 | 4h | T-B030 |
| T-B032 | 审计日志服务 | 实现AuditLogService记录日志 | 6h | T-B004 |
| T-B033 | 审计日志接口 | 实现审计日志查询接口 | 4h | T-B032 |
| T-B034 | 日志记录拦截器 | 实现操作日志自动记录 | 6h | T-B032 |

#### 阶段七：服务管理模块（第7周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-B035 | 服务管理接口 | 实现服务启停接口 | 8h | T-B001 |
| T-B036 | 进程管理工具 | 实现Java进程管理工具类 | 6h | T-B035 |
| T-B037 | 服务监控接口 | 实现服务状态查询接口 | 4h | T-B035 |

#### 阶段八：优化和测试（第8周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------||
| T-B038 | 接口文档生成 | 配置Swagger/API文档 | 4h | T-B001 |
| T-B039 | 单元测试 | 编写核心业务单元测试 | 16h | 前置任务 |
| T-B040 | 性能优化 | 数据库索引优化，连接池调优 | 8h | T-B003 |
| T-B041 | 集成测试 | 端到端集成测试 | 16h | T-B039 |
| T-B042 | 部署文档 | 编写部署和运维文档 | 8h | T-B041 |

---

## 五、前端架构设计

### 5.1 项目结构设计

```
frontend/
├── src/
│   ├── api/                                # API层
│   │   ├── index.js                       # API统一导出
│   │   ├── auth.js                         # 认证相关API
│   │   ├── task.js                         # 任务相关API
│   │   ├── upload.js                        # 上传相关API
│   │   ├── config.js                       # 配置相关API
│   │   └── audit.js                        # 审计相关API
│   │
│   ├── components/                        # 公共组件
│   │   ├── common/                         # 通用组件
│   │   │   ├── Header.vue
│   │   │   ├── Layout.vue
│   │   │   ├── Pagination.vue
│   │   │   └── Loading.vue
│   │   └── business/                       # 业务组件
│   │       ├── TaskTable.vue
│   │       ├── ImageUploader.vue
│   │       └── StatusBadge.vue
│   │
│   ├── composables/                        # 组合式函数（Hooks）
│   │   ├── useAuth.js                      # 认证相关逻辑
│   │   ├── useSSE.js                       # SSE流式处理
│   │   └── useTask.js                     # 任务相关逻辑
│   │
│   ├── router/                             # 路由配置
│   │   ├── index.js                        # 路由主文件
│   │   └── routes.js                       # 路由定义
│   │
│   ├── stores/                             # 状态管理
│   │   ├── auth.js                         # 用户认证状态
│   │   └── app.js                         # 应用全局状态
│   │
│   ├── utils/                              # 工具函数
│   │   ├── request.js                      # Axios封装
│   │   ├── auth.js                         # 认证工具
│   │   └── sse.js                         # SSE解析工具
│   │
│   ├── views/                             # 页面视图
│   │   ├── auth/
│   │   │   ├── Login.vue
│   │   │   └── Register.vue
│   │   ├── home/
│   │   │   └── Home.vue                    # 首页/上传
│   │   ├── task/
│   │   │   ├── TaskList.vue               # 任务列表
│   │   │   └── TaskEdit.vue               # 任务编辑
│   │   ├── config/
│   │   │   └── Config.vue                  # 配置管理
│   │   ├── audit/
│   │   │   └── AuditLog.vue               # 审计日志
│   │   ├── service/
│   │   │   └── Service.vue                 # 服务管理
│   │   └── user/
│   │       └── UserManagement.vue         # 用户管理
│   │
│   ├── App.vue                             # 根组件
│   ├── main.js                            # 入口文件
│   └── styles/                            # 样式文件
│       ├── variables.scss                  # 样式变量
│       └── global.scss                    # 全局样式
│
├── public/                                # 静态资源
│
├── package.json
├── vite.config.js
├── .env                                   # 环境变量
└── .env.development
```

### 5.2 核心模块映射关系

| React组件 | Vue3组件 | 说明 |
|----------|----------|------|
| HomePage.jsx | Home.vue | 首页/上传，核心上传逻辑 |
| TaskPage.jsx | TaskEdit.vue | 任务编辑，表格编辑 |
| TaskListPage.jsx | TaskList.vue | 任务列表 |
| LoginPage.jsx | Login.vue | 登录页 |
| RegisterPage.jsx | Register.vue | 注册页 |
| ConfigPage.jsx | Config.vue | 配置管理 |
| AuditLogPage.jsx | AuditLog.vue | 审计日志 |
| ServicePage.jsx | Service.vue | 服务管理 |
| UserManagementPage.jsx | UserManagement.vue | 用户管理 |
| Header.jsx | Header.vue | 顶部导航 |
| Layout.jsx | Layout.vue | 布局组件 |
| ProtectedRoute.jsx | Router Guard | 路由守卫 |

### 5.3 状态管理对比

| React (Context) | Vue 3 (Pinia) |
|----------------|---------------|
| AuthContext.jsx | stores/auth.js |
| - | stores/app.js |

```javascript
// Vue 3 Pinia Store 示例
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)
  
  const isAuthenticated = computed(() => !!token.value)
  
  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }
  
  const setUserInfo = (info) => {
    userInfo.value = info
  }
  
  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }
  
  return { token, userInfo, isAuthenticated, setToken, setUserInfo, logout }
})
```

---

## 六、前端详细实施计划

### 6.1 模块拆分与任务列表

#### 阶段一：项目初始化（第1周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F001 | 项目初始化 | 创建Vue 3 + Vite项目 | 2h | 无 |
| T-F002 | 依赖安装 | 安装Element Plus、Axios、Pinia等 | 2h | T-F001 |
| T-F003 | Vite配置 | 配置Vite代理、路径别名等 | 2h | T-F001 |
| T-F004 | 样式配置 | 配置SCSS变量、全局样式 | 4h | T-F002 |
| T-F005 | 路由配置 | 配置Vue Router路由守卫 | 4h | T-F002 |
| T-F006 | Axios封装 | 封装请求拦截器、响应拦截器 | 6h | T-F002 |
| T-F007 | Pinia配置 | 配置Pinia状态管理 | 4h | T-F002 |

#### 阶段二：认证模块（第2周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F008 | 登录页面 | 实现Login.vue登录页 | 6h | T-F005 |
| T-F009 | 注册页面 | 实现Register.vue注册页 | 4h | T-F008 |
| T-F010 | 认证Store | 实现useAuthStore状态管理 | 4h | T-F007 |
| T-F011 | 路由守卫 | 实现登录状态校验 | 4h | T-F005 |
| T-F012 | Token管理 | 实现Token存储和自动刷新 | 4h | T-F010 |

#### 阶段三：首页和上传模块（第3周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F013 | 首页布局 | 实现Home.vue页面布局 | 4h | T-F001 |
| T-F014 | 图片上传组件 | 实现ImageUploader.vue图片上传 | 8h | T-F006 |
| T-F015 | 图片压缩 | 实现前端图片压缩逻辑 | 6h | T-F014 |
| T-F016 | SSE流式上传 | 实现useSSE.js组合式函数 | 8h | T-F014 |
| T-F017 | 实时显示组件 | 实现识别结果实时显示 | 6h | T-F016 |
| T-F018 | 图片去重 | 实现SHA-256图片去重 | 4h | T-F014 |

#### 阶段四：任务编辑模块（第4周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F019 | 任务编辑页面 | 实现TaskEdit.vue编辑页 | 8h | T-F001 |
| T-F020 | 任务表格组件 | 实现TaskTable.vue表格组件 | 8h | T-F019 |
| T-F021 | 行编辑功能 | 实现表格行编辑、删除 | 6h | T-F020 |
| T-F022 | 数据验证 | 实现必填字段验证 | 4h | T-F019 |
| T-F023 | 标记展示 | 实现SmartMark智能标记展示 | 4h | T-F020 |
| T-F024 | 提交确认 | 实现数据提交确认流程 | 6h | T-F019 |

#### 阶段五：任务列表模块（第5周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F025 | 任务列表页面 | 实现TaskList.vue列表页 | 6h | T-F001 |
| T-F026 | 筛选功能 | 实现状态、日期筛选 | 4h | T-F025 |
| T-F027 | 分页组件 | 实现Pagination.vue分页 | 4h | T-F025 |
| T-F028 | 图片预览 | 实现图片预览弹窗 | 4h | T-F025 |
| T-F029 | 搜索功能 | 实现关键词搜索 | 4h | T-F025 |

#### 阶段六：配置和审计模块（第6周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F030 | 配置页面 | 实现Config.vue配置页 | 6h | T-F001 |
| T-F031 | 配置表单 | 实现配置项编辑表单 | 4h | T-F030 |
| T-F032 | 审计日志页面 | 实现AuditLog.vue页面 | 6h | T-F001 |
| T-F033 | 日志表格 | 实现日志列表展示 | 4h | T-F032 |

#### 阶段七：用户管理和服务管理（第7周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F034 | 用户管理页面 | 实现UserManagement.vue | 8h | T-F001 |
| T-F035 | 用户表格 | 实现用户列表、编辑 | 4h | T-F034 |
| T-F036 | 服务管理页面 | 实现Service.vue页面 | 6h | T-F001 |
| T-F037 | 服务状态监控 | 实现服务状态轮询 | 4h | T-F036 |
| T-F038 | 服务控制 | 实现启停按钮和控制 | 4h | T-F036 |

#### 阶段八：优化和测试（第8周）

| 任务编号 | 任务名称 | 任务描述 | 预估工时 | 依赖关系 |
|---------|---------|---------|---------|---------|
| T-F039 | 组件优化 | 提取公共组件，减少重复代码 | 8h | T-F001 |
| T-F040 | 样式优化 | 统一样式规范，响应式适配 | 8h | T-F039 |
| T-F041 | 单元测试 | 编写组件单元测试 | 12h | T-F001 |
| T-F042 | E2E测试 | 编写端到端测试 | 12h | T-F041 |
| T-F043 | 打包优化 | 配置生产环境构建优化 | 4h | T-F001 |
| T-F044 | 部署文档 | 编写前端部署文档 | 4h | T-F043 |

---

## 七、关键技术难点与解决方案

### 7.1 SSE流式接口实现

**难点**：Node.js的EventEmitter与Spring MVC的响应式流处理差异

**解决方案**：
```java
// Spring Boot SSE实现
@GetMapping(value = "/upload-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter uploadStream(@RequestParam("file") MultipartFile file,
                                @RequestHeader("Authorization") String auth) {
    SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
    
    // 异步处理
    CompletableFuture.runAsync(() -> {
        try {
            // 调用AI解析服务
            aiParserService.parseImageStreamByLine(base64Image, new StreamCallback() {
                @Override
                public void onRecord(ParsedRecord record) {
                    emitter.send(SseEmitter.event()
                        .name("record")
                        .data(record));
                }
                
                @Override
                public void onComplete(int totalCount) {
                    emitter.complete();
                }
            });
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });
    
    return emitter;
}
```

### 7.2 飞书SDK适配

**难点**：Node.js SDK与Java SDK API差异

**解决方案**：
```java
// Feishu SDK Java版配置
@Configuration
public class FeishuConfig {
    
    @Value("${feishu.app-id}")
    private String appId;
    
    @Value("${feishu.app-secret}")
    private String appSecret;
    
    @Bean
    public FeishuClient feishuClient() {
        return new FeishuClient(appId, appSecret);
    }
}

// 消息发送服务
@Service
public class FeishuMessageService {
    
    @Autowired
    private FeishuClient feishuClient;
    
    public void sendCardMessage(String userId, CardMessage card) {
        feishuClient.im().messages().create(param -> {
            param.receiveId(userId);
            param.msgType("interactive");
            param.content(card.toJson());
        });
    }
}
```

### 7.3 JWT认证兼容

**难点**：确保新旧系统Token格式兼容

**解决方案**：
```java
// JWT工具类保持与前端兼容的Token格式
@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    public String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }
    
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### 7.4 图片处理兼容

**难点**：Node.js Buffer与Java byte[]转换

**解决方案**：
```java
// MultipartFile转Base64
public String multipartFileToBase64(MultipartFile file) {
    try {
        byte[] bytes = file.getBytes();
        return Base64.getEncoder().encodeToString(bytes);
    } catch (IOException e) {
        throw new BusinessException("图片转换失败");
    }
}

// 保存上传文件
public String saveUploadFile(MultipartFile file) {
    String originalFilename = file.getOriginalFilename();
    String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
    String filename = UUID.randomUUID().toString() + extension;
    
    Path targetPath = Paths.get(uploadDir, filename);
    file.transferTo(targetPath);
    
    return filename;
}
```

---

## 八、API接口对照表

### 8.1 认证接口

| Node.js路由 | Spring Boot路由 | 方法 | 说明 |
|------------|----------------|------|------|
| POST /api/auth/register | POST /api/auth/register | RegisterRequest | 用户注册 |
| POST /api/auth/login | POST /api/auth/login | LoginRequest | 用户登录 |
| GET /api/auth/profile | GET /api/auth/profile | - | 获取用户信息 |
| POST /api/auth/change-password | POST /api/auth/change-password | ChangePasswordRequest | 修改密码 |
| POST /api/auth/logout | POST /api/auth/logout | - | 登出 |
| GET /api/auth/verify | GET /api/auth/verify | - | 验证Token |

### 8.2 任务接口

| Node.js路由 | Spring Boot路由 | 方法 | 说明 |
|------------|----------------|------|------|
| GET /api/tasks | GET /api/tasks | TaskQuery | 任务列表 |
| GET /api/tasks/:taskId | GET /api/tasks/:taskId | - | 任务详情 |
| POST /api/tasks/:taskId/confirm | POST /api/tasks/:taskId/confirm | ConfirmRequest | 确认提交 |

### 8.3 上传接口

| Node.js路由 | Spring Boot路由 | 方法 | 说明 |
|------------|----------------|------|------|
| POST /api/local/upload-stream | POST /api/local/upload-stream | MultipartFile | SSE流式上传 |
| POST /api/local/upload | POST /api/local/upload | MultipartFile | 普通上传 |
| GET /api/local/image/:fileKey | GET /api/local/image/:fileKey | - | 获取图片 |
| GET /api/local/export/:taskId/csv | GET /api/local/export/:taskId/csv | - | 导出CSV |
| GET /api/local/debug/:taskId | GET /api/local/debug/:taskId | - | 调试信息 |

### 8.4 配置接口

| Node.js路由 | Spring Boot路由 | 方法 | 说明 |
|------------|----------------|------|------|
| GET /api/config | GET /api/config | - | 获取配置 |
| PUT /api/config | PUT /api/config | ConfigUpdateRequest | 更新配置 |
| PUT /api/config/batch | PUT /api/config/batch | BatchConfigRequest | 批量更新 |
| GET /api/config/:key | GET /api/config/:key | - | 获取单个配置 |

### 8.5 审计接口

| Node.js路由 | Spring Boot路由 | 方法 | 说明 |
|------------|----------------|------|------|
| GET /api/audit | GET /api/audit | AuditQuery | 审计日志 |

### 8.6 服务管理接口

| Node.js路由 | Spring Boot路由 | 方法 | 说明 |
|------------|----------------|------|------|
| GET /api/service/status | GET /api/service/status | - | 服务状态 |
| POST /api/service/backend/start | POST /api/service/backend/start | - | 启动后端 |
| POST /api/service/backend/stop | POST /api/service/backend/stop | - | 停止后端 |
| POST /api/service/frontend/start | POST /api/service/frontend/start | - | 启动前端 |
| POST /api/service/frontend/stop | POST /api/service/frontend/stop | - | 停止前端 |
| POST /api/service/start-all | POST /api/service/start-all | - | 启动所有 |
| POST /api/service/stop-all | POST /api/service/stop-all | - | 停止所有 |

---

## 九、代码对照表

### 9.1 后端代码对照

| Node.js写法 | Spring Boot写法 | 说明 |
|------------|----------------|------|
| `module.exports = {}` | `@Service/@Component` | 类导出 |
| `const express = require('express')` | `@RestController` | REST控制器 |
| `router.get('/path', handler)` | `@GetMapping("/path")` | GET路由 |
| `router.post('/path', handler)` | `@PostMapping("/path")` | POST路由 |
| `req.body` | `@RequestBody` | 请求体 |
| `req.params.id` | `@PathVariable` | 路径参数 |
| `req.query.page` | `@RequestParam` | 查询参数 |
| `res.json({})` | `return Result.success()` | 响应 |
| `next(err)` | `@ExceptionHandler` | 异常处理 |
| `async/await` | `CompletableFuture` | 异步处理 |
| `EventEmitter` | `SseEmitter` | SSE事件 |
| `mysql2.pool` | `SqlSessionFactory` | 数据库连接 |
| `Winston logger` | `SLF4J Logger` | 日志 |
| `dotenv.config()` | `@ConfigurationProperties` | 配置管理 |

### 9.2 前端代码对照

| React写法 | Vue 3写法 | 说明 |
|----------|----------|------|
| `function Component()` | `<template><script setup>` | 组件定义 |
| `useState()` | `ref()/reactive()` | 状态管理 |
| `useEffect()` | `onMounted()/watch()` | 生命周期 |
| `useContext()` | `inject()/provide()` | 跨组件通信 |
| `useCallback()` | `computed()` | 计算属性 |
| `jsx` | `template` | 模板语法 |
| `className` | `class` | 样式类名 |
| `{condition && <Element />}` | `<Element v-if="condition">` | 条件渲染 |
| `{items.map(item => <div />)}` | `<div v-for="item in items">` | 列表渲染 |
| `this.props.xxx` | `props.xxx` | Props |
| `this.state.xxx` | `ref.value` | 状态 |
| `setState()` | `ref.value = xxx` | 状态更新 |
| `Context.Provider` | `<Provider>` | 状态Provider |
| `React Router` | `Vue Router` | 路由 |
| `Outlet` | `<RouterView>` | 路由视图 |

---

## 十、配置对照表

### 10.1 环境变量对照

| Node.js (.env) | Spring Boot (application.yml) | 说明 |
|---------------|------------------------------|------|
| `PORT=3000` | `server.port=3000` | 服务端口 |
| `NODE_ENV=development` | `spring.profiles.active=dev` | 环境 |
| `DB_HOST=localhost` | `spring.datasource.url` | 数据库 |
| `DB_PORT=3306` | `spring.datasource.url` | 数据库端口 |
| `DB_USER=root` | `spring.datasource.username` | 数据库用户 |
| `DB_PASSWORD=12345678` | `spring.datasource.password` | 数据库密码 |
| `DB_NAME=attendance_assistant` | `spring.datasource.url` | 数据库名 |
| `FEISHU_APP_ID` | `feishu.app-id` | 飞书App ID |
| `FEISHU_APP_SECRET` | `feishu.app-secret` | 飞书App Secret |
| `JWT_SECRET` | `jwt.secret` | JWT密钥 |
| `MIMO_API_KEY` | `mimo.api-key` | MiMo API密钥 |

### 10.2 Maven依赖对照npm依赖

| Maven依赖 | npm依赖 | 说明 |
|----------|--------|------|
| spring-boot-starter-web | express | Web框架 |
| spring-boot-starter-security | jsonwebtoken,bcrypt | 安全认证 |
| mybatis-spring-boot-starter | mysql2 | ORM框架 |
| druid-spring-boot-starter | mysql2 pool | 连接池 |
| feishu-sdk-java | @larksuiteoapi/node-sdk | 飞书SDK |
| okhttp | node-fetch | HTTP客户端 |
| logback-classic | winston | 日志框架 |
| lombok | - | 代码生成 |

---

## 十一、里程碑规划

### 11.1 项目总工期

```
总计：8周（56个工作日）
```

### 11.2 里程碑节点

| 里程碑 | 完成时间 | 交付内容 |
|-------|---------|---------|
| M1 后端基础架构 | 第1周末 | Spring Boot项目搭建、数据库配置 |
| M2 后端认证模块 | 第2周末 | 用户注册、登录、JWT认证 |
| M3 后端核心功能 | 第4周末 | 任务管理、文件上传、AI解析 |
| M4 后端飞书集成 | 第5周末 | 飞书消息、多维表格、Webhook |
| M5 后端完成 | 第7周末 | 配置管理、审计日志、服务管理 |
| M6 后端测试完成 | 第8周末 | 单元测试、集成测试、文档 |
| M7 前端基础架构 | 第2周末 | Vue 3项目、路由、状态管理 |
| M8 前端核心页面 | 第5周末 | 上传、编辑、列表页面 |
| M9 前端完成 | 第7周末 | 配置、审计、用户、服务页面 |
| M10 项目整体完成 | 第8周末 | 前后端联调、测试、部署 |

### 11.3 甘特图

```
Week:  1   2   3   4   5   6   7   8
       |---|---|---|---|---|---|---|---|
后端:  [====基础====][===认证===][====任务=====][===飞书===][==配置==][==测试==]
       |---|---|---|---|---|---|---|---|
前端:          [====基础====][===上传===][===编辑===][===列表===][==其他==][==测试==]
       |---|---|---|---|---|---|---|---|
联调:                              [=============联调=============]
       |---|---|---|---|---|---|---|---|
```

---

## 十二、风险评估与应对

### 12.1 技术风险

| 风险 | 影响 | 概率 | 应对措施 |
|-----|-----|-----|---------|
| SSE流式接口性能 | 高 | 中 | 使用异步处理，合理配置线程池 |
| 飞书SDK稳定性 | 中 | 低 | 官方SDK，提供降级方案 |
| AI接口兼容 | 高 | 中 | 封装AI服务层，支持多provider |
| 大文件上传 | 中 | 低 | 配置nginx上传限制，异步处理 |

### 12.2 项目风险

| 风险 | 影响 | 概率 | 应对措施 |
|-----|-----|-----|---------|
| 工期延误 | 高 | 中 | 设置缓冲期，优先级排序 |
| 人员变动 | 高 | 低 | 文档完善，知识传递 |
| 需求变更 | 中 | 中 | 敏捷迭代，变更控制 |

---

## 十三、验收标准

### 13.1 功能验收

- [ ] 用户认证：注册、登录、登出、Token刷新
- [ ] 任务管理：创建、查询、编辑、确认、删除
- [ ] 文件上传：单文件、多文件、SSE流式上传
- [ ] AI识别：图片解析、结果标准化、夜班检测
- [ ] 飞书集成：消息发送、多维表格写入、Webhook接收
- [ ] 配置管理：配置CRUD、提示词管理
- [ ] 审计日志：操作记录、查询筛选
- [ ] 服务管理：服务启停、状态监控

### 13.2 性能验收

- [ ] 接口响应时间：P95 < 500ms
- [ ] 文件上传：支持最大10MB图片
- [ ] 并发支持：支持50并发请求
- [ ] SSE推送：实时性 < 100ms

### 13.3 安全验收

- [ ] 认证安全：密码加密存储、Token时效控制
- [ ] 接口安全：JWT认证、权限校验
- [ ] 数据安全：SQL注入防护、XSS防护
- [ ] 文件安全：文件类型校验、存储隔离

---

## 十四、附录

### 14.1 技术选型理由

**后端**：
- **Spring Boot**：企业级成熟框架，生态完善，社区活跃
- **MyBatis**：SQL精细控制，适合复杂业务场景，学习曲线平缓
- **Druid**：阿里巴巴开源连接池，功能丰富，监控完善

**前端**：
- **Vue 3**：Composition API提供更好的逻辑复用，TypeScript支持好
- **Element Plus**：企业级组件库，文档完善，主题定制灵活
- **Pinia**：Vue官方推荐状态管理，TypeScript友好，API简洁

### 14.2 参考文档

- Spring Boot官方文档：https://spring.io/projects/spring-boot
- MyBatis中文文档：https://mybatis.org/mybatis-3/zh/index.html
- Vue 3官方文档：https://vuejs.org/
- Element Plus文档：https://element-plus.org/

---

**文档维护记录**：

| 版本 | 日期 | 修改人 | 修改内容 |
|-----|------|-------|---------|
| v1.0 | 2026-05-20 | - | 初始版本 |
