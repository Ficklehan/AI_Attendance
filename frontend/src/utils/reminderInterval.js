/** 提醒周期数值：统一最多一位小数 */
export function normalizeIntervalValue(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return null
  return Math.round(n * 10) / 10
}

export function formatIntervalValue(value) {
  const n = normalizeIntervalValue(value)
  if (n == null) return '—'
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

export function isValidIntervalValue(value) {
  const n = normalizeIntervalValue(value)
  return n != null && n >= 0.1
}
