# 配置管理系统总结

## 系统配置方式

系统支持两种配置方式，通过 `.env` 文件中的 `USE_DATABASE` 环境变量切换：

| 配置方式 | USE_DATABASE | 配置文件 | 需要数据库 |
|---------|--------------|---------|-----------|
| 数据库模式 | `true`（默认） | 无 | ✅ 是 |
| 文件模式 | `false` | `config.yaml` | ❌ 否 |

## 配置项清单

无论使用哪种模式，以下配置项都必须提供：

### 环境变量配置（.env）

| 配置项 | 说明 | 必填 | 示例 |
|--------|------|------|------|
| `FEISHU_APP_ID` | 飞书应用 ID | 是 | `cli_xxxxxx` |
| `FEISHU_APP_SECRET` | 飞书应用密钥 | 是 | `xxxxxx` |
| `MIMO_API_KEY` | MiMo AI API 密钥 | 是 | `sk-xxxxxx` |
| `MIMO_API_URL` | MiMo API 地址 | 否 | `https://api.mimo.com/v2/vision` |
| `USE_DATABASE` | 是否使用数据库 | 否 | `true` 或 `false` |
| `CONFIG_FILE` | 配置文件路径 | 否 | `./config.yaml` |

### 智能体配置

#### 1. AI 识别提示词

**配置键**：`ai_prompt`

**说明**：告诉 AI 如何识别考勤表格的规则

**内容**：
- 识别任务说明
- 夜班判断逻辑（根据到达/离开时间）
- 质量标记规则（正常/手写/模糊）
- 数据格式要求
- 示例数据

#### 2. 继续提示词

**配置键**：`continue_prompt`

**说明**：当 AI 响应被截断时，请求续写的提示词

**内容**：
- 续写指令
- 格式保持要求

#### 3. 飞书多维表格配置

**配置键**：
- `bitable_app_token`：多维表格的应用 Token
- `bitable_table_id`：数据表的 ID

**说明**：用于将识别结果写入飞书多维表格

#### 4. 字段映射配置

**配置键**：`field_mapping`

**说明**：AI 识别字段与飞书多维表格字段的映射关系

**格式**：JSON 数组

```yaml
field_mapping:
  - aiField: "AI识别字段名"
    feishuField: "飞书字段名"
    type: "string/number/date/datetime"
    required: true/false
    description: "字段说明"
```

## 配置文件结构（config.yaml）

```yaml
# AI 识别配置
ai_prompt: |
  识别法国考勤表格...
  
continue_prompt: |
  请接续上文继续输出...

# 飞书多维表格配置
bitable_app_token: your_app_token_here
bitable_table_id: your_table_id_here

# 字段映射配置
field_mapping:
  - aiField: "NO"
    feishuField: "NO"
    type: "string"
    required: true
    description: "工号"
  # ... 其他字段
```

## 文件清单

### 核心文件

| 文件 | 说明 |
|------|------|
| [ConfigManager.js](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/src/config/ConfigManager.js) | 配置管理服务 |
| [ConfigModel.js](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/src/models/ConfigModel.js) | 配置数据模型（支持双模式） |
| [index.js](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/src/index.js) | 启动入口（自动识别配置模式） |

### 配置文件

| 文件 | 说明 |
|------|------|
| [.env.example](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/.env.example) | 环境变量模板 |
| [config.yaml](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/config.yaml) | 配置文件示例 |

### 脚本文件

| 脚本 | 说明 |
|------|------|
| [generate_config.js](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/scripts/generate_config.js) | 生成配置文件 |
| [verify_prompts.js](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/scripts/verify_prompts.js) | 验证提示词配置 |
| [update_prompts.js](file:///Users/apple/Desktop/智能体/AI考勤识别助手/backend/scripts/update_prompts.js) | 更新提示词到数据库 |

### 文档文件

| 文档 | 说明 |
|------|------|
| [CONFIG_GUIDE.md](file:///Users/apple/Desktop/智能体/AI考勤识别助手/CONFIG_GUIDE.md) | 完整配置说明 |
| [QUICK_START.md](file:///Users/apple/Desktop/智能体/AI考勤识别助手/QUICK_START.md) | 快速开始指南 |

## 使用命令

### 数据库模式

```bash
# 启动服务
npm start

# 更新提示词
npm run update-prompts

# 验证提示词
npm run verify-prompts
```

### 文件模式

```bash
# 生成配置文件
npm run generate-config

# 启动服务
npm start
```

## 配置优先级

1. **环境变量**（`.env`）> 配置文件
2. **数据库配置** > 文件配置（当 `USE_DATABASE=true` 时）
3. **文件配置**（当 `USE_DATABASE=false` 时）

## 注意事项

1. **数据库模式下**，提示词存储在 `plugin_config` 表中
2. **文件模式下**，所有配置存储在 `config.yaml` 中
3. 修改配置后：
   - 数据库模式：下次 API 调用自动生效
   - 文件模式：需要重启服务或重新加载配置
4. `ai_prompt` 是**必填配置**，缺少会导致识别失败
5. 文件模式无需启动数据库，适合轻量部署

## 测试配置

启动服务后，检查日志输出：

```
Config mode: 数据库  # 或 "文件"
```

访问健康检查接口：

```bash
curl http://localhost:3000/health
```
