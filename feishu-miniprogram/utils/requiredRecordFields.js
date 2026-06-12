/** 确认提交必填字段：未出勤、已删除行不校验 */

const { markContains } = require('./recognitionLabels')
const { isPlaceholderValue } = require('./fieldPlaceholder')

const REQUIRED_SUBMIT_FIELD_KEYS = ['NOM_PRENOM', 'Date']

const FIELD_ALIASES = {
  NOM_PRENOM: ['NOM_PRENOM', 'Name'],
  Date: ['Date', 'WorkDate']
}

function pickField(record, ...keys) {
  if (!record) return ''
  for (let i = 0; i < keys.length; i++) {
    const v = record[keys[i]]
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      return String(v).trim()
    }
  }
  return ''
}

function hasFilledText(value) {
  return !isPlaceholderValue(value)
}

function getRecordMark(record) {
  return String((record && record.SmartMark) || (record && record.Mark) || '').trim()
}

function isRequiredValidationExempt(record) {
  if (!record) return true
  if (record.isDeleted) return true
  const mark = getRecordMark(record)
  if (markContains(mark, 'deleted')) return true
  if (mark.indexOf('未出勤') !== -1 && !record._restored) return true
  if (markContains(mark, 'absent') && !record._restored) return true
  return false
}

function isFieldMissing(record, fieldKey) {
  const keys = FIELD_ALIASES[fieldKey] || [fieldKey]
  return !hasFilledText(pickField(record, ...keys))
}

function getMissingRequiredFieldKeys(record) {
  if (isRequiredValidationExempt(record)) return []
  return REQUIRED_SUBMIT_FIELD_KEYS.filter(function (key) {
    return isFieldMissing(record, key)
  })
}

function hasRequiredMissing(record) {
  return getMissingRequiredFieldKeys(record).length > 0
}

module.exports = {
  REQUIRED_SUBMIT_FIELD_KEYS,
  isRequiredValidationExempt,
  getMissingRequiredFieldKeys,
  hasRequiredMissing
}
