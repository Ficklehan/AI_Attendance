# AttendanceAgent 加班方案配置管理系统 - 设计文档

> 文档版本：v1.0  
> 创建日期：2026-09-09  
> 状态：待确认

---

## 一、项目背景

### 1.1 业务需求

AttendanceAgent（AI考勤智能助手）需要新增**加班方案配置管理功能**，支持按欧洲多地加班方案配置，实现：

- 按国家配置加班规则（日标准工时、周标准工时、加班费率等）
- 自动计算员工加班时长（按日+按周）
- 支持法规默认配置和企业自定义费率
- 年度加班上限管控和告警
- 加班统计报表和导出

### 1.2 核心问题解答

#### ❓ "每天出勤大于8小时，是否要算加班？"

**答案：取决于该员工所在国家的加班方案配置**

| 国家 | 标准工时 | >8小时是否算加班 | 说明 |
|------|---------|-----------------|------|
| 🇩🇪 德国 | 8h/天 | **是**，但受6个月滚动平均约束 | 如果6个月内平均≤8h/天，某天超过8h不算加班 |
| 🇫🇷 法国 | 7h/天 | **是**，且更严格 | 超过7h即算加班，费率125%起步 |
| 🇬🇧 英国 | 合同约定 | **看合同** | 无强制规定，由雇佣合同约定 |
| 🇳🇱 荷兰 | 集体协议 | **看协议** | 无强制规定，由集体协议约定 |
| 🇪🇸 西班牙 | 8h/天 | **是** | 超过8h算加班，年度上限80h |

**核心设计原则：系统必须支持"按国家配置规则"，而非硬编码一种逻辑**

---

## 二、欧洲劳动法规调研

### 2.1 EU工作时间指令（2003/88/EC）

- 平均每周工作时间上限：**48小时**（包括加班）
- 每天最少休息：**11小时**
- 各成员国实施差异显著，各国法规为"地板"而非"天花板"

### 2.2 主要国家规定

#### 🇩🇪 德国

| 项目 | 规定 |
|------|------|
| 标准工作日 | 8小时 |
| 最长工作日 | 10小时（6个月内平均≤8h/天） |
| 标准工作周 | 48小时（含加班） |
| 加班费率 | 无强制规定，通常由集体协议规定（25-30%） |
| 特殊规定 | 6个月滚动平均工时≤8h/天 |

#### 🇫🇷 法国

| 项目 | 规定 |
|------|------|
| 标准工作日 | 7小时（35h/5天） |
| 标准工作周 | 35小时 |
| 加班费率 | 36-43h: 125%，44h+: 150% |
| 年度上限 | 220小时 |
| 特殊规定 | 严格保护，高加班费 |

#### 🇬🇧 英国

| 项目 | 规定 |
|------|------|
| 标准工作日 | 无强制规定 |
| 标准工作周 | 48小时（17周平均，可自愿退出） |
| 加班费率 | 无强制规定，由合同约定 |
| 特殊规定 | 灵活，注重平均工时 |

#### 🇳🇱 荷兰

| 项目 | 规定 |
|------|------|
| 标准工作日 | 无强制规定 |
| 最长工作时间 | 60小时/周（4周平均≤48h） |
| 加班费率 | 无强制规定，由集体协议约定 |
| 特殊规定 | 高度灵活 |

#### 🇪🇸 西班牙

| 项目 | 规定 |
|------|------|
| 标准工作日 | 8小时（40h/5天） |
| 标准工作周 | 40小时 |
| 加班费率 | 集体协议约定（通常75-175%） |
| 年度上限 | 80小时 |
| 特殊规定 | 有年度上限 |

---

## 三、系统设计方案

### 3.1 设计原则

1. **规则配置化**：通过配置表支持任意国家的加班规则，新增国家零代码
2. **计算引擎化**：加班计算作为独立服务，与现有系统松耦合
3. **数据独立化**：加班统计结果独立存储，不侵入现有task_records表
4. **费率灵活化**：支持法规默认配置和企业自定义费率

### 3.2 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端界面                              │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ 加班方案配置页面  │  │ 加班统计看板    │                  │
│  │ /settings/overtime│  │ /overtime       │                  │
│  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      后端服务层                              │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ OvertimeScheme  │  │ OvertimeEngine  │                  │
│  │ Controller      │  │ (计算引擎)      │                  │
│  └─────────────────┘  └─────────────────┘                  │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ OvertimeScheme  │  │ OvertimeSummary │                  │
│  │ Service         │  │ Service         │                  │
│  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      数据存储层                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐          │
│  │overtime_    │ │overtime_    │ │overtime_    │          │
│  │scheme       │ │rule         │ │country_bind │          │
│  └─────────────┘ └─────────────┘ └─────────────┘          │
│  ┌─────────────┐ ┌─────────────┐                          │
│  │overtime_    │ │public_      │                          │
│  │daily_summary│ │holiday      │                          │
│  └─────────────┘ └─────────────┘                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 四、数据库设计

### 4.1 表结构总览

| 表名 | 说明 | 主要字段 |
|------|------|---------|
| `overtime_scheme` | 加班方案主表 | id, scheme_key, scheme_name, country_code, status |
| `overtime_rule` | 加班规则明细表 | id, scheme_id, rule_type, rule_key, rule_value |
| `overtime_country_bind` | 国家-方案绑定表 | id, country_code, scheme_id, effective_from, effective_to |
| `overtime_daily_summary` | 加班日统计表 | id, emp_no, country_code, work_date, overtime_minutes |
| `public_holiday` | 法定节假日表 | id, country_code, holiday_date, holiday_name |

### 4.2 详细表结构

#### 表1：`overtime_scheme` 加班方案主表

