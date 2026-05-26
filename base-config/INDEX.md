# 配置文件快速索引

> 配置文件统一存放在 `base-config/` 目录下

## 📋 快速导航

| 配置文件 | 用途 | 修改频率 |
|---------|------|---------|
| [prompts.md](prompts.md) | AI识别提示词 | 低 |
| [feishu.md](feishu.md) | 飞书多维表配置 | 中 |
| [countries.md](countries.md) | 国家代码库 | 极低 |

## 🔗 常用操作

### 1️⃣ 修改AI提示词
```bash
# 编辑文件
vim base-config/prompts.md

# 或通过前端页面（推荐）
# 访问配置管理页面 → AI识别提示词
```

### 2️⃣ 配置新国家的飞书多维表
```bash
# 编辑文件
vim base-config/feishu.md

# 添加新国家配置块
```

### 3️⃣ 查看支持的国家
```bash
# 查看国家列表
cat base-config/countries.md | grep "^|"
```

## 📊 配置文件统计

```
base-config/
├── README.md          # 本文件 - 配置说明
├── prompts.md         # AI提示词（1.7KB）
├── feishu.md          # 飞书配置（8.5KB）
├── countries.md       # 国家库（11.5KB）
├── INDEX.md           # 配置文件索引
├── CONFIG_GUIDE.md    # 配置指南
├── CONFIG_SUMMARY.md  # 配置摘要
├── QUICK_START.md     # 快速入门
└── SERVICE_MANAGER_README.md  # 服务管理器说明
```

## ⚡ 快速参考

### AI提示词位置
```
base-config/prompts.md
  ↓
## 主要识别提示词 → ai_prompt
## 继续输出提示词 → continue_prompt
```

### 飞书配置位置
```
base-config/feishu.md
  ↓
## 全局默认配置 → default
## 国家配置 → FR/CN/DE/US/...
```

### 国家代码查找
```
base-config/countries.md
  ↓
| 代码 | 中文名称 | 时区 |
|------|---------|------|
| FR   | 法国     | Europe/Paris |
| CN   | 中国     | Asia/Shanghai |
```

## 🆘 常见问题

**Q: 如何添加新国家？**
A: 在 `base-config/feishu.md` 中添加新的国家配置块，使用ISO 3166-1代码作为标识

**Q: 提示词修改不生效？**
A: 确保保存了文件，部分配置需要重启后端服务

**Q: 配置文件丢失怎么办？**
A: 系统会自动创建默认配置，也可以从Git历史恢复

**Q: 如何备份配置？**
A: 直接复制整个 `base-config/` 目录即可

## 📝 版本历史

- **v1.0** (2026-05-19)
  - ✨ 初始版本
  - 🎯 支持AI提示词本地存储
  - 🎯 支持按国家配置飞书多维表
  - 🎯 包含全球200+国家代码库

---

**最后更新**：2026-05-19  
**维护者**：AI考勤识别助手团队
