# AI考勤识别提示词

## 主要识别提示词

```markdown
识别考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：6h→06:00；6h30/6.30/630→06:30；18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26 等均规范为 2026-05-17
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、- 1 -、Pagina 1/5 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","Netherlands","AMS","2026-05-17","张三","中介A","MATIN","08:00","18:00","60","员工签名","备注","正常",false,""]
["2","France","PAR","2026-05-17","李四","中介B","NUIT","22:00","06:00","60","SIGNATURE","Observations","正常;夜班",false,""]
["3","Netherlands","AMS","2026-05-17","王五","中介C","MATIN","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","中介D","SOIR","???","???","30","","","模糊;未出勤",false,""]
```

## 继续输出提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 法国 (FR) - 识别提示词

```markdown
识别法国考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：6h→06:00；6h30/6.30/630→06:30；18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26 等均规范为 2026-05-17
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、- 1 -、Pagina 1/5 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","Netherlands","AMS","2026-05-17","张三","中介A","MATIN","08:00","18:00","60","员工签名","备注","正常",false,""]
["2","France","PAR","2026-05-17","李四","中介B","NUIT","22:00","06:00","60","SIGNATURE","Observations","正常;夜班",false,""]
["3","Netherlands","AMS","2026-05-17","王五","中介C","MATIN","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","中介D","SOIR","???","???","30","","","模糊;未出勤",false,""]
```

## 法国 (FR) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 中国 (CN) - 识别提示词

```markdown
识别中国考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：6h→06:00；6h30/6.30/630→06:30；18h30→18:30
· 日期→YYYY-MM-DD：2026-05-17 等规范为 YYYY-MM-DD
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、- 1 - 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","中国","上海仓","2026-05-17","张三","中介A","上午","08:00","18:00","60","员工签名","备注","正常",false,""]
["2","中国","上海仓","2026-05-17","李四","中介B","夜班","22:00","06:00","60","","","正常;夜班",false,""]
["3","中国","上海仓","2026-05-17","王五","中介C","上午","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","中介D","下午","???","???","30","","","模糊;未出勤",false,""]
```

## 中国 (CN) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 德国 (DE) - 识别提示词

```markdown
识别德国考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：德国常见写法，按 6h→06:00、6h30/630→06:30 等规范
· 日期→YYYY-MM-DD：德国常用 DD.MM.YYYY(如 17.05.2026→2026-05-17)
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、- 1 - 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","Germany","BER","2026-05-17","张三","中介A","Frühschicht","08:00","18:00","60","","","正常",false,""]
["2","Germany","BER","2026-05-17","李四","中介B","Nachtschicht","22:00","06:00","60","","","正常;夜班",false,""]
["3","Germany","BER","2026-05-17","王五","中介C","Frühschicht","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","中介D","Spätschicht","???","???","30","","","模糊;未出勤",false,""]
```

## 德国 (DE) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 美国 (US) - 识别提示词

```markdown
识别美国考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：可读12小时制(含 AM/PM)，输出须24小时(如 08:00 AM→08:00)
· 日期→YYYY-MM-DD：美国常用 MM/DD/YYYY(如 05/17/2026→2026-05-17)
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、Page 1、Page 1 of 5、1/5、P.1 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","USA","NYC","2026-05-17","John Smith","Agency A","Day Shift","08:00 AM","06:00 PM","60","","","正常",false,""]
["2","USA","NYC","2026-05-17","Jane Doe","Agency B","Night Shift","10:00 PM","06:00 AM","60","","","正常;夜班",false,""]
["3","USA","NYC","2026-05-17","Bob Wilson","Agency C","Day Shift","08:30 AM","05:30 PM","60","","","手写",false,""]
["4","","","2026-05-17","???","Agency D","Evening Shift","???","???","30","","","模糊;未出勤",false,""]
```

## 美国 (US) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 荷兰 (NL) - 识别提示词