```sql
CREATE TABLE IF NOT EXISTS overtime_scheme (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scheme_key VARCHAR(32) NOT NULL COMMENT '方案标识，如 FR_STANDARD、DE_STANDARD',
    scheme_name VARCHAR(128) NOT NULL COMMENT '方案名称，如"法国标准方案"',
    country_code VARCHAR(16) NOT NULL COMMENT '适用国家代码 FR/DE/GB/NL/ES/default',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    description VARCHAR(512) NULL COMMENT '方案说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scheme_key (scheme_key),
    INDEX idx_country (country_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班方案主表';
```

#### 表2：`overtime_rule` 加班规则明细表

```sql
CREATE TABLE IF NOT EXISTS overtime_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scheme_id BIGINT NOT NULL COMMENT '关联 overtime_scheme.id',
    rule_type VARCHAR(32) NOT NULL COMMENT '规则类型：basic|rate|week',
    rule_key VARCHAR(64) NOT NULL COMMENT '规则键',
    rule_value VARCHAR(255) NOT NULL COMMENT '规则值（JSON或数值字符串）',
    rule_unit VARCHAR(16) NULL COMMENT '单位：hour|minute|percent|ratio',
    description VARCHAR(256) NULL COMMENT '规则说明',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scheme_rule (scheme_id, rule_key),
    INDEX idx_scheme (scheme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班规则明细';
```

#### 表3：`overtime_country_bind` 国家-方案绑定表

```sql
CREATE TABLE IF NOT EXISTS overtime_country_bind (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(16) NOT NULL COMMENT '国家代码，default 为全局默认',
    scheme_id BIGINT NOT NULL COMMENT '绑定的加班方案ID',
    effective_from DATE NULL COMMENT '生效日期',
    effective_to DATE NULL COMMENT '失效日期（NULL=永久）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_country_bind (country_code),
    INDEX idx_scheme (scheme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国家加班方案绑定';
```

#### 表4：`overtime_daily_summary` 加班日统计表

```sql
CREATE TABLE IF NOT EXISTS overtime_daily_summary (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    emp_no VARCHAR(64) NOT NULL COMMENT '工号',
    country_code VARCHAR(16) NOT NULL COMMENT '国家',
    work_date DATE NOT NULL COMMENT '工作日期',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '当日总工时（分钟）',
    standard_work_minutes INT NOT NULL DEFAULT 0 COMMENT '当日标准工时（分钟）',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '加班总时长（分钟）',
    overtime_weekday_minutes INT NOT NULL DEFAULT 0 COMMENT '平日加班分钟',
    overtime_weekend_minutes INT NOT NULL DEFAULT 0 COMMENT '周末加班分钟',
    overtime_holiday_minutes INT NOT NULL DEFAULT 0 COMMENT '节假日加班分钟',
    overtime_night_minutes INT NOT NULL DEFAULT 0 COMMENT '夜班加班分钟',
    day_type VARCHAR(16) NOT NULL DEFAULT 'weekday' COMMENT '日期类型：weekday|weekend|holiday',
    scheme_key VARCHAR(32) NULL COMMENT '使用的加班方案',
    source_task_ids TEXT NULL COMMENT '来源任务ID列表（JSON）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_date (emp_no, work_date),
    INDEX idx_country_date (country_code, work_date),
    INDEX idx_emp_month (emp_no, country_code, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班日统计';
```

#### 表5：`public_holiday` 法定节假日表

```sql
CREATE TABLE IF NOT EXISTS public_holiday (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(16) NOT NULL COMMENT '国家代码',
    holiday_date DATE NOT NULL COMMENT '节假日日期',
    holiday_name VARCHAR(128) NOT NULL COMMENT '节假日名称',
    is_recurring TINYINT NOT NULL DEFAULT 0 COMMENT '是否每年重复',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_country_date (country_code, holiday_date),
    INDEX idx_country_year (country_code, holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法定节假日';
```

### 4.3 ER关系图

```
┌──────────────────┐     1:N      ┌──────────────────┐
│ overtime_scheme   │────────────▶│ overtime_rule     │
│ (加班方案主表)     │             │ (规则明细)        │
│                  │             │  - basic规则      │
│  id              │             │  - rate规则       │
│  scheme_key      │             │  - week规则       │
│  country_code    │             │                   │
└───────┬──────────┘             └──────────────────┘
        │
        │ 1:1
        ▼
┌──────────────────┐
│overtime_country   │
│_bind              │
│ (国家方案绑定)     │
│  country_code     │
│  scheme_id        │
└──────────────────┘

┌──────────────────┐
│task_records       │─── 原始考勤数据（已有）
│  emp_no           │
│  work_date        │
│  arrival          │
│  departure        │
│  pause_minutes    │
│  country          │
└───────┬──────────┘
        │ 汇聚计算
        ▼
┌──────────────────┐
│overtime_daily     │
│_summary           │
│ (加班日统计)       │
│  emp_no           │
│  work_date        │
│  total_work_min   │
│  overtime_min     │
│  day_type         │
│  overtime_weekday │
│  overtime_weekend │
│  overtime_holiday │
└──────────────────┘

┌──────────────────┐
│public_holiday     │
│ (法定节假日)       │
│  country_code     │
│  holiday_date     │
│  holiday_name     │
└──────────────────┘
```

---

## 五、规则引擎设计

### 5.1 规则类型体系

`overtime_rule` 表通过 `rule_type` + `rule_key` 组合，定义了一套完整的规则语言：

#### 基础规则（rule_type = basic）

