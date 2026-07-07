import { FILTER_TYPES } from '@/utils/fieldFilterValue'

/**
 * 考勤记录列表：列定义与筛选字段类型（与后端 TaskRecordMapper 条件字段一致）
 * filterType: text | status | datetime | date | time | multiselect
 */
export function buildEmployeeRecordFieldDefs(t) {
  return [
    { field: 'taskId', label: t('tasks.taskId'), dataIndex: 'taskId', key: 'taskId', filterType: FILTER_TYPES.TEXT },
    { field: 'PAGE_NUM', label: t('taskEdit.pageNumber'), dataIndex: 'pageNum', key: 'pageNum', filterType: FILTER_TYPES.TEXT },
    { field: 'NO', label: t('taskEdit.workerNumber'), dataIndex: 'no', key: 'no', filterType: FILTER_TYPES.TEXT, density: 'compact' },
    { field: 'NOM_PRENOM', label: t('taskEdit.name'), dataIndex: 'name', key: 'name', filterType: FILTER_TYPES.TEXT, ellipsis: false },
    { field: 'Pays', label: t('taskEdit.countryField'), dataIndex: 'country', key: 'country', filterType: FILTER_TYPES.TEXT, density: 'compact' },
    { field: 'Entrepot', label: t('taskEdit.warehouse'), dataIndex: 'warehouse', key: 'warehouse', filterType: FILTER_TYPES.TEXT },
    { field: 'Date', label: t('taskEdit.date'), dataIndex: 'date', key: 'date', filterType: FILTER_TYPES.DATE, density: 'compact' },
    { field: 'AGENCE_INTERIMAIRE', label: t('taskEdit.agency'), dataIndex: 'agency', key: 'agency', filterType: FILTER_TYPES.TEXT },
    { field: 'HORAIRES_DU_TRAVAIL', label: t('taskEdit.shift'), dataIndex: 'shift', key: 'shift', filterType: FILTER_TYPES.TEXT, density: 'compact' },
    { field: 'ARRIVEE', label: t('taskEdit.arrival'), dataIndex: 'arrival', key: 'arrival', filterType: FILTER_TYPES.TIME, density: 'compact' },
    { field: 'DEPAR', label: t('taskEdit.departure'), dataIndex: 'departure', key: 'departure', filterType: FILTER_TYPES.TIME, density: 'compact' },
    { field: 'PAUSE', label: t('taskEdit.breakTime'), dataIndex: 'pauseMinutes', key: 'pauseMinutes', filterType: FILTER_TYPES.TEXT, density: 'compact' },
    { field: 'workHours', label: t('taskEdit.workHours'), dataIndex: 'workHours', key: 'workHours', filterable: false, ellipsis: false },
    { field: 'SIGNATURE', label: t('taskEdit.signature'), dataIndex: 'signature', key: 'signature', filterType: FILTER_TYPES.MULTISELECT, filterOptionsKey: 'signatureMarks' },
    { field: 'SmartMark', label: t('taskEdit.mark'), dataIndex: 'smartMark', key: 'smartMark', filterType: FILTER_TYPES.MULTISELECT, filterOptionsKey: 'smartMarks' },
    { field: 'Observations', label: t('taskEdit.observations'), dataIndex: 'observations', key: 'observations', filterType: FILTER_TYPES.TEXT },
    { field: 'anomalyDescription', label: t('taskEdit.anomalyReasons'), dataIndex: 'anomalyDescription', key: 'anomalyDescription', filterable: false, ellipsis: false },
    { field: 'userName', label: t('tasks.operator'), dataIndex: 'userName', key: 'userName', filterType: FILTER_TYPES.TEXT },
    { field: 'status', label: t('tasks.status'), dataIndex: 'taskStatus', key: 'taskStatus', filterType: FILTER_TYPES.STATUS, filterOptionsKey: 'taskStatus' },
    { field: 'createdAt', label: t('tasks.createTime'), dataIndex: 'createdAt', key: 'createdAt', filterType: FILTER_TYPES.DATETIME },
    { field: 'fileKey', label: t('tasks.fileName'), dataIndex: 'fileKey', key: 'fileKey', filterType: FILTER_TYPES.TEXT },
  ]
}

export function buildEmptyAdvancedFilters(fieldDefs) {
  const filters = {}
  fieldDefs.forEach((def) => {
    if (def.filterType === FILTER_TYPES.MULTISELECT) {
      filters[def.field] = []
    } else if ([FILTER_TYPES.DATE, FILTER_TYPES.DATETIME, FILTER_TYPES.TIME].includes(def.filterType)) {
      filters[def.field] = { from: '', to: '' }
    } else {
      filters[def.field] = ''
    }
  })
  return filters
}

export function filterPlaceholder(def, t) {
  if (def.filterType === FILTER_TYPES.STATUS) return t('tasks.filterStatus')
  if (def.filterType === FILTER_TYPES.DATETIME) return t('tasks.filterDateTimeRange')
  if (def.filterType === FILTER_TYPES.DATE) return t('tasks.filterDateRange')
  if (def.filterType === FILTER_TYPES.TIME) return t('tasks.filterTimeRange')
  if (def.filterType === FILTER_TYPES.MULTISELECT) return t('tasks.filterMultiSelect')
  if (def.placeholderKey) return t(def.placeholderKey)
  return t('tasks.searchContent')
}

export function findFieldDef(fieldDefs, field) {
  return fieldDefs.find((def) => def.field === field)
}
