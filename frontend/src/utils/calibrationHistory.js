/** 员工记录人工校准历史解析与展示 */

export function parseCalibrationHistory(record) {
  const raw = record?._calibrationHistory
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function hasManualCalibration(record) {
  if (!record) return false
  if (record._manualCalibrated === true) return true
  return parseCalibrationHistory(record).length > 0
}

export function formatCalibDisplayValue(value) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

/**
 * @param {object} entry - { at, byName, reason, changes }
 * @param {(key: string) => string} labelForField
 */
export function formatHistoryChanges(entry, labelForField = (k) => k) {
  const changes = entry?.changes
  if (!changes || typeof changes !== 'object') return []
  return Object.entries(changes).map(([field, diff]) => ({
    field,
    label: labelForField(field),
    from: formatCalibDisplayValue(diff?.from),
    to: formatCalibDisplayValue(diff?.to),
  }))
}
