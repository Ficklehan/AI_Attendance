# 飞书多维表配置

## 全局默认配置

```yaml
bitable_app_token: 'MssZb4YXQaEeR5swLencoIcRnLc'
bitable_table_id: 'tbl5WHAQbm0f6JyE'
field_mapping:
  - aiField: 'NO'
    feishuField: 'NO'
    type: 'string'
    required: true
    description: '工号'
  - aiField: 'NOM_PRENOM'
    feishuField: 'NOM PRENOM'
    type: 'string'
    required: false
    description: '姓名'
  - aiField: 'AGENCE_INTERIMAIRE'
    feishuField: "AGENCE D'INTERIMAIR"
    type: 'string'
    required: false
    description: '中介机构'
  - aiField: 'HORAIRES_DU_TRAVAIL'
    feishuField: 'HORAIRES DU TRAVAI'
    type: 'string'
    required: false
    description: '工作时间'
  - aiField: 'Date'
    feishuField: 'Date'
    type: 'date'
    required: true
    description: '日期'
  - aiField: 'ARRIVEE_DATETIME'
    feishuField: 'ARRIVE'
    type: 'datetime'
    required: true
    description: '到达时间'
  - aiField: 'DEPAR_DATETIME'
    feishuField: 'DEPAR'
    type: 'datetime'
    required: true
    description: '离开时间'
  - aiField: 'PAUSE'
    feishuField: 'PAUS'
    type: 'number'
    required: true
    description: '休息(分钟)'
  - aiField: 'CHECKER'
    feishuField: 'CHECKER'
    type: 'string'
    required: false
    description: '状态'
  - aiField: 'SmartMark'
    feishuField: 'Mark'
    type: 'string'
    required: false
    description: '标记'
  - aiField: 'TASK_ID'
    feishuField: '任务id'
    type: 'string'
    required: false
    description: '任务id'
  - aiField: 'UPLOADED_BY'
    feishuField: '上传人员'
    type: 'user'
    required: false
    description: '上传人员'
```

## 法国 (FR) - 飞书配置

```yaml
bitable_app_token: 'MssZb4YXQaEeR5swLencoIcRnLc'
bitable_table_id: 'tbl5WHAQbm0f6JyE'
field_mapping:
  - aiField: 'NO'
    feishuField: 'NO'
    type: 'string'
    required: true
    description: '工号'
  - aiField: 'NOM_PRENOM'
    feishuField: 'NOM PRENOM'
    type: 'string'
    required: false
    description: '姓名'
  - aiField: 'AGENCE_INTERIMAIRE'
    feishuField: "AGENCE D'INTERIMAIR"
    type: 'string'
    required: false
    description: '中介机构'
  - aiField: 'HORAIRES_DU_TRAVAIL'
    feishuField: 'HORAIRES DU TRAVAI'
    type: 'string'
    required: false
    description: '工作时间'
  - aiField: 'Date'
    feishuField: 'Date'
    type: 'date'
    required: true
    description: '日期'
  - aiField: 'ARRIVEE_DATETIME'
    feishuField: 'ARRIVE'
    type: 'datetime'
    required: true
    description: '到达时间'
  - aiField: 'DEPAR_DATETIME'
    feishuField: 'DEPAR'
    type: 'datetime'
    required: true
    description: '离开时间'
  - aiField: 'PAUSE'
    feishuField: 'PAUS'
    type: 'number'
    required: true
    description: '休息(分钟)'
  - aiField: 'CHECKER'
    feishuField: 'CHECKER'
    type: 'string'
    required: false
    description: '状态'
  - aiField: 'SmartMark'
    feishuField: 'Mark'
    type: 'string'
    required: false
    description: '标记'
  - aiField: 'TASK_ID'
    feishuField: '任务id'
    type: 'string'
    required: false
    description: '任务id'
  - aiField: 'UPLOADED_BY'
    feishuField: '上传人员'
    type: 'user'
    required: false
    description: '上传人员'
```

## 中国 (CN) - 飞书配置

