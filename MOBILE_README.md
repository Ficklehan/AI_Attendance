# 移动端开发指南

## 概述

本项目提供了两种移动端解决方案：

1. **飞书小程序** - 集成在飞书生态中，适合飞书用户使用
2. **H5移动端** - 通用的移动Web应用，可在任何浏览器中使用

---

## 一、飞书小程序

### 项目结构

```
feishu-miniprogram/
├── app.js                 # 应用入口
├── app.json               # 应用配置
├── app.css                # 全局样式
├── package.json           # 依赖配置
├── pages/                 # 页面
│   ├── index/             # 首页
│   ├── camera/            # 拍照扫描页
│   ├── result/            # 识别结果页
│   ├── chat/              # AI聊天页
│   ├── tasks/             # 任务列表页
│   └── profile/           # 个人中心页
└── utils/                 # 工具模块
    ├── request.js         # 请求封装
    └── api.js             # API模块
```

### 功能特性

- ✅ 拍照扫描（带引导框动画）
- ✅ 图片选择与上传
- ✅ AI对话式识别
- ✅ 任务管理
- ✅ 国家配置切换
- ✅ 用户登录与状态管理

### 开发指南

1. **安装依赖**
   ```bash
   cd feishu-miniprogram
   npm install
   ```

2. **配置AppID**
   
   在飞书开放平台获取AppID，配置到项目中

3. **开发调试**
   
   使用飞书开发者工具打开项目目录进行调试

4. **发布上线**
   
   在飞书开发者工具中提交审核

---

## 二、H5移动端

### 项目结构

```
mobile/
├── index.html             # HTML入口
├── package.json           # 依赖配置
├── vite.config.js         # Vite配置
├── .env                   # 环境变量
└── src/
    ├── main.js            # 应用入口
    ├── App.vue            # 根组件
    ├── router/            # 路由配置
    │   └── index.js
    ├── stores/            # Pinia状态管理
    │   └── auth.js
    ├── api/               # API模块
    │   ├── request.js
    │   └── index.js
    ├── components/        # 公共组件
    │   └── TabBar.vue
    ├── views/             # 页面组件
    │   ├── Home.vue       # 首页
    │   ├── Camera.vue     # 拍照扫描页
    │   ├── Chat.vue       # AI聊天页
    │   ├── Tasks.vue      # 任务列表页
    │   ├── Profile.vue    # 个人中心页
    │   ├── Login.vue      # 登录页
    │   └── Result.vue     # 结果页
    └── styles/            # 样式
        └── global.css
```

### 功能特性

- ✅ 响应式设计（支持各种屏幕尺寸）
- ✅ 拍照扫描（带扫描框引导动画）
- ✅ 实时摄像头预览
- ✅ 图片选择与上传
- ✅ AI对话式识别（打字机效果）
- ✅ 任务列表与筛选
- ✅ 个人中心与统计
- ✅ 登录认证
- ✅ 安全区域适配（iPhone刘海屏等）

### 快速开始

#### 1. 安装依赖

```bash
cd mobile
npm install
```

#### 2. 配置环境变量

编辑 `.env` 文件：

```env
VITE_API_BASE_URL=http://localhost:8080
```

#### 3. 启动开发服务器

```bash
npm run dev
```

访问 `http://localhost:5174` 即可看到应用

#### 4. 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist` 目录

### 部署说明

1. 将 `dist` 目录下的所有文件上传到Web服务器
2. 配置Nginx或其他Web服务器
3. 确保API地址配置正确

### 页面说明

| 页面 | 路由 | 功能 |
|------|------|------|
| 首页 | `/` | 图片选择、国家配置、开始识别 |
| 拍照扫描 | `/camera` | 调用摄像头、拍照、扫描引导 |
| 识别结果 | `/result/:id` | 查看识别详情、确认提交 |
| AI助手 | `/chat` | 对话式AI、图片识别 |
| 任务列表 | `/tasks` | 任务筛选、查看历史 |
| 个人中心 | `/profile` | 用户信息、统计数据 |
| 登录 | `/login` | 用户登录 |

---

## 三、技术栈

### H5移动端
- Vue 3 (Composition API)
- Vite 5
- Pinia (状态管理)
- Vue Router 4
- Axios

### 飞书小程序
- 飞书小程序原生API
- 飞书开发者工具

---

## 四、注意事项

### 摄像头权限

H5版本需要HTTPS环境才能调用摄像头（localhost除外）

### 飞书小程序配置

需要在飞书开放平台：
1. 创建小程序应用
2. 配置服务器域名白名单
3. 获取AppID和AppSecret

### 后端对接

确保后端服务正常运行，并配置了正确的CORS策略

---

## 五、相关文档

- [飞书小程序开发文档](https://open.feishu.cn/document/uYjL24iN)
- [Vue 3 文档](https://cn.vuejs.org/)
- [Vite 文档](https://cn.vitejs.dev/)
