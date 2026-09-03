/** 字段筛选：类型定义、序列化与本地匹配（与后端 filterType 一致） */

import dayjs from 'dayjs'
import { resolveCountryCodeFromPays } from '@/utils/countryCatalog'

const COUNTRY_FILTER_FIELDS = new Set(['Pays', 'country'])

export const FILTER_TYPES = {
  TEXT: 'text',
  STATUS: 'status',
  DATETIME: 'datetime',
  DATE: 'date',
  TIME: 'time',
  MULTISELECT: 'multiselect',
}

const RANGE_SEP = '|'
const MULTI_SEP = ';'

export function buildTaskStatusOptions(t) {
  return [
    { value: 'processing', label: t('tasks.statusProcessing') },
    { value: 'processed', label: t('tasks.statusProcessed') },
    { value: 'confirmed', label: t('tasks.statusConfirmed') },
    { value: 'failed', label: t('tasks.statusFailed') },
    { value: 'cancelled', label: t('tasks.statusCancelled') },
  ]
}

export function buildSignatureMarkOptions(t) {
  return [
    { value: '未签字', label: t('recognition.marks.unsigned') },
    { value: '已签字', label: t('recognition.marks.signed') },
  ]
}

export function buildSmartMarkOptions(t) {
  return [
    { value: '正常', label: t('recognition.marks.normal') },
    { value: '手写', label: t('recognition.marks.handwriting') },
    { value: '模糊', label: t('recognition.marks.blurred') },
    { value: '夜班', label: t('recognition.marks.nightShift') },
    { value: '未出勤', label: t('recognition.marks.absent') },
    { value: '已删除', label: t('recognition.marks.deleted') },
  ]
}

export function getFilterOptions(def, t, countryOptions = []) {
  if (!def) return []
  if (def.filterOptions) return def.filterOptions
  const key = def.filterOptionsKey
  if (key === 'countryOptions' || def.isCountry) return countryOptions || []
  if (key === 'taskStatus') return buildTaskStatusOptions(t)
  if (key === 'signatureMarks') return buildSignatureMarkOptions(t)
  if (key === 'smartMarks') return buildSmartMarkOptions(t)
  if (key === 'exceptionTypes') return buildExceptionTypeOptions(t)
  if (def.filterType === FILTER_TYPES.STATUS) return buildTaskStatusOptions(t)
  return []
}

function buildExceptionTypeOptions(t) {
  return [
    { value: 'attendance_ok', label: t('taskEdit.exceptionTypeAttendanceOkShort') },
    { value: 'paper_ok_ocr_wrong', label: t('taskEdit.exceptionTypePaperOkOcrWrongShort') },
    { value: 'paper_wrong_time', label: t('taskEdit.exceptionTypePaperWrongTimeShort') },
  ]
}

export function emptyFilterValue(filterType) {
  switch (filterType) {
    case FILTER_TYPES.MULTISELECT:
      return []
    case FILTER_TYPES.DATE:
    case FILTER_TYPES.DATETIME:
    case FILTER_TYPES.TIME:
      return { from: '', to: '' }
    default:
      return ''
  }
}

export function isFilterActive(filterType, value) {
  if (filterType === FILTER_TYPES.MULTISELECT) {
    return Array.isArray(value) && value.length > 0
  }
  if ([FILTER_TYPES.DATE, FILTER_TYPES.DATETIME, FILTER_TYPES.TIME].includes(filterType)) {
    return Boolean(value?.from?.trim() || value?.to?.trim())
  }
  return String(value ?? '').trim() !== ''
}

export function serializeFilterValue(filterType, value) {
  if (!isFilterActive(filterType, value)) return ''
  if (filterType === FILTER_TYPES.MULTISELECT) {
    return (value || []).map((v) => String(v).trim()).filter(Boolean).join(MULTI_SEP)
  }
  if ([FILTER_TYPES.DATE, FILTER_TYPES.DATETIME, FILTER_TYPES.TIME].includes(filterType)) {
    const from = String(value?.from || '').trim()
    const to = String(value?.to || '').trim()
    if (!from && !to) return ''
    return `${from}${RANGE_SEP}${to}`
  }
  return String(value ?? '').trim()
}

export function parseFilterValue(filterType, keyword) {
  const raw = String(keyword ?? '').trim()
  if (!raw) return emptyFilterValue(filterType)

  if (filterType === FILTER_TYPES.MULTISELECT) {
    return raw.split(MULTI_SEP).map((v) => v.trim()).filter(Boolean)
  }
  if ([FILTER_TYPES.DATE, FILTER_TYPES.DATETIME, FILTER_TYPES.TIME].includes(filterType)) {
    const [from = '', to = ''] = raw.split(RANGE_SEP)
    return { from: from.trim(), to: to.trim() }
  }
  return raw
}

export function buildFilterCondition(def, value) {
  const filterType = def?.filterType || FILTER_TYPES.TEXT
  const keyword = serializeFilterValue(filterType, value)
  if (!keyword) return null
  return { field: def.field, keyword, filterType }
}

export function buildFilterConditions(fieldDefs, filtersMap) {
  const list = []
  for (const def of fieldDefs || []) {
    const value = filtersMap?.[def.field]
    const cond = buildFilterCondition(def, value)
    if (cond) list.push(cond)
  }
  return list
}

