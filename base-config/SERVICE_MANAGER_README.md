# AI考勤助手 - 服务管理系统

## 🚀 快速开始

### 方法一：使用简单脚本（推荐）- 最简单！

直接运行，无需任何依赖：

```bash
# 在项目根目录运行
node start-services.js
```

这会一键启动前后端服务，日志直接显示在终端上！

**其他命令：**
```bash
node start-services.js backend   # 仅启动后端
node start-services.js frontend  # 仅启动前端
node start-services.js stop      # 停止所有服务
node start-services.js help      # 查看帮助
```

### 方法二：使用 Web 管理界面

```bash
node service-manager.js
```

然后打开浏览器访问：**http://localhost:3001**

### 方法三：使用应用内服务管理

1. 先手动启动后端服务
2. 登录应用后，点击导航栏的「**服务管理**」（🔧）

---

## 📋 功能说明

### start-services.js（推荐）

- ✅ **最简单的使用方式**：直接运行即可
- ✅ **实时日志**：服务输出直接显示在终端
- ✅ **命令行操作**：不需要浏览器
- ✅ **一键启动/停止**：同时管理前后端

### service-manager.js

- 提供 **Web 管理界面**
- 可以通过浏览器管理服务
- 需要占用端口 3001

### 应用内服务管理

- 集成在应用中
- 需要先启动后端
- 提供图形化界面

---

## 💻 命令行用法

### start-services.js（简单脚本）

```bash
# 启动所有服务（前后端）
node start-services.js

# 仅启动后端
node start-services.js backend

# 仅启动前端
node start-services.js frontend

# 停止所有服务
node start-services.js stop

# 查看帮助
node start-services.js help
```

### service-manager.js（Web 版本）

```bash
# 启动 Web 管理界面
node service-manager.js

# 使用命令行启动服务
node service-manager.js start
node service-manager.js start backend
node service-manager.js start frontend

# 使用命令行停止服务
node service-manager.js stop
```

---

## ⚙️ 前置准备

确保已完成以下步骤：

1. **安装 Node.js** (版本 >= 18.0.0)
2. **安装依赖**：
```bash
# 后端依赖
cd backend
npm install

# 前端依赖
cd ../frontend
npm install
```

---

## 📁 文件说明

| 文件 | 说明 |
|------|------|
| `start-services.js` | **简单脚本**（推荐使用） |
| `service-manager.js` | Web 管理界面版本 |
| `backend/src/services/ServiceManager.js` | 后端服务管理模块 |
| `backend/src/routes/service.js` | 后端服务管理 API |
| `frontend/src/pages/ServicePage.jsx` | 前端服务管理页面 |

---

## 🔒 安全说明

- 应用内的服务管理功能需要先登录才能访问
- Web 管理界面（service-manager.js）不需要登录，但只应在本地开发环境使用
- 生产环境建议使用专门的进程管理器（如 pm2）

---

## 🎯 使用建议

1. **日常开发**：使用 `start-services.js`，简单直接
2. **需要图形界面**：使用 `service-manager.js`
3. **生产环境**：建议使用 pm2 或 docker 进行服务管理

---

## ❓ 常见问题

**Q: 提示 "npm: command not found"？**

A: 确保 Node.js 已正确安装，在终端运行 `node -v` 确认

**Q: 端口被占用怎么办？**

A: 先停止占用端口的程序，或修改配置中的端口号

**Q: 如何确认服务已启动？**

A: 查看终端日志，或访问对应地址（如 http://localhost:5175）

**Q: 如何停止服务？**

A:
- 使用 Ctrl+C 停止 start-services.js
- 访问 http://localhost:3001 点击停止按钮
- 在应用内点击停止按钮

---

## 📝 示例输出

运行 `node start-services.js` 后的输出：

```
🚀 AI考勤助手 - 服务管理器


📌 正在启动所有服务...

🔧 正在启动后端服务...
📁 工作目录: /path/to/AI考勤识别助手/backend
📡 端口: 3000
---
✅ 后端进程已启动，等待服务启动...

🎨 正在启动前端服务...
📁 工作目录: /path/to/AI考勤识别助手/frontend
📡 端口: 5175
---
✅ 前端进程已启动，等待服务启动...

╔═══════════════════════════════════════════════════════════════╗
║                                                               ║
║   ✅ 所有服务启动完成!                                        ║
║                                                               ║
║   🌐 前端地址: http://localhost:5175                          ║
║   🔧 后端地址: http://localhost:3000                         ║
║                                                               ║
║   📝 服务日志将显示在上方                                     ║
║   🛑 按 Ctrl+C 停止所有服务并退出                             ║
║                                                               ║
╚═══════════════════════════════════════════════════════════════╝
```