```yaml
bitable_app_token: ''
bitable_table_id: ''
field_mapping:
  - aiField: 'NO'
    feishuField: '工号'
    type: 'string'
    required: true
    description: '工号'
  - aiField: 'NOM_PRENOM'
    feishuField: '姓名'
    type: 'string'
    required: false
    description: '姓名'
  - aiField: 'AGENCE_INTERIMAIRE'
    feishuField: '中介机构'
    type: 'string'
    required: false
    description: '中介机构'
  - aiField: 'HORAIRES_DU_TRAVAIL'
    feishuField: '班次'
    type: 'string'
    required: false
    description: '工作时间'
  - aiField: 'Date'
    feishuField: '日期'
    type: 'date'
    required: true
    description: '日期'
  - aiField: 'ARRIVEE_DATETIME'
    feishuField: '到达时间'
    type: 'datetime'
    required: true
    description: '到达时间'
  - aiField: 'DEPAR_DATETIME'
    feishuField: '离开时间'
    type: 'datetime'
    required: true
    description: '离开时间'
  - aiField: 'PAUSE'
    feishuField: '休息'
    type: 'number'
    required: true
    description: '休息(分钟)'
  - aiField: 'CHECKER'
    feishuField: '状态'
    type: 'string'
    required: false
    description: '状态'
  - aiField: 'SmartMark'
    feishuField: '标记'
    type: 'string'
    required: false
    description: '标记'
  - aiField: 'TASK_ID'
    feishuField: '任务id'
    type: 'string'
    required: false
    description: '任务id'
  - aiField: 'UPLOADED_BY'
    feishuField: '上传人员'
    type: 'user'
    required: false
    description: '上传人员'
```

## 德国 (DE) - 飞书配置

```yaml
bitable_app_token: ''
bitable_table_id: ''
field_mapping:
  - aiField: 'NO'
    feishuField: 'NO'
    type: 'string'
    required: true
    description: '工号'
  - aiField: 'NOM_PRENOM'
    feishuField: 'Name'
    type: 'string'
    required: false
    description: '姓名'
  - aiField: 'AGENCE_INTERIMAIRE'
    feishuField: 'Agentur'
    type: 'string'
    required: false
    description: '中介机构'
  - aiField: 'HORAIRES_DU_TRAVAIL'
    feishuField: 'Schicht'
    type: 'string'
    required: false
    description: '工作时间'
  - aiField: 'Date'
    feishuField: 'Datum'
    type: 'date'
    required: true
    description: '日期'
  - aiField: 'ARRIVEE_DATETIME'
    feishuField: 'Ankunft'
    type: 'datetime'
    required: true
    description: '到达时间'
  - aiField: 'DEPAR_DATETIME'
    feishuField: 'Abfahrt'
    type: 'datetime'
    required: true
    description: '离开时间'
  - aiField: 'PAUSE'
    feishuField: 'Pause'
    type: 'number'
    required: true
    description: '休息(分钟)'
  - aiField: 'CHECKER'
    feishuField: 'Status'
    type: 'string'
    required: false
    description: '状态'
  - aiField: 'SmartMark'
    feishuField: 'Markierung'
    type: 'string'
    required: false
    description: '标记'
  - aiField: 'TASK_ID'
    feishuField: '任务id'
    type: 'string'
    required: false
    description: '任务id'
  - aiField: 'UPLOADED_BY'
    feishuField: '上传人员'
    type: 'user'
    required: false
    description: '上传人员'
```

## 美国 (US) - 飞书配置

```yaml
bitable_app_token: ''
bitable_table_id: ''
field_mapping:
  - aiField: 'NO'
    feishuField: 'Employee ID'
    type: 'string'
    required: true
    description: '工号'
  - aiField: 'NOM_PRENOM'
    feishuField: 'Name'
    type: 'string'
    required: false
    description: '姓名'
  - aiField: 'AGENCE_INTERIMAIRE'
    feishuField: 'Agency'
    type: 'string'
    required: false
    description: '中介机构'
  - aiField: 'HORAIRES_DU_TRAVAIL'
    feishuField: 'Shift'
    type: 'string'
    required: false
    description: '工作时间'
  - aiField: 'Date'
    feishuField: 'Date'
    type: 'date'
    required: true
    description: '日期'
  - aiField: 'ARRIVEE_DATETIME'
    feishuField: 'Arrival Time'
    type: 'datetime'
    required: true
    description: '到达时间'
  - aiField: 'DEPAR_DATETIME'
    feishuField: 'Departure Time'
    type: 'datetime'
    required: true
    description: '离开时间'
  - aiField: 'PAUSE'
    feishuField: 'Break'
    type: 'number'
    required: true
    description: '休息(分钟)'
  - aiField: 'CHECKER'
    feishuField: 'Status'
    type: 'string'
    required: false
    description: '状态'
  - aiField: 'SmartMark'
    feishuField: 'Mark'
    type: 'string'
    required: false
    description: '标记'
  - aiField: 'TASK_ID'
    feishuField: 'Task ID'
    type: 'string'
    required: false
    description: '任务id'
  - aiField: 'UPLOADED_BY'
    feishuField: 'Uploaded By'
    type: 'user'
    required: false
    description: '上传人员'
```
