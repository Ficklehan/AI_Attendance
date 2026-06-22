# AI考勤识别提示词

## 主要识别提示词

```markdown
识别考勤表格(表头中/法/荷/意/西等)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE_INTERIMAIRE=供应商/中介(Agence/Agency/供应商/Interim)；第7位HORAIRES_DU_TRAVAIL=班次/工时(Horaires/Heures/Shift/MATIN/SOIR/时段如14:30-21:00)；二者严禁互换；HORAIRES禁填公司名；供应商分组标题(MANPOWER/STARTPEOPLE/JOB&TALENT等)→仅写AGENCE

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/6.30/630→06:30，18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26→2026-05-17
· 表头→Pays/Country/Paese；Entrepôt/Warehouse/Magazzino；NOM/Name→NOM_PRENOM；Agence/Agency/供应商→第6位AGENCE；Horaires/Heures/班次→第7位HORAIRES；含员工签名/SIGNATURE/Signature/Firma/Signatura/签名关键词列(可有说明文字，非Firma e conferma主管栏)→SIGNATURE；Observations/Remarks/Osservazioni
· PAUSE仅分钟整数；Entrepot仅读图，无/看不清→""，禁按国家猜AMS/PAR

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`连接)
· 夜班由系统自动计算，勿写入标记列；未出勤：到离皆空或???
· 仅NO+姓名均非手写且非模糊/未出勤可「正常」；NO或姓名任一手写必含「手写」(它列手写不计)

【其他】已删除：删线=true否则false；PAGE_NUM：页眉/页脚/底边页码(1,Page 1,1/5,P.1,Pagina 1/5等)，有总页写当前/总，同页相同，无→""