| rule_key | 类型 | 说明 | 示例 |
|----------|------|------|------|
| `daily_standard_hours` | number | 每日标准工时（小时） | DE=8, FR=7, GB=8, NL=8, ES=8 |
| `weekly_standard_hours` | number | 每周标准工时（小时） | DE=48, FR=35, GB=48, NL=40, ES=40 |
| `monthly_standard_hours` | number | 每月标准工时（小时，可选） | ES=176 |
| `overtime_granularity` | string | 加班计算粒度 | `daily`=按日超时 / `weekly`=按周超时 / `both`=日+周 |
| `daily_max_overtime_minutes` | number | 日加班上限（分钟） | DE=120(可延至10h), ES=0(无日上限但年有上限) |
| `annual_max_overtime_hours` | number | 年度加班上限（小时） | FR=220, ES=80 |
| `rolling_average_months` | number | 滚动平均月数 | DE=6（6个月平均≤8h/天） |
| `count_pause_as_work` | boolean | 休息时间是否计入工时 | 一般为false |

#### 加班费率规则（rule_type = rate）

| rule_key | 类型 | 说明 | 示例 |
|----------|------|------|------|
| `rate_weekday` | number | 平日加班倍率（%） | DE=125, FR=125, GB=100(无强制) |
| `rate_weekday_tier2` | number | 平日加班二级倍率（可选） | FR=150 |
| `rate_weekday_tier2_threshold` | number | 二级倍率起算小时 | FR=44(周工时>44h部分) |
| `rate_weekend` | number | 周末加班倍率（%） | DE=150, FR=150, ES=175 |
| `rate_holiday` | number | 节假日加班倍率（%） | DE=200, FR=200, ES=200 |
| `rate_night` | number | 夜班加班额外倍率（%） | 可叠加 |

#### 周判定规则（rule_type = week）

| rule_key | 类型 | 说明 | 示例 |
|----------|------|------|------|
| `week_start_day` | string | 一周起始日 | `monday`(欧洲通用) |
| `weekend_days` | string(JSON) | 周末日列表 | `["saturday","sunday"]` |
| `week_overtime_mode` | string | 周加班计算模式 | `cumulative`=累计超时 / `daily_sum`=每日超时之和 |

### 5.2 各国规则配置示例

#### 法国方案（FR_STANDARD）

```json
{
  "scheme_key": "FR_STANDARD",
  "scheme_name": "法国标准加班方案",
  "country_code": "FR",
  "rules": [
    { "key": "daily_standard_hours", "value": "7", "unit": "hour" },
    { "key": "weekly_standard_hours", "value": "35", "unit": "hour" },
    { "key": "overtime_granularity", "value": "both" },
    { "key": "annual_max_overtime_hours", "value": "220", "unit": "hour" },
    { "key": "rate_weekday", "value": "125", "unit": "percent" },
    { "key": "rate_weekday_tier2", "value": "150", "unit": "percent" },
    { "key": "rate_weekday_tier2_threshold", "value": "44", "unit": "hour" },
    { "key": "rate_weekend", "value": "150", "unit": "percent" },
    { "key": "rate_holiday", "value": "200", "unit": "percent" },
    { "key": "week_start_day", "value": "monday" },
    { "key": "weekend_days", "value": "[\"saturday\",\"sunday\"]" }
  ]
}
```

#### 德国方案（DE_STANDARD）

```json
{
  "scheme_key": "DE_STANDARD",
  "scheme_name": "德国标准加班方案",
  "country_code": "DE",
  "rules": [
    { "key": "daily_standard_hours", "value": "8", "unit": "hour" },
    { "key": "weekly_standard_hours", "value": "48", "unit": "hour" },
    { "key": "overtime_granularity", "value": "both" },
    { "key": "daily_max_overtime_minutes", "value": "120", "unit": "minute" },
    { "key": "rolling_average_months", "value": "6" },
    { "key": "rate_weekday", "value": "125", "unit": "percent" },
    { "key": "rate_weekend", "value": "150", "unit": "percent" },
    { "key": "rate_holiday", "value": "200", "unit": "percent" },
    { "key": "week_start_day", "value": "monday" },
    { "key": "weekend_days", "value": "[\"saturday\",\"sunday\"]" },
    { "key": "week_overtime_mode", "value": "cumulative" }
  ]
}
```

#### 英国方案（GB_STANDARD）

```json
{
  "scheme_key": "GB_STANDARD",
  "scheme_name": "英国标准加班方案",
  "country_code": "GB",
  "rules": [
    { "key": "daily_standard_hours", "value": "8", "unit": "hour" },
    { "key": "weekly_standard_hours", "value": "48", "unit": "hour" },
    { "key": "overtime_granularity", "value": "weekly" },
    { "key": "rolling_average_weeks", "value": "17" },
    { "key": "rate_weekday", "value": "100", "unit": "percent" },
    { "key": "rate_weekend", "value": "100", "unit": "percent" },
    { "key": "rate_holiday", "value": "100", "unit": "percent" },
    { "key": "opt_out_allowed", "value": "true" },
    { "key": "week_start_day", "value": "monday" }
  ]
}
```

#### 西班牙方案（ES_STANDARD）

```json
{
  "scheme_key": "ES_STANDARD",
  "scheme_name": "西班牙标准加班方案",
  "country_code": "ES",
  "rules": [
    { "key": "daily_standard_hours", "value": "8", "unit": "hour" },
    { "key": "weekly_standard_hours", "value": "40", "unit": "hour" },
    { "key": "overtime_granularity", "value": "both" },
    { "key": "annual_max_overtime_hours", "value": "80", "unit": "hour" },
    { "key": "rate_weekday", "value": "125", "unit": "percent" },
    { "key": "rate_weekend", "value": "175", "unit": "percent" },
    { "key": "rate_holiday", "value": "200", "unit": "percent" },
    { "key": "week_start_day", "value": "monday" },
    { "key": "weekend_days", "value": "[\"saturday\",\"sunday\"]" }
  ]
}
```

### 5.3 核心计算逻辑

#### 日加班计算

