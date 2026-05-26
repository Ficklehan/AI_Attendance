# AI考勤智能助手 - 项目迁移指南

## 目录
1. [迁移前准备](#迁移前准备)
2. [数据库初始化详细说明](#数据库初始化详细说明)
3. [环境变量配置](#环境变量配置)
4. [后端项目迁移](#后端项目迁移)
5. [前端项目迁移](#前端项目迁移)
6. [启动验证](#启动验证)
7. [常见问题](#常见问题)

---

## 迁移前准备

### 环境要求
- **JDK**: 1.8+
- **Maven**: 3.6+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **操作系统**: Windows / Mac / Linux

### 需要迁移的内容清单
- ✅ 项目源代码（整个项目文件夹）
- ✅ 数据库数据（如果需要迁移现有数据）
- ✅ 环境配置文件
- ✅ 上传文件（可选）

---

## 数据库初始化详细说明

### 1. 数据库表结构说明

项目使用 5 个核心表：

#### users - 用户表
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | VARCHAR(64) | 用户ID（主键） |
| username | VARCHAR(50) | 用户名（唯一） |
| email | VARCHAR(100) | 邮箱（唯一） |
| password_hash | VARCHAR(255) | 密码哈希值 |
| feishu_user_id | VARCHAR(64) | 飞书用户ID |
| role | ENUM | 角色（admin/user） |
| real_name | VARCHAR(100) | 真实姓名 |
| employee_id | VARCHAR(50) | 员工编号 |
| status | ENUM | 状态（active/inactive/deleted） |
| last_login_at | DATETIME | 最后登录时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### tasks - 任务表
| 字段 | 类型 | 说明 |
|-----|------|------|
| task_id | VARCHAR(64) | 任务ID（主键） |
| user_id | VARCHAR(64) | 创建者用户ID |
| file_key | VARCHAR(128) | 原始文件key或JSON数组 |
| status | ENUM | 任务状态 |
| raw_data | JSON | AI解析的原始数据 |
| confirmed_data | JSON | 用户确认后的数据 |
| ai_raw_output | TEXT | AI原始输出 |
| processed_by | VARCHAR(512) | 处理人信息 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### plugin_config - 配置表
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | INT | 主键（自增） |
| config_key | VARCHAR(100) | 配置键（唯一） |
| config_value | TEXT | 配置值 |
| config_type | ENUM | 配置类型 |
| description | TEXT | 配置描述 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### audit_logs - 审计日志表
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键（自增） |
| user_id | VARCHAR(64) | 操作用户ID |
| username | VARCHAR(128) | 操作用户名 |
| action | VARCHAR(50) | 操作类型 |
| target_type | VARCHAR(50) | 目标类型 |
| target_id | VARCHAR(64) | 目标ID |
| details | JSON | 操作详情 |
| created_at | DATETIME | 操作时间 |

#### logs - 系统日志表
| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键（自增） |
| task_id | VARCHAR(64) | 关联任务ID |
| log_type | VARCHAR(32) | 日志类型 |
| content | TEXT | 日志内容 |
| created_at | DATETIME | 创建时间 |

---

### 2. 数据库初始化步骤

#### 方式一：使用 SQL 脚本初始化（推荐用于全新环境）

**步骤 1：进入 MySQL 命令行**
```bash
mysql -u root -p
```
输入密码后进入 MySQL。

**步骤 2：执行初始化脚本**

有两种方式执行：

**方式 A：在 MySQL 命令行中执行**
```sql
source /path/to/your/project/backend/config/init.sql;
```

**方式 B：在操作系统命令行中执行**
```bash
mysql -u root -p < /path/to/your/project/backend/config/init.sql
```

**步骤 3：验证数据库创建成功**
```sql
USE attendance_assistant;
SHOW TABLES;
```
应该看到以下 5 个表：
- audit_logs
- logs
- plugin_config
- tasks
- users

#### 方式二：从现有数据库迁移数据（需要保留旧数据）

如果要从旧电脑迁移数据：

**步骤 1：在旧电脑上导出数据库**
```bash
mysqldump -u root -p attendance_assistant > attendance_backup.sql
```

**步骤 2：在新电脑上导入数据库**
```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS attendance_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p attendance_assistant < attendance_backup.sql
```

---

### 3. 初始数据说明

初始化脚本会自动创建以下数据：

#### 默认管理员账号
| 项目 | 值 |
|-----|-----|
| 用户名 | admin |
| 密码 | admin123 |
| 邮箱 | admin@example.com |
| 角色 | admin |
| 姓名 | 系统管理员 |

**重要提示**：首次登录后请立即修改默认密码！

#### 默认配置项
| 配置键 | 说明 |
|--------|------|
| feishu_bitable_app_token_DEFAULT | 飞书多维表格 APP Token |
| feishu_bitable_table_id_DEFAULT | 飞书多维表格 Table ID |
| feishu_field_mapping_DEFAULT | 字段映射配置（JSON格式） |
| recognition_batch_size | 识别批次大小，默认 100 |
| auto_confirm | 是否自动确认，默认 false |
| notification_enabled | 是否启用通知，默认 true |

---

## 环境变量配置

### 1. 配置文件位置

在项目根目录下：
```
backend/
  ├── .env.example          # 配置示例文件
  └── .env                  # 实际配置文件（需要自己创建）
```

### 2. 创建配置文件

```bash
# 复制示例配置文件
cd /path/to/your/project/backend
cp .env.example .env
```

### 3. 详细配置说明

编辑 `.env` 文件，填入实际的配置值：

```env
# ============================================
# 飞书应用配置
# ============================================
# 飞书应用的 App ID（在飞书开放平台获取）
FEISHU_APP_ID=cli_xxxxxxxxxxxxxxxx

# 飞书应用的 App Secret
FEISHU_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# 飞书事件订阅的加密 Key（可选）
FEISHU_ENCRYPT_KEY=

# 飞书事件订阅的验证 Token（可选）
FEISHU_VERIFICATION_TOKEN=

# ============================================
# 飞书多维表格配置
# ============================================
# 飞书多维表格的 APP Token
BITABLE_APP_TOKEN=

# 飞书多维表格的 Table ID
BITABLE_TABLE_ID=

# ============================================
# AI服务配置
# ============================================
# MiMo API Key（在小米Mimo开放平台获取）
MIMO_API_KEY=your_mimo_api_key

# MiMo API地址（可选，默认使用官方地址）
MIMO_API_URL=https://api.mimo.com/v2/vision

# ============================================
# 数据库配置
# ============================================
# MySQL 数据库地址
DB_HOST=localhost

# MySQL 端口
DB_PORT=3306

# MySQL 用户名
DB_USER=root

# MySQL 密码
DB_PASSWORD=12345678

# 数据库名称（保持默认即可）
DB_NAME=attendance_assistant

# ============================================
# JWT配置
# ============================================
# JWT 密钥（生产环境请修改为复杂的随机字符串）
JWT_SECRET=your-jwt-secret-key-change-in-production

# JWT Token 过期时间（毫秒），默认 7 天
JWT_EXPIRATION=604800000
```

---

## 后端项目迁移

### 1. 项目迁移

将整个项目文件夹复制到新电脑：
```
# 源电脑
# 打包项目（可选，用于压缩传输）
cd /path/to/project
tar -czf attendance_project.tar.gz AI考勤智能体/

# 目标电脑
# 解压项目
tar -xzf attendance_project.tar.gz
```

### 2. Maven 依赖安装

进入后端目录，安装依赖：
```bash
cd /path/to/project/backend
mvn clean install -DskipTests
```

如果网络较慢，可以配置国内 Maven 镜像：

编辑 `~/.m2/settings.xml` 或项目的 `pom.xml`：
```xml
<mirrors>
  <mirror>
    <id>aliyunmaven</id>
    <mirrorOf>*</mirrorOf>
    <name>阿里云公共仓库</name>
    <url>https://maven.aliyun.com/repository/public</url>
  </mirror>
</mirrors>
```

### 3. 后端编译和启动

#### 开发模式启动
```bash
cd /path/to/project/backend
mvn spring-boot:run
```

#### 编译为 JAR 包并启动
```bash
# 编译
mvn clean package -DskipTests

# 启动（注意：JAR文件名可能不同）
java -jar target/attendance-assistant-1.0.0.jar
```

#### 验证后端启动成功
看到类似日志即表示启动成功：
```
Started AttendanceApplication in X.XXX seconds
```

后端服务将在 **http://localhost:3000/api** 启动。

---

## 前端项目迁移

### 1. 依赖安装

进入前端目录，安装依赖：
```bash
cd /path/to/project/frontend
npm install
```

如果网络较慢，可以配置淘宝镜像：
```bash
npm install --registry=https://registry.npmmirror.com
```

### 2. 前端启动

#### 开发模式启动
```bash
cd /path/to/project/frontend
npm run dev
```

前端服务将在 **http://localhost:5175** 启动。

#### 生产环境构建
```bash
npm run build
# 构建产物在 dist/ 目录，可以使用 Nginx 等部署
```

---

## 启动验证

### 1. 服务健康检查

#### 检查后端
访问：http://localhost:3000/api
应该看到相关响应（如果有健康检查接口），或者无报错。

#### 检查前端
访问：http://localhost:5175
应该能看到登录页面。

### 2. 功能验证流程

#### 步骤 1：登录系统
- 打开浏览器，访问 http://localhost:5175
- 使用默认账号登录：
  - 用户名：`admin`
  - 密码：`admin123`

#### 步骤 2：测试上传功能
- 在首页选择一张考勤图片
- 点击"开始识别"
- 检查是否能正常识别并显示结果

#### 步骤 3：测试数据库连接
- 完成识别后，点击"确认提交"
- 检查任务列表是否正常显示

---

## 文件迁移（可选）

### 上传文件迁移

如果需要迁移已上传的文件：

```bash
# 从旧电脑复制 uploads 目录
# 源路径：backend/uploads/
# 目标路径：backend/uploads/

# 注意保持文件夹结构一致
```

### 日志文件迁移

如果需要保留旧日志：
```bash
# 从旧电脑复制 logs 目录
# 源路径：backend/logs/
# 目标路径：backend/logs/
```

---

## 常见问题

### 1. 数据库连接失败

**问题**：`Communications link failure`

**解决**：
- 检查 MySQL 服务是否启动
- 确认 `.env` 中的数据库配置正确
- 检查防火墙是否阻止了 MySQL 端口

**Linux/Mac检查MySQL状态**：
```bash
# 检查 MySQL 状态
sudo systemctl status mysql

# 或者
sudo service mysql status
```

**Windows检查MySQL状态**：
1. 打开"服务"（services.msc）
2. 找到 MySQL 服务，检查是否在运行

---

### 2. Maven 依赖下载失败

**问题**：依赖包下载报错

**解决**：
- 配置阿里云 Maven 镜像
- 检查网络连接
- 尝试多次运行 `mvn clean install`

---

### 3. 前端 npm install 失败

**问题**：node_modules 安装报错

**解决**：
```bash
# 清除缓存
npm cache clean --force

# 删除 node_modules 和 package-lock.json
rm -rf node_modules package-lock.json

# 重新安装
npm install
```

---

### 4. 端口被占用

**问题**：`Port 3000 was already in use`

**解决**：
- 查找并关闭占用端口的进程

**Linux/Mac**：
```bash
# 查找占用 3000 端口的进程
lsof -ti :3000

# 关闭进程
kill -9 <进程ID>
```

**Windows**：
```cmd
# 查找占用 3000 端口的进程
netstat -ano | findstr :3000

# 关闭进程
taskkill /PID <进程ID> /F
```

---

### 5. AI 识别失败

**问题**：上传图片后识别失败

**解决**：
- 检查 MIMO_API_KEY 是否配置正确
- 检查 API 地址是否正确
- 查看后端日志确认详细错误
- 确认 API Key 有足够的配额

---

## 技术支持

如果遇到其他问题：
1. 查看后端日志：`backend/logs/attendance-assistant.log`
2. 查看浏览器控制台（F12）的错误信息
3. 检查环境变量配置是否完整正确

---

## 快速检查清单

迁移完成后，请确认以下项目：

- [ ] MySQL 数据库已初始化
- [ ] 数据库表已创建（5个表）
- [ ] `.env` 文件已配置并包含所有必需项
- [ ] 后端依赖已安装 (`mvn clean install`)
- [ ] 前端依赖已安装 (`npm install`)
- [ ] 后端服务能正常启动（端口 3000）
- [ ] 前端服务能正常启动（端口 5175）
- [ ] 能使用默认账号登录
- [ ] 能正常上传和识别图片
- [ ] 任务状态能正常更新

---

祝您迁移顺利！如有问题，请参考项目 README.md 或提交 Issue。