function parseComparableDate(text) {
  const s = String(text || '').trim()
  if (!s) return null
  const d = dayjs(s)
  return d.isValid() ? d : null
}

function parseComparableTime(text) {
  const s = String(text || '').trim().replace(',', '.')
  if (!s) return null
  const m = s.match(/^(\d{1,2})[:hH.](\d{1,2})/)
  if (!m) return null
  return Number(m[1]) * 60 + Number(m[2])
}

function inDateRange(cell, range) {
  const from = parseComparableDate(range?.from)
  const to = parseComparableDate(range?.to)
  const val = parseComparableDate(cell)
  if (!val) return false
  if (from && val.isBefore(from, 'day')) return false
  if (to && val.isAfter(to, 'day')) return false
  return true
}

function inDateTimeRange(cell, range) {
  const from = parseComparableDate(range?.from)
  const to = parseComparableDate(range?.to)
  const val = parseComparableDate(cell)
  if (!val) return false
  if (from && val.isBefore(from)) return false
  if (to && val.isAfter(to)) return false
  return true
}

function inTimeRange(cell, range) {
  const minutes = parseComparableTime(cell)
  if (minutes == null) return false
  const from = range?.from ? parseComparableTime(range.from) : null
  const to = range?.to ? parseComparableTime(range.to) : null
  if (from != null && minutes < from) return false
  if (to != null && minutes > to) return false
  return true
}

function matchMultiselect(cell, selected) {
  const text = String(cell ?? '')
  if (!text) return false
  const parts = text.split(/[;；,，]/).map((p) => p.trim()).filter(Boolean)
  return selected.some((sel) => parts.includes(sel) || text.includes(sel))
}

/** 国家多选：按标准国家码归一后比对，兼容单元格存的是代码或本地化名称 */
function matchCountryMultiselect(cell, selected) {
  const raw = String(cell ?? '').trim()
  if (!raw) return false
  const cellCode = (resolveCountryCodeFromPays(raw) || raw).toUpperCase()
  return (selected || []).some((sel) => {
    const selCode = (resolveCountryCodeFromPays(sel) || String(sel || '')).trim().toUpperCase()
    return selCode && (selCode === cellCode || raw.toUpperCase() === selCode)
  })
}

/** 任务详情页本地筛选 */
export function matchRecordByFilter(filterType, field, keyword, record) {
  const value = parseFilterValue(filterType, keyword)
  const cell = record?.[field]

  switch (filterType) {
    case FILTER_TYPES.STATUS:
      return String(cell ?? '') === String(value)
    case FILTER_TYPES.MULTISELECT:
      return COUNTRY_FILTER_FIELDS.has(field)
        ? matchCountryMultiselect(cell, value)
        : matchMultiselect(cell, value)
    case FILTER_TYPES.DATE:
      return inDateRange(cell, value)
    case FILTER_TYPES.DATETIME:
      return inDateTimeRange(cell, value)
    case FILTER_TYPES.TIME:
      return inTimeRange(cell, value)
    default:
      return String(cell ?? '').toLowerCase().includes(String(value).toLowerCase())
  }
}

export function getRecognitionFieldFilterMeta() {
  return {
    PAGE_NUM: { filterType: FILTER_TYPES.TEXT },
    NO: { filterType: FILTER_TYPES.TEXT },
    Pays: { filterType: FILTER_TYPES.MULTISELECT, filterOptionsKey: 'countryOptions', isCountry: true },
    Entrepot: { filterType: FILTER_TYPES.TEXT },
    Date: { filterType: FILTER_TYPES.DATE },
    NOM_PRENOM: { filterType: FILTER_TYPES.TEXT },
    AGENCE_INTERIMAIRE: { filterType: FILTER_TYPES.TEXT },
    HORAIRES_DU_TRAVAIL: { filterType: FILTER_TYPES.TEXT },
    ARRIVEE: { filterType: FILTER_TYPES.TIME },
    DEPAR: { filterType: FILTER_TYPES.TIME },
    PAUSE: { filterType: FILTER_TYPES.TEXT },
    SIGNATURE: { filterType: FILTER_TYPES.MULTISELECT, filterOptionsKey: 'signatureMarks' },
    Observations: { filterType: FILTER_TYPES.TEXT },
    SmartMark: { filterType: FILTER_TYPES.MULTISELECT, filterOptionsKey: 'smartMarks' },
    ExceptionType: { filterType: FILTER_TYPES.MULTISELECT, filterOptionsKey: 'exceptionTypes' },
  }
}

export function resolveColumnFilterType(column) {
  if (column?.filterType) return column.filterType
  const meta = getRecognitionFieldFilterMeta()[column?.searchField || column?.dataIndex]
  return meta?.filterType || FILTER_TYPES.TEXT
}

export function resolveColumnFilterOptions(column, t, countryOptions = []) {
  if (column?.filterOptions?.length) return column.filterOptions
  const meta = getRecognitionFieldFilterMeta()[column?.searchField || column?.dataIndex]
  return getFilterOptions({ ...meta, filterType: resolveColumnFilterType(column) }, t, countryOptions)
}
