# 快速开始指南

## 选择配置方式

### 方式一：使用数据库配置（默认，推荐）

适用于需要后台管理界面、动态修改配置的场景。

#### 1. 启动数据库

```bash
cd backend
docker-compose up -d mysql
```

#### 2. 配置环境变量

复制 `.env.example` 为 `.env`：

```bash
cp .env.example .env
```

编辑 `.env`：

```env
USE_DATABASE=true

# 飞书配置
FEISHU_APP_ID=cli_xxxxxx
FEISHU_APP_SECRET=xxxxxx

# 飞书多维表格
BITABLE_APP_TOKEN=xxxxxx
BITABLE_TABLE_ID=xxxxxx

# AI 配置
MIMO_API_KEY=xxxxxx
MIMO_API_URL=https://api.mimo.com/v2/vision
```

#### 3. 安装依赖并启动

```bash
npm install
npm start
```

#### 4. 配置 AI 提示词

通过 API 或直接运行脚本更新提示词：

```bash
npm run update-prompts
```

或通过后台界面配置。

---

### 方式二：使用文件配置（无需数据库）

适用于轻量部署、配置固定的场景。

#### 1. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`：

```env
USE_DATABASE=false
CONFIG_FILE=./config.yaml

# 飞书配置
FEISHU_APP_ID=cli_xxxxxx
FEISHU_APP_SECRET=xxxxxx

# AI 配置
MIMO_API_KEY=xxxxxx
MIMO_API_URL=https://api.mimo.com/v2/vision
```

**注意**：当 `USE_DATABASE=false` 时，以下数据库配置可以省略或保留：

```env
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=12345678
DB_NAME=attendance_assistant
```

#### 2. 生成配置文件

```bash
npm run generate-config
```

这会在 `backend/config.yaml` 生成示例配置。

#### 3. 编辑配置文件

打开 `config.yaml`，填入你的飞书多维表格凭证：

```yaml
# 飞书多维表格配置
bitable_app_token: your_app_token_here
bitable_table_id: your_table_id_here
```

根据需要调整：
- `ai_prompt`：AI 识别规则
- `continue_prompt`：续写提示词
- `field_mapping`：字段映射关系

#### 4. 安装依赖并启动

```bash
npm install
npm start
```

---

## 获取飞书配置

### 飞书应用配置

1. 前往 [飞书开放平台](https://open.feishu.cn/)
2. 创建企业自建应用
3. 获取 `App ID` 和 `App Secret`

### 飞书多维表格配置

1. 打开飞书，创建或打开一个多维表格
2. 从 URL 获取配置：
   - URL 格式：`https://xxx.feishu.cn/base/{app_token}?table={table_id}`
   - `app_token` = `bitable_app_token`
   - `table_id` = `bitable_table_id`

示例 URL：
```
https://xxx.feishu.cn/base/BascnLyexample123abc?table=tblXyz789
```

对应配置：
```yaml
bitable_app_token: BascnLyexample123abc
bitable_table_id: tblXyz789
```

### 配置多维表格字段

确保你的多维表格有以下字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| NO | 单行文本 | 工号 |
| NOM | 单行文本 | 姓名 |
| AGENCE | 单行文本 | 中介（可选） |
| SHIFT | 单行文本 | 班次（可选） |
| DATE | 日期 | 日期 |
| ARRIVE | 日期+时间 | 到达时间 |
| DEPAR | 日期+时间 | 离开时间 |
| PAUS | 数字 | 休息时间（分钟） |
| CHECKER | 单行文本 | 检查器（可选） |
| Mark | 单行文本 | 标记 |

---

## 验证配置

启动服务后，访问健康检查：

```bash
curl http://localhost:3000/health
```

应该返回：

```json
{
  "status": "ok",
  "timestamp": "2026-05-19T10:00:00.000Z"
}
```

查看启动日志，确认配置模式：

```
Config mode: 数据库  # 或 "文件"
```

---

## 常见问题

### Q: 提示词未配置错误

**使用数据库模式时**：

运行提示词更新脚本：

```bash
npm run update-prompts
```

**使用文件模式时**：

检查 `config.yaml` 是否包含 `ai_prompt` 配置项。

### Q: 飞书多维表格写入失败

1. 检查 `bitable_app_token` 和 `bitable_table_id` 是否正确
2. 确认应用有多维表格权限：
   - 在飞书开放平台 → 应用功能 → 权限管理
   - 添加：`base:app` 和 `base:record:create`
3. 确认字段名称与 `field_mapping` 一致

### Q: 数据库连接错误

仅在使用数据库模式时需要数据库。切换到文件模式：

```env
USE_DATABASE=false
```

### Q: 配置文件格式错误

YAML 格式要求：
- 使用空格缩进（2个空格）
- 字符串可以用引号，但推荐不使用
- 多行文本使用 `|` 符号

验证 YAML 格式：

```bash
npm run generate-config
```

---

## 下一步

- 查看完整配置说明：[CONFIG_GUIDE.md](./CONFIG_GUIDE.md)
- 配置飞书机器人 Webhook
- 测试图片识别功能