```java
/**
 * 计算日加班时长
 * @param record 考勤记录
 * @param scheme 加班方案
 * @return 加班统计结果
 */
public OvertimeDailySummary calculateDailyOvertime(TaskRecord record, OvertimeScheme scheme) {
    // 1. 解析当日工时
    int totalWorkMinutes = parseWorkMinutes(record.getArrival(), record.getDeparture(), record.getPauseMinutes());
    
    // 2. 判定日期类型
    String dayType = classifyDay(record.getWorkDate(), scheme);
    
    // 3. 获取标准工时
    int dailyStandard = scheme.getRule("daily_standard_hours") * 60; // 转分钟
    
    // 4. 计算日加班
    String granularity = scheme.getRule("overtime_granularity");
    int dailyOT = 0;
    if (granularity.equals("daily") || granularity.equals("both")) {
        dailyOT = Math.max(0, totalWorkMinutes - dailyStandard);
    }
    
    // 5. 日上限检查
    int maxDailyOT = scheme.getRule("daily_max_overtime_minutes");
    if (maxDailyOT > 0 && dailyOT > maxDailyOT) {
        dailyOT = maxDailyOT; // 超限截断，但仍需告警
    }
    
    // 6. 按日期类型分流
    int overtimeWeekday = 0, overtimeWeekend = 0, overtimeHoliday = 0;
    switch (dayType) {
        case "weekend":  overtimeWeekend = dailyOT; break;
        case "holiday":  overtimeHoliday = dailyOT; break;
        default:         overtimeWeekday = dailyOT; break;
    }
    
    // 7. 构建统计结果
    OvertimeDailySummary summary = new OvertimeDailySummary();
    summary.setEmpNo(record.getEmpNo());
    summary.setCountryCode(record.getCountry());
    summary.setWorkDate(record.getWorkDate());
    summary.setTotalWorkMinutes(totalWorkMinutes);
    summary.setStandardWorkMinutes(dailyStandard);
    summary.setOvertimeMinutes(dailyOT);
    summary.setOvertimeWeekdayMinutes(overtimeWeekday);
    summary.setOvertimeWeekendMinutes(overtimeWeekend);
    summary.setOvertimeHolidayMinutes(overtimeHoliday);
    summary.setDayType(dayType);
    summary.setSchemeKey(scheme.getSchemeKey());
    
    return summary;
}
```

#### 周加班聚合

```java
/**
 * 计算周加班时长（法国分段费率逻辑）
 * @param empNo 工号
 * @param workDate 工作日期
 * @param scheme 加班方案
 */
public void recalculateWeeklyOvertime(String empNo, LocalDate workDate, OvertimeScheme scheme) {
    // 1. 获取本周起止日期
    LocalDate weekStart = getWeekStart(workDate, scheme.getRule("week_start_day"));
    LocalDate weekEnd = weekStart.plusDays(6);
    
    // 2. 获取周标准工时
    int weeklyStandard = scheme.getRule("weekly_standard_hours") * 60;
    
    // 3. 查询本周总工时
    int weeklyTotal = overtimeSummaryMapper.sumTotalWorkMinutes(empNo, weekStart, weekEnd);
    
    // 4. 计算周加班
    int weeklyOT = Math.max(0, weeklyTotal - weeklyStandard);
    
    // 5. 法国分段费率：36-43h=125%, >44h=150%
    int tier1Threshold = 43 * 60; // 分钟
    int tier2Threshold = scheme.getRule("rate_weekday_tier2_threshold") * 60;
    
    int tier1OT = 0, tier2OT = 0;
    if (weeklyTotal > tier2Threshold) {
        tier2OT = weeklyTotal - tier2Threshold;
        tier1OT = weeklyTotal - weeklyStandard - tier2OT;
    } else if (weeklyTotal > weeklyStandard) {
        tier1OT = weeklyTotal - weeklyStandard;
    }
    
    // 6. 按比例分摊到各天（加权分配）
    distributeToDays(empNo, weekStart, weekEnd, tier1OT, tier2OT);
}
```

### 5.4 企业自定义费率支持

系统支持两种费率配置模式：

1. **法规默认配置**：按国家法规预设费率，不可修改
2. **企业自定义配置**：在法规默认基础上，企业可调整费率（不能低于法规最低要求）

```java
/**
 * 获取生效的加班方案（支持企业自定义覆盖）
 * @param countryCode 国家代码
 * @return 加班方案
 */
public OvertimeScheme getEffectiveScheme(String countryCode) {
    // 1. 查询国家绑定的企业自定义方案
    OvertimeCountryBind bind = countryBindMapper.selectByCountryCode(countryCode);
    
    // 2. 如果有企业自定义方案，使用它
    if (bind != null && bind.getSchemeId() != null) {
        return schemeMapper.selectById(bind.getSchemeId());
    }
    
    // 3. 否则使用法规默认方案
    return schemeMapper.selectByCountryCode(countryCode);
}
```

---

## 六、API接口设计

### 6.1 加班方案管理 API

所有接口挂在 `/api/clockai/v1/overtime` 下：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/overtime/schemes` | 获取所有加班方案列表 |
| `POST` | `/v1/overtime/schemes` | 创建加班方案 |
| `PUT` | `/v1/overtime/schemes/{id}` | 更新加班方案 |
| `DELETE` | `/v1/overtime/schemes/{id}` | 删除加班方案 |
| `GET` | `/v1/overtime/schemes/{id}/rules` | 获取方案的规则明细 |
| `PUT` | `/v1/overtime/schemes/{id}/rules` | 批量更新规则明细 |
| `POST` | `/v1/overtime/schemes/{id}/clone` | 克隆方案（快速新建） |

### 6.2 国家绑定 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/overtime/country-binds` | 获取所有国家绑定 |
| `PUT` | `/v1/overtime/country-binds` | 更新国家绑定（批量） |
| `GET` | `/v1/overtime/country-binds/{countryCode}` | 获取某国绑定方案 |
| `GET` | `/v1/overtime/effective/{countryCode}` | 获取某国生效规则（含默认回退） |