示例(勿照抄)：
["1","Netherlands","AMS","2026-05-17","张三","中介A","MATIN","08:00","18:00","60","Dupont","备注","正常",false,""]
["4","","","2026-05-17","???","中介D","SOIR","???","???","30","","","模糊;未出勤",false,""]
```

## 继续输出提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 法国 (FR) - 识别提示词

```markdown
识别法国考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介(Agence d'intérimaire/Agency)；第7位HORAIRES=班次(Horaires du travail/Heures/MATIN/SOIR/22:00-06:00等)；二者严禁互换；FR表常按供应商分块，区块标题(MANPOWER/STARTPEOPLE/STAFFMATCH/JOB&TALENT等)→写AGENCE，员工行班次在Horaires列

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/6.30/630→06:30，18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026、17-05-26→2026-05-17
· 表头→Pays/Country；Entrepôt/Warehouse；NOM/Name→NOM_PRENOM；Agence/Agency/供应商→第6位AGENCE；Horaires/Heures/班次→第7位HORAIRES；含签名关键词列→SIGNATURE(非Firma e conferma)；Observations/Remarks/Osservazioni
· PAUSE仅分钟整数；Entrepot仅读图，无/看不清→""，禁猜AMS/PAR

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：页眉页脚页码，同页相同，无→""

示例(勿照抄)：
["1","France","PAR","2026-05-17","张三","中介A","MATIN","08:00","18:00","60","Dupont","备注","正常",false,""]
["4","","","2026-05-17","???","中介D","SOIR","???","???","30","","","模糊;未出勤",false,""]
```

## 法国 (FR) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 中国 (CN) - 识别提示词

```markdown
识别中国考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介；第7位HORAIRES=班次/工时；先读表头禁按位置猜，二者严禁互换

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/6.30/630→06:30，18h30→18:30
· 日期→YYYY-MM-DD：2026-05-17等规范为YYYY-MM-DD
· 表头→Pays/Country；Entrepôt/Warehouse；含员工签名/SIGNATURE/Signature/Firma/签名关键词列→SIGNATURE；Observations/备注
· PAUSE仅分钟整数；Entrepot仅读图，无/看不清→""

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：页眉页脚页码，同页相同，无→""

示例(勿照抄)：
["1","中国","上海仓","2026-05-17","张三","中介A","上午","08:00","18:00","60","Dupont","备注","正常",false,""]
["4","","","2026-05-17","???","中介D","下午","???","???","30","","","模糊;未出勤",false,""]
```

## 中国 (CN) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 德国 (DE) - 识别提示词

```markdown
识别德国考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介；第7位HORAIRES=班次/工时；先读表头禁按位置猜，二者严禁互换

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/630→06:30等德国写法
· 日期→YYYY-MM-DD：DD.MM.YYYY(17.05.2026→2026-05-17)
· 表头→Pays；Entrepot；含签名关键词列→SIGNATURE(非Firma e conferma)；Observations
· PAUSE仅分钟整数；Entrepot仅读图禁猜

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：页眉页脚页码，同页相同，无→""

示例(勿照抄)：
["1","Germany","BER","2026-05-17","张三","中介A","Frühschicht","08:00","18:00","60","","","正常",false,""]
["4","","","2026-05-17","???","中介D","Spätschicht","???","???","30","","","模糊;未出勤",false,""]
```

## 德国 (DE) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 美国 (US) - 识别提示词

```markdown
识别美国考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介；第7位HORAIRES=班次/工时；先读表头禁按位置猜，二者严禁互换

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：可读12h制(AM/PM)，输出须24h(08:00 AM→08:00)
· 日期→YYYY-MM-DD：MM/DD/YYYY(05/17/2026→2026-05-17)
· 表头→Pays/Country；Entrepot/Warehouse；含签名关键词列→SIGNATURE；Observations/Remarks
· PAUSE仅分钟整数；Entrepot仅读图禁猜

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：Page 1,1/5等，同页相同，无→""

示例(勿照抄)：
["1","USA","NYC","2026-05-17","John Smith","Agency A","Day","08:00","18:00","60","","","正常",false,""]
["4","","","2026-05-17","???","Agency D","Evening","???","???","30","","","模糊;未出勤",false,""]
```

## 美国 (US) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 荷兰 (NL) - 识别提示词

```markdown
识别荷兰考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介；第7位HORAIRES=班次/工时；先读表头禁按位置猜，二者严禁互换

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/630→06:30，18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、17-05-2026→2026-05-17
· 表头→Pays；Entrepot；含签名关键词列→SIGNATURE；Observations
· PAUSE仅分钟整数；Entrepot仅读图禁猜

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：页眉页脚页码，同页相同，无→""

示例(勿照抄)：
["1","Netherlands","AMS","2026-05-17","Jan de Vries","Agency A","OCHTEND","08:00","18:00","60","","","正常",false,""]
["4","","","2026-05-17","???","Agency D","AVOND","???","???","30","","","模糊;未出勤",false,""]
```

## 荷兰 (NL) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 意大利 (IT) - 识别提示词

```markdown
识别意大利考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介；第7位HORAIRES=班次/工时；先读表头禁按位置猜，二者严禁互换

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/630→06:30，18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026、06/06/26→2026-06-06
· 表头→Pays/Paese；Magazzino；Cognome Nome→NOM_PRENOM；含Firma/Signatura关键词列→SIGNATURE(非Firma e conferma responsabile主管栏)；Osservazioni
· PAUSE仅分钟整数；Entrepot仅读图禁猜

【SIGNATURE·11】读Firma列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列
· FOGLIO PRESENZE：Firma列常见蓝笔手写，有可见笔迹必输出???或转写，禁""

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：Pagina 1/5等，同页相同，无→""

示例(勿照抄)：
["4","Italy","ROMA","2026-06-06","FRANKA DAVID","","12:00-21:00","12:00","","0","Sangare","","正常",false,""]
["10","","","2026-06-06","???","","","???","???","0","","","模糊;未出勤",false,""]
```

## 意大利 (IT) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```

## 西班牙 (ES) - 识别提示词

```markdown
识别西班牙考勤表格(表头多语言)。图片列顺序不固定，必须先读表头定位列；15字段JSON输出顺序固定。每行一个JSON数组：
[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除,PAGE_NUM]

【列定位】第6位AGENCE=供应商/中介；第7位HORAIRES=班次/工时；先读表头禁按位置猜，二者严禁互换

【数据】只输出真实行；看不清用???或""，禁猜测补全编造；勿把表头当数据；名/工号???或空→到离必空；每行单数组
【JSON格式·强制】每行仅一个独立JSON数组、恰好15个双引号字段；行末必须以]结束再换行；严禁把多列拼成一串(如50ITALIAMILANO...)；严禁行与行粘连(如false["53")；续写新行必须以[开头
· 时间→HH:MM(24h)：6h→06:00，6h30/630→06:30，18h30→18:30
· 日期→YYYY-MM-DD：17/05/2026→2026-05-17
· 表头→Pays/País；Entrepot/Almacén；含Firma/签名关键词列→SIGNATURE；Observations/Observaciones
· PAUSE仅分钟整数；Entrepot仅读图禁猜

【SIGNATURE·11】读员工签名列单元格笔迹：可辨→转写，有笔迹看不清→???，空白→""；禁表头字面量
· ???/模糊=已签字；""=未签字；签字横线划掉或整行删除线→isDeleted=true；勿写入标记列

【标记·13】手写|模糊|正常|未出勤(`;`)；夜班由系统计算勿写入；未出勤到离空；仅NO+姓名均非手写可正常，任一手写必含手写

【其他】已删除：删线=true；PAGE_NUM：页眉页脚页码，同页相同，无→""

示例(勿照抄)：
["1","Spain","MAD","2026-05-17","Carlos García","Agencia A","MAÑANA","08:00","18:00","60","","","正常",false,""]
["4","","","2026-05-17","???","Agencia D","TARDE","???","???","30","","","模糊;未出勤",false,""]
```

## 西班牙 (ES) - 继续提示词

```markdown
接续上文继续输出，格式与字段不变，不重复已输出行。
```
