/** AI/识别占位符：不算真实填值，展示为 -，校验视为未填 */

const EXACT_PLACEHOLDERS = new Set(['???', '??', '-', '—'])
const LOWER_PLACEHOLDERS = new Set(['illegible', 'unknown', 'n/a', 'na', 'null', 'none'])

export function isPlaceholderValue(value) {
  if (value === null || value === undefined) return true
  const s = String(value).trim()
  if (!s) return true
  if (EXACT_PLACEHOLDERS.has(s)) return true
  return LOWER_PLACEHOLDERS.has(s.toLowerCase())
}

export function sanitizeFieldValue(value) {
  return isPlaceholderValue(value) ? '' : String(value).trim()
}

export function displayFieldValue(value, emptyDisplay = '-') {
  if (isPlaceholderValue(value)) return emptyDisplay
  return String(value).trim() || emptyDisplay
}

export const RECORD_TEXT_FIELD_KEYS = [
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

export function sanitizeRecordPlaceholders(record) {
  if (!record || typeof record !== 'object') return record
  const next = { ...record }
  for (const key of RECORD_TEXT_FIELD_KEYS) {
    if (key in next) {
      next[key] = sanitizeFieldValue(next[key])
    }
  }
  return next
}