### 6.3 加班统计 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/v1/overtime/calculate` | 手动触发加班计算（指定日期范围） |
| `GET` | `/v1/overtime/summary` | 加班汇总（按员工/周/月/国家） |
| `GET` | `/v1/overtime/summary/export` | 导出加班报表 |
| `GET` | `/v1/overtime/employee/{empNo}` | 员工加班明细 |
| `GET` | `/v1/overtime/alerts` | 加班告警（超限、接近上限） |

### 6.4 响应示例

```json
// GET /v1/overtime/effective/FR
{
  "countryCode": "FR",
  "schemeKey": "FR_STANDARD",
  "schemeName": "法国标准加班方案",
  "isDefault": false,
  "rules": {
    "daily_standard_hours": 7,
    "weekly_standard_hours": 35,
    "overtime_granularity": "both",
    "annual_max_overtime_hours": 220,
    "rate_weekday": 1.25,
    "rate_weekday_tier2": 1.50,
    "rate_weekday_tier2_threshold": 44,
    "rate_weekend": 1.50,
    "rate_holiday": 2.00,
    "week_start_day": "monday",
    "weekend_days": ["saturday", "sunday"]
  }
}

// GET /v1/overtime/summary?country=FR&startDate=2026-09-01&endDate=2026-09-30
{
  "country": "FR",
  "period": "2026-09-01 ~ 2026-09-30",
  "totalEmployees": 156,
  "totalOvertimeHours": 847.5,
  "totalOvertimeCost": 12712.50,
  "byAgency": [
    {
      "agencyKey": "MANPOWER",
      "warehouseKey": "LIL-01",
      "employeeCount": 42,
      "totalOvertimeHours": 215.0,
      "weekdayOT": 180.5,
      "weekendOT": 34.5,
      "estimatedCost": 3225.00
    }
  ],
  "byEmployee": [
    {
      "empNo": "FR00042",
      "empName": "Dupont Jean",
      "totalOTMinutes": 480,
      "weekdayOTMinutes": 360,
      "weekendOTMinutes": 120,
      "annualOTUsedHours": 42,
      "annualOTRemainingHours": 178,
      "alertLevel": "normal"
    }
  ]
}
```

---

## 七、前端界面设计

### 7.1 新增页面路由

| 路由 | 组件 | 权限 | 说明 |
|------|------|------|------|
| `/settings/overtime` | `OvertimeSchemeConfig.vue` | `overtimeConfig` | 加班方案配置（设置页） |
| `/overtime` | `OvertimeDashboard.vue` | `overtimeView` | 加班统计看板（主菜单） |

### 7.2 加班方案配置页面（OvertimeSchemeConfig.vue）

**页面结构**：

```
┌─────────────────────────────────────────────────────────┐
│ ⚙️ 加班方案配置                            [新建方案 ▼]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ┌─ 国家方案绑定 ──────────────────────────────────────┐ │
│ │  🇩🇪 德国  → [DE_STANDARD ▼]  ✅ 已配置             │ │
│ │  🇫🇷 法国  → [FR_STANDARD ▼]  ✅ 已配置             │ │
│ │  🇬🇧 英国  → [GB_STANDARD ▼]  ✅ 已配置             │ │
│ │  🇳🇱 荷兰  → [继承默认 ▼]     ⚠️ 使用默认           │ │
│ │  🇪🇸 西班牙 → [ES_STANDARD ▼]  ✅ 已配置             │ │
│ │  🌐 默认  → [DEFAULT_PLAN ▼]                        │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─ 方案详情：法国标准加班方案 ────────────────────────┐ │
│ │                                                     │ │
│ │ 📋 基础规则                                         │ │
│ │  每日标准工时    [7] 小时                            │ │
│ │  每周标准工时    [35] 小时                           │ │
│ │  加班计算粒度    [按日+按周 ▼]                       │ │
│ │  年度加班上限    [220] 小时                          │ │
│ │                                                     │ │
│ │ 💰 加班费率                                         │ │
│ │  ┌──────────────┬────────┬──────────┐              │ │
│ │  │ 类型          │ 倍率    │ 阈值      │              │ │
│ │  ├──────────────┼────────┼──────────┤              │ │
│ │  │ 平日加班(一级) │ 125%   │ -        │              │ │
│ │  │ 平日加班(二级) │ 150%   │ >44h/周   │              │ │
│ │  │ 周末加班      │ 150%   │ -        │              │ │
│ │  │ 节假日加班    │ 200%   │ -        │              │ │
│ │  │ 夜班额外      │ +25%   │ 20:00-6:00│              │ │
│ │  └──────────────┴────────┴──────────┘              │ │
│ │                                                     │ │
│ │ 📅 周规则                                           │ │
│ │  一周起始日      [周一 ▼]                            │ │
│ │  周末日          [☑周六 ☑周日]                       │ │
│ │                                                     │ │
│ │ [保存] [重置] [克隆为新方案]                          │ │
│ └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 7.3 加班统计看板页面（OvertimeDashboard.vue）

**页面结构**：

```
┌─────────────────────────────────────────────────────────┐
│ 📊 加班统计看板                [导出] [刷新] [手动重算]   │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ 🔍 筛选栏                                               │
│  周: [◀ 上周] 2026-09-07 ~ 2026-09-13 [下周 ▶]         │
│  国家: [全选 ▼]  仓库: [全选 ▼]  中介: [全选 ▼]         │
│                                                         │
│ ┌─ KPI 卡片 ─────────────────────────────────────────┐ │
│ │  总加班时长    加班人数     加班率      年度告警       │ │
│ │  847.5 h      89 人       57.1%      ⚠️ 3人接近上限  │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─ 加班趋势图（按周折线图）───────────────────────────┐ │
│ │  ╱╲                                                  │ │
│ │ ╱  ╲   ╱╲                                           │ │
│ │╱    ╲_╱  ╲_____                                      │ │
│ │  W35  W36  W37  W38  W39                             │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─ 明细表格 ─────────────────────────────────────────┐ │
│ │ 工号    │ 姓名     │ 国家 │ 平日OT │ 周末OT │ 合计    │ │
│ │─────────┼──────────┼──────┼────────┼────────┼────────│ │
│ │ FR00042 │ Dupont   │ 🇫🇷  │ 6.0h   │ 2.0h   │ 8.0h   │ │
│ │ DE00015 │ Müller   │ 🇩🇪  │ 4.5h   │ 0h     │ 4.5h   │ │
│ │ ...     │          │      │        │        │        │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                         │
│ ┌─ 国家维度汇总 ─────────────────────────────────────┐ │
│ │ 国家    │ 人数 │ 总OT(h) │ 平日OT │ 周末OT │ 节假日OT │ │
│ │─────────┼──────┼─────────┼────────┼────────┼──────────│ │
│ │ 🇫🇷 法国 │ 42   │ 215.0   │ 180.5  │ 34.5   │ 0        │ │
│ │ 🇩🇪 德国 │ 38   │ 198.0   │ 168.0  │ 30.0   │ 0        │ │
│ │ 🇪🇸 西班牙│ 35   │ 172.5   │ 142.5  │ 30.0   │ 0        │ │
│ └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 7.4 与现有页面的集成

