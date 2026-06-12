/** 确认提交必填字段：可配置；仅删除、未出勤行跳过校验 */

import { markContains } from './recognitionLabels'
import { isPlaceholderValue } from './fieldPlaceholder'

export const CONFIRM_FIELD_KEYS = [
  'Pays',
  'Entrepot',
  'Date',
  'NOM_PRENOM',
  'AGENCE_INTERIMAIRE',
  'HORAIRES_DU_TRAVAIL',
  'ARRIVEE',
  'DEPAR',
  'PAUSE',
]

export const DEFAULT_CONFIRM_VALIDATION = {
  scope: 'except_deleted_absent',
  requiredFields: ['NOM_PRENOM', 'Date', 'ARRIVEE', 'DEPAR', 'PAUSE'],
}

/** 表格列必填星号等沿用此数组，随 setConfirmValidationConfig 同步更新 */
export const REQUIRED_SUBMIT_FIELD_KEYS = [...DEFAULT_CONFIRM_VALIDATION.requiredFields]

const FIELD_ALIASES = {
  NOM_PRENOM: ['NOM_PRENOM', 'Name'],
  Date: ['Date', 'WorkDate'],
  DEPAR: ['DEPAR', 'DEPART'],
}

let activeConfig = {
  scope: DEFAULT_CONFIRM_VALIDATION.scope,
  requiredFields: [...DEFAULT_CONFIRM_VALIDATION.requiredFields],
}

function syncRequiredSubmitFieldKeys(fields) {
  REQUIRED_SUBMIT_FIELD_KEYS.length = 0
  REQUIRED_SUBMIT_FIELD_KEYS.push(...fields)
}

export function setConfirmValidationConfig(config) {
  if (!config) {
    activeConfig = {
      scope: DEFAULT_CONFIRM_VALIDATION.scope,
      requiredFields: [...DEFAULT_CONFIRM_VALIDATION.requiredFields],
    }
    syncRequiredSubmitFieldKeys(activeConfig.requiredFields)
    return
  }
  const fields = Array.isArray(config.requiredFields) && config.requiredFields.length
    ? config.requiredFields.filter((k) => CONFIRM_FIELD_KEYS.includes(k))
    : [...DEFAULT_CONFIRM_VALIDATION.requiredFields]
  activeConfig = {
    scope: config.scope || DEFAULT_CONFIRM_VALIDATION.scope,
    requiredFields: fields,
  }
  syncRequiredSubmitFieldKeys(fields)
}

export function getConfirmValidationConfig() {
  return activeConfig
}

export function isConfiguredRequiredField(fieldKey, config = activeConfig) {
  return (config.requiredFields || []).includes(fieldKey)
}

export function appendRequiredMark(label) {
  return `${label} *`
}

function pickField(record, ...keys) {
  if (!record) return ''
  for (const key of keys) {
    const v = record[key]
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      return String(v).trim()
    }
  }
  return ''
}

function hasFilledText(value) {
  return !isPlaceholderValue(value)
}

function isPauseMissing(value) {
  if (value === 0 || value === '0') return false
  if (isPlaceholderValue(value)) return true
  const s = String(value).trim()
  if (!s) return true
  if (s === '00:00' || s === '0:00') return false
  return false
}

function getRecordMark(record) {
  return String(record?.SmartMark || record?.Mark || '').trim()
}

/** 仅删除、未出勤行跳过校验（含手写、模糊、夜班等） */
export function isRequiredValidationExempt(record, config = activeConfig) {
  if (!record) return true
  if (record.isDeleted) return true
  const mark = getRecordMark(record)
  if (markContains(mark, 'deleted')) return true
  if (!record._restored && (mark.includes('未出勤') || markContains(mark, 'absent'))) return true
  return false
}

function isFieldMissing(record, fieldKey) {
  if (fieldKey === 'PAUSE') {
    return isPauseMissing(record.PAUSE)
  }
  const keys = FIELD_ALIASES[fieldKey] || [fieldKey]
  return !hasFilledText(pickField(record, ...keys))
}

export function getMissingRequiredFieldKeys(record, config = activeConfig) {
  if (isRequiredValidationExempt(record, config)) return []
  return (config.requiredFields || []).filter((key) => isFieldMissing(record, key))
}

export function hasRequiredMissing(record, config = activeConfig) {
  return getMissingRequiredFieldKeys(record, config).length > 0
}

export function collectConfirmValidationIssues(records, config = activeConfig) {
  const issues = []
  ;(records || []).forEach((record, index) => {
    const fields = getMissingRequiredFieldKeys(record, config)
    if (fields.length) {
      issues.push({
        line: index + 1,
        no: pickField(record, 'NO') || '?',
        name: pickField(record, 'NOM_PRENOM', 'Name'),
        fields,
      })
    }
  })
  return issues
}

/** TaskEdit 提交错误详情：字段 key -> i18n key */
export const REQUIRED_FIELD_I18N_KEYS = {
  Pays: 'taskEdit.countryField',
  Entrepot: 'taskEdit.warehouse',
  Date: 'taskEdit.date',
  NOM_PRENOM: 'taskEdit.name',
  AGENCE_INTERIMAIRE: 'taskEdit.agency',
  HORAIRES_DU_TRAVAIL: 'taskEdit.shift',
  ARRIVEE: 'taskEdit.arrival',
  DEPAR: 'taskEdit.departure',
  PAUSE: 'taskEdit.breakTime',
}
