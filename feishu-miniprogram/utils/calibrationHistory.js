const { t } = require('./i18n')
const { FIELD_LABEL_KEYS } = require('./calibratableFields')

function parseCalibrationHistory(record) {
  const raw = record && record._calibrationHistory
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function hasManualCalibration(record) {
  if (!record) return false
  if (record._manualCalibrated === true) return true
  return parseCalibrationHistory(record).length > 0
}

function formatCalibDisplayValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function formatHistoryTime(at) {
  if (!at) return '—'
  const s = String(at).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

function formatHistoryChanges(entry) {
  const changes = entry && entry.changes
  if (!changes || typeof changes !== 'object') return []
  return Object.keys(changes).map((field) => {
    const diff = changes[field] || {}
    return {
      field,
      label: t(FIELD_LABEL_KEYS[field] || field),
      from: formatCalibDisplayValue(diff.from),
      to: formatCalibDisplayValue(diff.to)
    }
  })
}

function buildCalibrationHistoryUi(record) {
  return parseCalibrationHistory(record)
    .slice()
    .reverse()
    .map((entry) => ({
      byName: entry.byName || entry.by || '—',
      at: formatHistoryTime(entry.at),
      reason: entry.reason || '',
      changes: formatHistoryChanges(entry)
    }))
}

module.exports = {
  parseCalibrationHistory,
  hasManualCalibration,
  formatCalibDisplayValue,
  buildCalibrationHistoryUi
}