#### 7.4.1 中介账单页面集成（AgencyBilling.vue）

在现有账单详情的"每周出勤表"中，**新增加班工时列**：

```
现有：
员工 | 周一 | 周二 | ... | 周日 | 总工时
                              | 42.5h

新增：
员工 | 周一 | 周二 | ... | 周日 | 总工时 | 标准工时 | 加班工时 | 加班费
                              | 42.5h  | 35.0h   | 7.5h     | €93.75
```

#### 7.4.2 考勤记录页面集成（TaskRecords.vue）

在现有记录表格中，**新增列**：

| 新增列 | 说明 |
|--------|------|
| 日标准工时 | 该国当日标准工时（如FR=7h） |
| 加班时长 | 当日加班分钟数 |
| 加班类型 | 平日/周末/节假日 |
| 日类型标签 | 彩色标签：🟢工作日 🟡周末 🔴节假日 |

---

## 八、与现有系统的集成方案

### 8.1 数据流集成

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  AI 识别      │     │  用户确认     │     │  确认完成     │
│  (现有)       │────▶│  (现有)       │────▶│  触发回调     │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                  │
                                                  ▼
                                        ┌──────────────────┐
                                        │ OvertimeEngine    │
                                        │ (新增)            │
                                        │                   │
                                        │ 1. 查询国家方案   │
                                        │ 2. 计算日加班     │
                                        │ 3. 判定日期类型   │
                                        │ 4. 写入summary表  │
                                        │ 5. 触发周聚合     │
                                        └──────────────────┘
                                                  │
                                                  ▼
                                        ┌──────────────────┐
                                        │ overtime_daily    │
                                        │ _summary          │
                                        └──────────────────┘
```

### 8.2 触发时机

| 触发点 | 说明 |
|--------|------|
| **任务确认时** | `tasks.status` 变为 `confirmed` 时，异步触发该任务所有行的加班计算 |
| **记录修改时** | `TaskEdit.vue` 中修改 arrival/departure/pause 后，重新计算该行加班 |
| **手动重算** | 管理员在加班看板点击"手动重算"，支持指定日期范围和国家 |
| **定时任务** | 每日凌晨扫描前一天未计算的记录（防漏兜底） |
| **方案变更时** | 修改加班规则后，可选"重算受影响日期范围" |

### 8.3 与夜班规则的关系

加班规则引擎与现有夜班标记系统**互补但独立**：

```
加班计算：
  输入: arrival, departure, pause_minutes, country
  输出: overtime_minutes (按类型分类)

夜班标记：
  输入: arrival, departure, shift
  输出: isNightShift (boolean)

交叉：
  如果 isNightShift && overtime_minutes > 0:
    → overtime_night_minutes = overtime_minutes
    → 可叠加夜班额外费率
```

---

## 九、实施路线图

### Phase 1：基础规则引擎（MVP，3周）

**目标**：支持按国家配置日加班标准，计算日加班时长

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 数据库建表 | 0.5d | 5张新表 + 迁移脚本 |
| 后端：规则配置CRUD | 3d | scheme + rule + bind 管理 |
| 后端：日加班计算引擎 | 3d | 核心计算逻辑 |
| 后端：计算触发集成 | 1.5d | 任务确认回调 + 记录修改回调 |
| 前端：方案配置页面 | 3d | 复用 NightShiftSettingsCard 模式 |
| 前端：TaskRecords 加班列 | 1.5d | 新增加班时长/类型列 |
| 测试 | 2.5d | 单元测试 + 集成测试 |

**交付物**：
- 按国家配置日标准工时
- 任务确认后自动计算日加班
- 考勤记录表展示加班时长
- 支持 FR/DE/GB/NL/ES 5国基础规则

### Phase 2：周加班 + 费率（2周）

**目标**：支持周加班聚合、多级费率

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 后端：周聚合引擎 | 3d | 法国分段费率逻辑 |
| 后端：费率计算 | 2d | 日类型判定 + 费率计算 |
| 前端：费率配置UI | 2d | 费率表格编辑 |
| 前端：中介账单集成 | 2d | 加班工时/加班费列 |
| 测试 | 1d | |

**交付物**：
- 法国35h周标准 + 125%/150%分段费率
- 中介账单新增加班工时和加班费
- 周末/节假日加班分类统计

### Phase 3：统计看板 + 年度管控（2周）

**目标**：完整加班统计看板、年度上限告警

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 后端：统计汇总API | 3d | 多维度聚合查询 |
| 后端：导出功能 | 2d | Excel导出 |
| 后端：告警引擎 | 1.5d | 年度上限预警 |
| 前端：加班统计看板 | 4d | KPI卡片+趋势图+明细表 |
| 前端：员工加班详情 | 2d | 员工维度加班历史 |
| 测试 | 1.5d | |

**交付物**：
- 加班统计看板（KPI、趋势图、明细表）
- Excel导出功能
- 法国220h年度上限预警
- 西班牙80h年度上限预警

### Phase 4：高级功能（可选，2周）

| 任务 | 说明 |
|------|------|
| 德国6个月滚动平均 | 计算6个月滚动平均工时，动态调整加班判定 |
| 节假日日历配置 | 按国家配置法定节假日，自动标记节假日加班 |
| 集体协议适配 | 支持不同集体协议(CBA)的加班费率差异 |
| 加班审批流程 | 员工提交加班申请 → 主管审批 → 系统确认 |

---

## 十、数据库迁移脚本

```sql
-- 迁移编号：029_overtime_management.sql

