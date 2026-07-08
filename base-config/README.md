# base-config 运行时配置

仓库根目录下的 **`base-config/`** 由后端 `ConfigPathResolver` 解析（支持从 `backend/` 或仓库根目录启动）。

## 文件说明

| 文件 | 用途 | 修改方式 |
|------|------|----------|
| `prompts.md` | 识别提示词 Markdown 源；空库时可导入 DB | 配置页保存 → 写入 `recognition_prompt`；或直接编辑后重启（见 `attendance.prompt.*`） |
| `feishu.md` | 各国飞书多维表 App Token、Table ID、字段映射 | 编辑后重启或触发配置重载；PC「设置 → 飞书配置」 |
| `countries.md` | 国家元数据（时区、名称）；解析有效国家列表 | 一般只读参考；生效国家以 `feishu.md` / `prompts.md` 章节为准 |
| `permissions.json` | 角色权限：`admin` / `user` / 自定义角色各功能开关 | PC「设置 → 权限管理」保存 |
| `permissions-by-country.json` | **按国家覆盖**部分权限（如 `recordCalibrate`） | PC 权限管理「按国家」页保存 |

### 权限合并优先级（与 `PermissionService` 一致）

1. **`admin` 角色** — 代码层强制拥有全部能力（含 `recordCalibrate`、角色管理等）
2. **`permissions.json`** — 角色默认功能开关（`user` / `test` / 自定义 `role_key`）
3. **`permissions-by-country.json`** — 仅覆盖列出的国家 + 权限键；未列出的国家回退到第 2 层

小程序与 PC 业务校验规则以 `shared/js` 为单源；权限只控制菜单与 API 入口，不替代服务端校验。

内置标准提示词副本（只读参考）：`backend/src/main/resources/canonical/prompts.md`。

## 国家配置解析规则

- 请求国家码：`default` 或 `CN`、`FR`、`NL` 等（大写）。
- **提示词**：优先 `recognition_prompt` 表中该国行；无则回退 `default`。
- **飞书**：优先 `feishu.md` 中该国段落；无则回退 `default` 全局段。
- **当前工作国家**：内存字段（`MarkdownConfigService.currentCountry`），经 API `POST /attendance/api/config/current-country` 设置；重启后恢复为 `default`，除非从配置流程持久化。

## 权限项（permissions.json）

| 键 | 说明 |
|----|------|
| `tasks` | 任务列表与编辑 |
| `country` | 切换工作国家 |
| `aiConfig` | AI 提示词配置 |
| `feishuConfig` | 飞书多维表配置 |
| `users` | 用户管理（管理员） |
| `audit` | 审计日志 |
| `recordCalibrate` | 已确认任务的员工记录校准 |

管理员角色在代码中强制保留 `recordCalibrate` 等管理权限。

## 与环境变量的关系

敏感项在 `backend/.env` 或环境变量中配置，**不**写入本目录：

- `DB_*`、`JWT_SECRET`
- `FEISHU_APP_ID` / `FEISHU_APP_SECRET`
- `MIMO_API_KEY` / `MIMO_MODEL`

详见 [docs/architecture-and-config.md](../docs/architecture-and-config.md)。
