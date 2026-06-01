/** 确认提交必填字段：未出勤、已删除行不校验 */

import { markContains } from './recognitionLabels'

export const REQUIRED_SUBMIT_FIELD_KEYS = ['NOM_PRENOM', 'Date']

const FIELD_ALIASES = {
  NOM_PRENOM: ['NOM_PRENOM', 'Name'],
  Date: ['Date', 'WorkDate'],
}

const PLACEHOLDER_VALUES = new Set(['???', '??', 'illegible'])

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
  const s = value === null || value === undefined ? '' : String(value).trim()
  if (!s) return false
  if (PLACEHOLDER_VALUES.has(s) || PLACEHOLDER_VALUES.has(s.toLowerCase())) return false
  return true
}

function getRecordMark(record) {
  return String(record?.SmartMark || record?.Mark || '').trim()
}

/** 未出勤、已删除（含标记）行跳过必填校验 */
export function isRequiredValidationExempt(record) {
  if (!record) return true
  if (record.isDeleted) return true
  const mark = getRecordMark(record)
  if (markContains(mark, 'deleted')) return true
  if (mark.includes('未出勤') && !record._restored) return true
  if (markContains(mark, 'absent') && !record._restored) return true
  return false
}

function isFieldMissing(record, fieldKey) {
  const keys = FIELD_ALIASES[fieldKey] || [fieldKey]
  return !hasFilledText(pickField(record, ...keys))
}

export function getMissingRequiredFieldKeys(record) {
  if (isRequiredValidationExempt(record)) return []
  return REQUIRED_SUBMIT_FIELD_KEYS.filter((key) => isFieldMissing(record, key))
}

export function hasRequiredMissing(record) {
  return getMissingRequiredFieldKeys(record).length > 0
}

/** TaskEdit 提交错误详情：字段 key -> i18n key */
export const REQUIRED_FIELD_I18N_KEYS = {
  NOM_PRENOM: 'taskEdit.name',
  Date: 'taskEdit.date',
}
