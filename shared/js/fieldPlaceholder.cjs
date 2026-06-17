/**
 * AI/识别占位符：不算真实填值，展示为 -，校验视为未填
 * PC Web 与飞书小程序共用（shared/js）
 */

const { isNonTimeFieldLabel } = require('./recognizedTimeNormalizer.cjs')

const TIME_FIELD_KEYS = ['HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR', 'DEPART']
const EXACT_PLACEHOLDERS = { '???': true, '??': true, '-': true, '—': true }
const LOWER_PLACEHOLDERS = {
  illegible: true,
  unknown: true,
  'n/a': true,
  na: true,
  null: true,
  none: true,
}

function isPlaceholderValue(value) {
  if (value === null || value === undefined) return true
  const s = String(value).trim()
  if (!s) return true
  if (EXACT_PLACEHOLDERS[s]) return true
  return !!LOWER_PLACEHOLDERS[s.toLowerCase()]
}

function sanitizeFieldValue(value) {
  return isPlaceholderValue(value) ? '' : String(value).trim()
}

function displayFieldValue(value, emptyDisplay) {
  const empty = emptyDisplay === undefined ? '-' : emptyDisplay
  if (isPlaceholderValue(value)) return empty
  const s = String(value).trim()
  return s || empty
}

const RECORD_TEXT_FIELD_KEYS = [
  'Pays',
  'Entrepot',
  'Date',
  'WorkDate',
  'NOM_PRENOM',
  'Name',
  'NO',
  'AGENCE_INTERIMAIRE',
  'HORAIRES_DU_TRAVAIL',
  'ARRIVEE',
  'DEPAR',
  'DEPART',
  'Observations',
  'PAGE_NUM',
  'pageNum',
]

function sanitizeRecordPlaceholders(record) {
  if (!record || typeof record !== 'object') return record
  const next = { ...record }
  RECORD_TEXT_FIELD_KEYS.forEach((key) => {
    if (key in next) {
      next[key] = sanitizeFieldValue(next[key])
    }
  })
  return next
}

function isExplicitUnreadableValue(value) {
  if (value === null || value === undefined) return false
  const s = String(value).trim()
  if (!s) return false
  if (s === '???' || s === '??') return true
  const lower = s.toLowerCase()
  return lower === 'illegible' || lower === 'unknown'
}

function collectUnreadableFields(record, fieldKeys = RECORD_TEXT_FIELD_KEYS) {
  if (!record || typeof record !== 'object') return []
  const merged = new Set()
  if (Array.isArray(record._unreadableFields)) {
    record._unreadableFields.forEach((key) => {
      if (key) merged.add(String(key))
    })
  }
  fieldKeys.forEach((key) => {
    if (isExplicitUnreadableValue(record[key])) {
      merged.add(key)
      return
    }
    if (TIME_FIELD_KEYS.includes(key) && isNonTimeFieldLabel(record[key])) {
      merged.add(key)
    }
  })
  return [...merged]
}

function isFieldUnreadable(record, fieldKey) {
  if (!record || !fieldKey) return false
  return Array.isArray(record._unreadableFields) && record._unreadableFields.includes(fieldKey)
}

function clearFieldUnreadable(record, fieldKey) {
  if (!record || !fieldKey || !Array.isArray(record._unreadableFields)) return
  record._unreadableFields = record._unreadableFields.filter((k) => k !== fieldKey)
  if (!record._unreadableFields.length) {
    delete record._unreadableFields
  }
}

/** 识别入库：??? 等写入 _unreadableFields，单元格值清空 */
function prepareRecordPlaceholders(record) {
  if (!record || typeof record !== 'object') return record
  const unreadable = collectUnreadableFields(record)
  const next = { ...record }
  RECORD_TEXT_FIELD_KEYS.forEach((key) => {
    if (key in next) {
      if (TIME_FIELD_KEYS.includes(key) && isNonTimeFieldLabel(next[key])) {
        next[key] = ''
      } else {
        next[key] = sanitizeFieldValue(next[key])
      }
    }
  })
  if (unreadable.length) {
    next._unreadableFields = unreadable
  } else {
    delete next._unreadableFields
  }
  return next
}

function stripRecordMetadata(record) {
  if (!record || typeof record !== 'object') return record
  const next = { ...record }
  delete next._unreadableFields
  return next
}

module.exports = {
  isPlaceholderValue,
  isExplicitUnreadableValue,
  sanitizeFieldValue,
  displayFieldValue,
  RECORD_TEXT_FIELD_KEYS,
  sanitizeRecordPlaceholders,
  prepareRecordPlaceholders,
  collectUnreadableFields,
  isFieldUnreadable,
  clearFieldUnreadable,
  stripRecordMetadata,
}
