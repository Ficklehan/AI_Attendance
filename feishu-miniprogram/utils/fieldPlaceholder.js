/** AI/识别占位符：不算真实填值，展示为 -，校验视为未填 */

const EXACT = { '???': true, '??': true, '-': true, '—': true }
const LOWER = { illegible: true, unknown: true, 'n/a': true, na: true, null: true, none: true }

function isPlaceholderValue(value) {
  if (value === null || value === undefined) return true
  const s = String(value).trim()
  if (!s) return true
  if (EXACT[s]) return true
  return !!LOWER[s.toLowerCase()]
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

module.exports = {
  isPlaceholderValue,
  sanitizeFieldValue,
  displayFieldValue,
}
