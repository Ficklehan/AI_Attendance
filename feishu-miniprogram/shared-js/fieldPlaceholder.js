/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * AI/识别占位符：不算真实填值，展示为 -，校验视为未填
 * PC Web 与飞书小程序共用（shared/js）
 */

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

module.exports = {
  isPlaceholderValue,
  sanitizeFieldValue,
  displayFieldValue,
  RECORD_TEXT_FIELD_KEYS,
  sanitizeRecordPlaceholders,
}