```markdown
识别荷兰考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：6h→06:00；6h30/6.30/630→06:30；18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26 等均规范为 2026-05-17
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、- 1 - 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","Netherlands","AMS","2026-05-17","Jan de Vries","Agency A","OCHTEND","08:00","18:00","60","","","正常",false,""]
["2","Netherlands","AMS","2026-05-17","Anna Bakker","Agency B","NACHT","22:00","06:00","60","","","正常;夜班",false,""]
["3","Netherlands","AMS","2026-05-17","Pieter Jansen","Agency C","OCHTEND","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","Agency D","AVOND","???","???","30","","","模糊;未出勤",false,""]
```

## 荷兰 (NL) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 意大利 (IT) - 识别提示词

```markdown
识别意大利考勤表格（表头可为中/法/荷/意等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：6h→06:00；6h30/6.30/630→06:30；18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26 等均规范为 2026-05-17
· 表头语义→字段：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、Pagina 1/5 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","Italy","MIL","2026-05-17","Marco Rossi","Agenzia A","MATTINA","08:00","18:00","60","","","正常",false,""]
["2","Italy","MIL","2026-05-17","Giulia Bianchi","Agenzia B","NOTTE","22:00","06:00","60","","","正常;夜班",false,""]
["3","Italy","MIL","2026-05-17","Luca Verdi","Agenzia C","MATTINA","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","Agenzia D","SERA","???","???","30","","","模糊;未出勤",false,""]
```

## 意大利 (IT) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 西班牙 (ES) - 识别提示词

```markdown
识别西班牙考勤表格（表头可为中/法/荷/意/西等，列顺序固定）。每行仅输出一个 JSON 数组，15 项：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【数据与格式】
· 只输出图中真实行；看不清用 ??? 或 ""；禁止编造、补全、连号演示(1,2,3…)、把表头当数据(NO/姓名/供应商/签名/备注等)
· 每行仅一个 JSON 数组，不要包大数组
· 时间→HH:MM(24h)：6h→06:00；6h30/6.30/630→06:30；18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26 等均规范为 2026-05-17
· 表头语义→字段：国家/Pays/Country/País→Pays；仓库/Entrepôt/Warehouse/Almacén→Entrepot；签名/SIGNATURE/Firma→SIGNATURE；备注/Observations/Remarks/Observaciones→Observations
· PAUSE 只输出分钟整数(去 min/mn/h 等单位)
· Entrepot 仅读图，无列或看不清则 ""，禁止按国家猜 AMS/PAR 等

【标记·第13项】取值：手写|模糊|正常|夜班|未出勤，`;` 连接。
· 夜班：到达≥20:00 或 离开≤06:00/跨午夜
· 未出勤：到、离皆空或 ???
· 仅当 NO 与姓名均非手写且非模糊/未出勤时可「正常」；NO 或姓名任一手写必含「手写」(其他列手写不计)
· 例：手写;夜班、模糊;未出勤

【其他字段】
· 已删除：行有删除线=true，否则 false
· PAGE_NUM(第15项)：读本页页眉/页脚/底边 Excel 页码(1、第1页、Page 1、1/5、P.1、Pagina 1/5 等)；有总页写 当前/总(如 3/10)；同页各行相同；无则 ""

示例（勿照抄，仅格式参考）：
["1","Spain","MAD","2026-05-17","Carlos García","Agencia A","MAÑANA","08:00","18:00","60","","","正常",false,""]
["2","Spain","MAD","2026-05-17","María López","Agencia B","NOCHE","22:00","06:00","60","","","正常;夜班",false,""]
["3","Spain","MAD","2026-05-17","Juan Pérez","Agencia C","MAÑANA","08:30","17:30","60","","","手写",false,""]
["4","","","2026-05-17","???","Agencia D","TARDE","???","???","30","","","模糊;未出勤",false,""]
```

## 西班牙 (ES) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```
