# 智能体配置说明

## 配置方式

系统支持两种配置方式：
1. **数据库配置**（默认）- 配置存储在数据库 `plugin_config` 表中
2. **文件配置** - 配置存储在 `config.yaml` 文件中

## 启用配置

在 `.env` 文件中设置：

```env
# 是否启用数据库（true/false）
USE_DATABASE=true

# 仅在 USE_DATABASE=false 时使用
CONFIG_FILE=./config.yaml
```

## 配置文件格式（config.yaml）

当 `USE_DATABASE=false` 时，系统会读取 `config.yaml` 文件：

```yaml
# AI 识别配置
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

  示例：
  ["1","张三","中介A","MATIN","2026-05-17","08:00","18:00","60","","正常",false]
  ["2","李四","中介B","NUIT","2026-05-17","22:00","06:00","60","","正常;夜班",false]
  ["3","王五","中介C","MATIN","2026-05-17","08:30","17:30","60","","手写",false]
  ["4","???","中介D","SOIR","2026-05-17","???","???","30","","模糊",false]
  ["5","李明","中介E","NUIT","2026-05-17","23:00","07:00","30","","正常;夜班",false]

continue_prompt: |
  请接续上文继续输出，不要重复已有内容，保持相同格式。

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
  - aiField: "NOM_PRENOM"
    feishuField: "NOM"
    type: "string"
    required: true
    description: "姓名"
  - aiField: "AGENCE_INTERIMAIRE"
    feishuField: "AGENCE"
    type: "string"
    required: false
    description: "中介"
  - aiField: "SHIFT"
    feishuField: "SHIFT"
    type: "string"
    required: false
    description: "班次"
  - aiField: "Date"
    feishuField: "DATE"
    type: "date"
    required: true
    description: "日期"
  - aiField: "ARRIVEE_DATETIME"
    feishuField: "ARRIVE"
    type: "datetime"
    required: true
    description: "到达时间"
  - aiField: "DEPAR_DATETIME"
    feishuField: "DEPAR"
    type: "datetime"
    required: true
    description: "离开时间"
  - aiField: "PAUSE"
    feishuField: "PAUS"
    type: "number"
    required: true
    description: "休息时间"
  - aiField: "CHECKER"
    feishuField: "CHECKER"
    type: "string"
    required: false
    description: "检查器"
  - aiField: "SmartMark"
    feishuField: "Mark"
    type: "string"
    required: false
    description: "标记"
```

## 配置项说明

### AI 识别配置

#### ai_prompt
- **说明**：AI 识别考勤表格的提示词
- **必填**：是
- **格式**：多行文本

#### continue_prompt
- **说明**：AI 继续输出的提示词
- **必填**：是
- **格式**：单行文本

### 飞书配置

#### bitable_app_token
- **说明**：飞书多维表格的应用 Token
- **获取方式**：打开多维表格 → 地址栏中的 app_token 参数
- **格式**：`xxxxxx`（18位字母数字组合）

#### bitable_table_id
- **说明**：飞书多维表格的数据表 ID
- **获取方式**：打开数据表 → 地址栏中的 table_id 参数
- **格式**：`tblxxxxxx`（开头为 tbl 的字符串）

### 字段映射配置

#### field_mapping
- **说明**：AI 识别字段与飞书多维表格字段的映射关系
- **必填**：是
- **格式**：JSON 数组

每个映射项包含：
- `aiField`：AI 识别返回的字段名
- `feishuField`：飞书多维表格的字段名
- `type`：数据类型（string/number/date/datetime）
- `required`：是否必填
- `description`：字段说明

## 快速开始

### 方式一：使用数据库配置（推荐）

1. 修改 `.env` 文件：
```env
USE_DATABASE=true
```

2. 启动数据库和后端服务：
```bash
docker-compose up -d mysql
npm start
```

3. 通过后台管理界面配置各项参数

### 方式二：使用文件配置

1. 修改 `.env` 文件：
```env
USE_DATABASE=false
CONFIG_FILE=./config.yaml
```

2. 编辑 `config.yaml` 文件，填入你的配置

3. 启动后端服务：
```bash
npm start
```

## 配置验证

启动服务后，系统会自动验证配置：

1. **数据库配置**：检查 `plugin_config` 表中的配置项
2. **文件配置**：检查 `config.yaml` 文件格式和内容

如果配置有问题，系统会在启动时输出错误信息。

## 常见问题

### Q: 配置文件中字段映射不正确
A: 确保 `aiField` 与 AI 返回的字段名完全一致，包括大小写

### Q: 飞书多维表格写入失败
A: 检查：
1. `bitable_app_token` 和 `bitable_table_id` 是否正确
2. 应用是否有多维表格的读写权限
3. 字段类型是否匹配

### Q: AI 识别结果不准确
A: 调整 `ai_prompt` 中的规则说明，添加更多示例数据