-- 1. 加班方案主表
CREATE TABLE IF NOT EXISTS overtime_scheme (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scheme_key VARCHAR(32) NOT NULL COMMENT '方案标识',
    scheme_name VARCHAR(128) NOT NULL COMMENT '方案名称',
    country_code VARCHAR(16) NOT NULL COMMENT '适用国家代码',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    description VARCHAR(512) NULL COMMENT '方案说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scheme_key (scheme_key),
    INDEX idx_country (country_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班方案主表';

-- 2. 加班规则明细表
CREATE TABLE IF NOT EXISTS overtime_rule (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scheme_id BIGINT NOT NULL COMMENT '关联 overtime_scheme.id',
    rule_type VARCHAR(32) NOT NULL COMMENT '规则类型',
    rule_key VARCHAR(64) NOT NULL COMMENT '规则键',
    rule_value VARCHAR(255) NOT NULL COMMENT '规则值',
    rule_unit VARCHAR(16) NULL COMMENT '单位',
    description VARCHAR(256) NULL COMMENT '规则说明',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_scheme_rule (scheme_id, rule_key),
    INDEX idx_scheme (scheme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班规则明细';

-- 3. 国家-方案绑定表
CREATE TABLE IF NOT EXISTS overtime_country_bind (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(16) NOT NULL COMMENT '国家代码',
    scheme_id BIGINT NOT NULL COMMENT '加班方案ID',
    effective_from DATE NULL COMMENT '生效日期',
    effective_to DATE NULL COMMENT '失效日期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_country_bind (country_code),
    INDEX idx_scheme (scheme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='国家加班方案绑定';

-- 4. 加班日统计表
CREATE TABLE IF NOT EXISTS overtime_daily_summary (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    emp_no VARCHAR(64) NOT NULL COMMENT '工号',
    country_code VARCHAR(16) NOT NULL COMMENT '国家',
    work_date DATE NOT NULL COMMENT '工作日期',
    total_work_minutes INT NOT NULL DEFAULT 0 COMMENT '当日总工时(分钟)',
    standard_work_minutes INT NOT NULL DEFAULT 0 COMMENT '当日标准工时(分钟)',
    overtime_minutes INT NOT NULL DEFAULT 0 COMMENT '加班总时长(分钟)',
    overtime_weekday_minutes INT NOT NULL DEFAULT 0 COMMENT '平日加班分钟',
    overtime_weekend_minutes INT NOT NULL DEFAULT 0 COMMENT '周末加班分钟',
    overtime_holiday_minutes INT NOT NULL DEFAULT 0 COMMENT '节假日加班分钟',
    overtime_night_minutes INT NOT NULL DEFAULT 0 COMMENT '夜班加班分钟',
    day_type VARCHAR(16) NOT NULL DEFAULT 'weekday' COMMENT '日期类型',
    scheme_key VARCHAR(32) NULL COMMENT '使用的加班方案',
    source_task_ids TEXT NULL COMMENT '来源任务ID列表(JSON)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_date (emp_no, work_date),
    INDEX idx_country_date (country_code, work_date),
    INDEX idx_emp_month (emp_no, country_code, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加班日统计';

-- 5. 法定节假日表
CREATE TABLE IF NOT EXISTS public_holiday (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(16) NOT NULL COMMENT '国家代码',
    holiday_date DATE NOT NULL COMMENT '节假日日期',
    holiday_name VARCHAR(128) NOT NULL COMMENT '节假日名称',
    is_recurring TINYINT NOT NULL DEFAULT 0 COMMENT '是否每年重复',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_country_date (country_code, holiday_date),
    INDEX idx_country_year (country_code, holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法定节假日';

-- 6. 插入默认方案
INSERT INTO overtime_scheme (scheme_key, scheme_name, country_code, description) VALUES
('DEFAULT', '默认方案', 'default', '系统默认加班方案，8h/天标准'),
('FR_STANDARD', '法国标准方案', 'FR', '法国35h/周标准，125%/150%分段费率'),
('DE_STANDARD', '德国标准方案', 'DE', '德国8h/天标准，6个月滚动平均'),
('GB_STANDARD', '英国标准方案', 'GB', '英国48h/周上限，合同约定费率'),
('NL_STANDARD', '荷兰标准方案', 'NL', '荷兰集体协议标准'),
('ES_STANDARD', '西班牙标准方案', 'ES', '西班牙40h/周，年度80h上限');

-- 7. 插入默认规则（法国为例）
INSERT INTO overtime_rule (scheme_id, rule_type, rule_key, rule_value, rule_unit, description) VALUES
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'basic', 'daily_standard_hours', '7', 'hour', '每日标准工时'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'basic', 'weekly_standard_hours', '35', 'hour', '每周标准工时'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'basic', 'overtime_granularity', 'both', NULL, '按日+按周计算'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'basic', 'annual_max_overtime_hours', '220', 'hour', '年度加班上限'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'rate', 'rate_weekday', '125', 'percent', '平日加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'rate', 'rate_weekday_tier2', '150', 'percent', '平日加班二级倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'rate', 'rate_weekday_tier2_threshold', '44', 'hour', '二级倍率起算周工时'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'rate', 'rate_weekend', '150', 'percent', '周末加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'rate', 'rate_holiday', '200', 'percent', '节假日加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'week', 'week_start_day', 'monday', NULL, '一周起始日'),
((SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD'), 'week', 'weekend_days', '["saturday","sunday"]', NULL, '周末日');

-- 德国规则
INSERT INTO overtime_rule (scheme_id, rule_type, rule_key, rule_value, rule_unit, description) VALUES
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'basic', 'daily_standard_hours', '8', 'hour', '每日标准工时'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'basic', 'weekly_standard_hours', '48', 'hour', '每周标准工时(含加班)'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'basic', 'overtime_granularity', 'both', NULL, '按日+按周计算'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'basic', 'daily_max_overtime_minutes', '120', 'minute', '日加班上限(可延至10h)'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'basic', 'rolling_average_months', '6', NULL, '6个月滚动平均'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'rate', 'rate_weekday', '125', 'percent', '平日加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'rate', 'rate_weekend', '150', 'percent', '周末加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD'), 'rate', 'rate_holiday', '200', 'percent', '节假日加班倍率');

-- 西班牙规则
INSERT INTO overtime_rule (scheme_id, rule_type, rule_key, rule_value, rule_unit, description) VALUES
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'basic', 'daily_standard_hours', '8', 'hour', '每日标准工时'),
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'basic', 'weekly_standard_hours', '40', 'hour', '每周标准工时'),
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'basic', 'overtime_granularity', 'both', NULL, '按日+按周计算'),
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'basic', 'annual_max_overtime_hours', '80', 'hour', '年度加班上限'),
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'rate', 'rate_weekday', '125', 'percent', '平日加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'rate', 'rate_weekend', '175', 'percent', '周末加班倍率'),
((SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD'), 'rate', 'rate_holiday', '200', 'percent', '节假日加班倍率');

-- 8. 绑定默认方案到各国
INSERT INTO overtime_country_bind (country_code, scheme_id) VALUES
('FR', (SELECT id FROM overtime_scheme WHERE scheme_key='FR_STANDARD')),
('DE', (SELECT id FROM overtime_scheme WHERE scheme_key='DE_STANDARD')),
('GB', (SELECT id FROM overtime_scheme WHERE scheme_key='GB_STANDARD')),
('NL', (SELECT id FROM overtime_scheme WHERE scheme_key='NL_STANDARD')),
('ES', (SELECT id FROM overtime_scheme WHERE scheme_key='ES_STANDARD')),
('default', (SELECT id FROM overtime_scheme WHERE scheme_key='DEFAULT'));
```

---

## 十一、关键设计决策与风险提示

### 11.1 关键决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 加班计算是实时还是异步 | **异步** | 确认时触发，不阻塞用户操作；记录修改时可增量更新 |
| 加班统计存独立表还是字段扩展 | **独立表** | 不侵入现有 task_records 结构，可独立演进 |
| 规则引擎在后端还是前端 | **后端** | 规则引擎涉及复杂计算，前端只做展示和配置 |
| 费率计算结果存储还是实时算 | **存储** | 便于报表查询，方案变更时批量重算 |

### 11.2 风险提示

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **德国6个月滚动平均**复杂度高 | 计算性能、数据量 | Phase 4 实现，MVP先用日标准简化 |
| **各国法规变更频繁** | 配置维护成本 | 规则全配置化，无需改代码；提供"方案版本"功能 |
| **考勤数据不完整**（缺到离时间） | 加班无法计算 | 与现有异常检测一致，标记为"数据不足" |
| **节假日数据** | 各国节假日不同 | 预置各国节假日日历表，管理员可手动维护 |
| **历史数据重算** | 方案变更时需重算 | 提供批量重算工具，支持按日期范围和国家 |

---

## 十二、财务影响分析

**假设**：一个仓库50名临时工，平均每周加班3小时/人

| 项目 | 计算 | 结果 |
|------|------|------|
| 月加班总工时 | 50人 × 3h/周 × 4周 | 600h/月 |
| 平均时薪（含社保） | €15/h | - |
| 月加班费（125%） | 600h × €15 × 0.25 | €2,250/月 |
| 年加班费 | €2,250 × 12 | **€27,000/年** |

**系统价值**：通过准确计算加班、避免多算/漏算，每个仓库年均可节省5-10%的加班费支出（€1,350-€2,700），同时确保劳动法合规，避免高额罚款。

---

## 十三、总结

本方案的核心设计理念是**"规则配置化，计算引擎化，数据独立化"**：

1. **规则配置化**：通过 `overtime_scheme` + `overtime_rule` 的组合，支持任意国家的加班规则配置，新增国家零代码
2. **计算引擎化**：`OvertimeEngine` 作为独立服务，在任务确认时异步触发，与现有系统松耦合
3. **数据独立化**：`overtime_daily_summary` 独立存储加班统计结果，不侵入现有 `task_records` 表
4. **费率灵活化**：支持法规默认配置和企业自定义费率，满足不同企业需求

**核心功能**：
- ✅ 按日+按周计算加班
- ✅ 支持法规默认配置和企业自定义费率
- ✅ 年度加班上限管控和告警
- ✅ 多维度加班统计报表
- ✅ 与现有系统无缝集成

---

## 附录：文档修订历史

| 版本 | 日期 | 修订内容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-09-09 | 初始版本，完成方案设计 | AI Assistant |
