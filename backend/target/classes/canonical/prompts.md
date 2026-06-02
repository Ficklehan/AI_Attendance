# AI考勤识别提示词

## 主要识别提示词

```markdown
识别考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
7. 日期统一转YYYY-MM-DD：17/05/2026→2026-05-17,17-05-2026→2026-05-17,17-05-26→2026-05-17
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","Netherlands","AMS","2026-05-17","张三","中介A","MATIN","08:00","18:00","60","员工签名","备注","正常",false]
["2","France","PAR","2026-05-17","李四","中介B","NUIT","22:00","06:00","60","SIGNATURE","Observations","正常;夜班",false]
["3","Netherlands","AMS","2026-05-17","王五","中介C","MATIN","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","中介D","SOIR","???","???","30","","","模糊;未出勤",false]
```
## 继续输出提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```
## 法国 (FR) - 识别提示词

```markdown
识别法国考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
7. 日期统一转YYYY-MM-DD：17/05/2026→2026-05-17,17-05-2026→2026-05-17,17-05-26→2026-05-17
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","Netherlands","AMS","2026-05-17","张三","中介A","MATIN","08:00","18:00","60","员工签名","备注","正常",false]
["2","France","PAR","2026-05-17","李四","中介B","NUIT","22:00","06:00","60","SIGNATURE","Observations","正常;夜班",false]
["3","Netherlands","AMS","2026-05-17","王五","中介C","MATIN","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","中介D","SOIR","???","???","30","","","模糊;未出勤",false]
```

## 法国 (FR) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```

## 中国 (CN) - 识别提示词

```markdown
识别中国考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
7. 日期统一转YYYY-MM-DD：2026-05-17
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","中国","上海仓","2026-05-17","张三","中介A","上午","08:00","18:00","60","员工签名","备注","正常",false]
["2","中国","上海仓","2026-05-17","李四","中介B","夜班","22:00","06:00","60","","","正常;夜班",false]
["3","中国","上海仓","2026-05-17","王五","中介C","上午","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","中介D","下午","???","???","30","","","模糊;未出勤",false]
```

## 中国 (CN) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```

## 德国 (DE) - 识别提示词

```markdown
识别德国考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：德国时间格式
7. 日期统一转YYYY-MM-DD：德国常用DD.MM.YYYY格式
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","Germany","BER","2026-05-17","张三","中介A","Frühschicht","08:00","18:00","60","","","正常",false]
["2","Germany","BER","2026-05-17","李四","中介B","Nachtschicht","22:00","06:00","60","","","正常;夜班",false]
["3","Germany","BER","2026-05-17","王五","中介C","Frühschicht","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","中介D","Spätschicht","???","???","30","","","模糊;未出勤",false]
```

## 德国 (DE) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```

## 美国 (US) - 识别提示词

```markdown
识别美国考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：美国常用12小时制，AM/PM后缀
7. 日期统一转YYYY-MM-DD：美国常用MM/DD/YYYY格式
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","USA","NYC","2026-05-17","John Smith","Agency A","Day Shift","08:00 AM","06:00 PM","60","","","正常",false]
["2","USA","NYC","2026-05-17","Jane Doe","Agency B","Night Shift","10:00 PM","06:00 AM","60","","","正常;夜班",false]
["3","USA","NYC","2026-05-17","Bob Wilson","Agency C","Day Shift","08:30 AM","05:30 PM","60","","","手写",false]
["4","","","2026-05-17","???","Agency D","Evening Shift","???","???","30","","","模糊;未出勤",false]
```

## 美国 (US) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```

## 荷兰 (NL) - 识别提示词

```markdown
识别荷兰考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
7. 日期统一转YYYY-MM-DD：17/05/2026→2026-05-17,17-05-2026→2026-05-17,17-05-26→2026-05-17
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","Netherlands","AMS","2026-05-17","Jan de Vries","Agency A","OCHTEND","08:00","18:00","60","","","正常",false]
["2","Netherlands","AMS","2026-05-17","Anna Bakker","Agency B","NACHT","22:00","06:00","60","","","正常;夜班",false]
["3","Netherlands","AMS","2026-05-17","Pieter Jansen","Agency C","OCHTEND","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","Agency D","AVOND","???","???","30","","","模糊;未出勤",false]
```

## 荷兰 (NL) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```

## 意大利 (IT) - 识别提示词

```markdown
识别意大利考勤表格，表头可能为中文、法语、荷兰语或意大利语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
7. 日期统一转YYYY-MM-DD：17/05/2026→2026-05-17,17-05-2026→2026-05-17,17-05-26→2026-05-17
8. 表头对应关系：国家/Pays/Country/Paese→Pays；仓库/Entrepôt/Warehouse/Magazzino→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Osservazioni→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","Italy","MIL","2026-05-17","Marco Rossi","Agenzia A","MATTINA","08:00","18:00","60","","","正常",false]
["2","Italy","MIL","2026-05-17","Giulia Bianchi","Agenzia B","NOTTE","22:00","06:00","60","","","正常;夜班",false]
["3","Italy","MIL","2026-05-17","Luca Verdi","Agenzia C","MATTINA","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","Agenzia D","SERA","???","???","30","","","模糊;未出勤",false]
```

## 意大利 (IT) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```

## 西班牙 (ES) - 识别提示词

```markdown
识别西班牙考勤表格，表头可能为中文、法语、荷兰语、意大利语或西班牙语，但字段顺序一致。逐行返回单个JSON数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。

规则：
1. 只返回真实数据，禁止编造
2. 标记列：手写/模糊/正常；夜班（20:00后到或06:00前走，跨午夜）；未出勤（到达离开都空或???）
3. 必须观察工号（NO）和姓名两列的视觉笔迹：只要任一单元格是手写，第13个字段标记必须包含"手写"，不得输出"正常"；其他列的手写不影响标记
4. 标记用;分隔，如"手写;夜班"，只有工号和姓名都非手写且非模糊/未出勤时才允许输出"正常"
5. 删除线=true否则=false
6. 时间统一转HH:MM（24h）：6h→06:00,6h30→06:30,6.30→06:30,630→06:30,6→06:00,18h30→18:30
7. 日期统一转YYYY-MM-DD：17/05/2026→2026-05-17,17-05-2026→2026-05-17,17-05-26→2026-05-17
8. 表头对应关系：国家/Pays/Country/País→Pays；仓库/Entrepôt/Warehouse/Almacén→Entrepot；员工签名/SIGNATURE/Signature/Firma→SIGNATURE；备注/Observations/Remarks/Observaciones→Observations
9. 休息字段只输出分钟数值，不带单位：30min、30mn、0h30、00:30都输出30
10. 每行单独数组，不要包大数组

示例：
["1","Spain","MAD","2026-05-17","Carlos García","Agencia A","MAÑANA","08:00","18:00","60","","","正常",false]
["2","Spain","MAD","2026-05-17","María López","Agencia B","NOCHE","22:00","06:00","60","","","正常;夜班",false]
["3","Spain","MAD","2026-05-17","Juan Pérez","Agencia C","MAÑANA","08:30","17:30","60","","","手写",false]
["4","","","2026-05-17","???","Agencia D","TARDE","???","???","30","","","模糊;未出勤",false]
```

## 西班牙 (ES) - 继续提示词

```markdown
请接续上文继续输出，不要重复已有内容，保持相同格式。
```
