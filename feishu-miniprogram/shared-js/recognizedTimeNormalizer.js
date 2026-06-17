/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 欧洲时间写法归一化：14H30 / 21H → 14:30 / 21:00；班次取前两个时间点。
 */

const TIME_IN_TEXT_RE = /(\d{1,2})[:hH](\d{2})|(\d{1,2})[hH](?!\d)/gi

function isUnrecognized(value) {
  if (value === undefined || value === null) return true
  const s = String(value).trim()
  if (!s || s === '-' || s === '???') return true
  return false
}

function normalizeClockTime(timeStr) {
  if (timeStr === undefined || timeStr === null) return timeStr
  const str = String(timeStr).trim()
  if (!str || isUnrecognized(str)) return isUnrecognized(str) ? '' : str

  if (/^\d{1,2}:\d{2}$/.test(str)) {
    const [h, m] = str.split(':')
    return `${String(parseInt(h, 10)).padStart(2, '0')}:${m}`
  }
  const hOnly = str.match(/^(\d{1,2})[hH]$/i)
  if (hOnly) {
    return `${String(parseInt(hOnly[1], 10)).padStart(2, '0')}:00`
  }
  const hm = str.match(/^(\d{1,2})[hH](\d{1,2})$/i)
  if (hm) {
    return `${String(parseInt(hm[1], 10)).padStart(2, '0')}:${String(parseInt(hm[2], 10)).padStart(2, '0')}`
  }
  const comma = str.match(/^(\d{1,2})[,.](\d{1,2})$/)
  if (comma) {
    return `${String(parseInt(comma[1], 10)).padStart(2, '0')}:${String(parseInt(comma[2], 10)).padStart(2, '0')}`
  }
  const four = str.match(/^(\d{2})(\d{2})$/)
  if (four) return `${four[1]}:${four[2]}`
  const one = str.match(/^(\d)$/)
  if (one) return `${String(parseInt(one[1], 10)).padStart(2, '0')}:00`
  return str
}

function extractTimeTokenStrings(raw) {
  const s = String(raw || '').trim()
  if (!s) return []
  const tokens = []
  let match
  TIME_IN_TEXT_RE.lastIndex = 0
  while ((match = TIME_IN_TEXT_RE.exec(s)) !== null) {
    if (match[0]) tokens.push(match[0])
  }
  return tokens
}

/** 法文考勤表常见非时间标注（OCR 误入到/离列），无时间点时应视为看不清 */
const NON_TIME_LABEL_PATTERNS = [
  /^repos$/i,
  /\bfin\s+de\s+mission\b/i,
  /^pas\s+de\b/i,
  /^pas$/i,
  /^début$/i,
  /^debut$/i,
  /^fin$/i,
  /^sans\b/i,
]

function isNonTimeFieldLabel(value) {
  const s = String(value || '').trim()
  if (!s || isUnrecognized(s)) return false
  if (extractTimeTokenStrings(s).length > 0) return false
  return NON_TIME_LABEL_PATTERNS.some((pattern) => pattern.test(s))
}

function normalizeShiftSchedule(raw) {
  if (raw === undefined || raw === null) return raw
  const str = String(raw).trim()
  if (!str || isUnrecognized(str)) return isUnrecognized(str) ? '' : str
  const tokens = extractTimeTokenStrings(str)
  if (tokens.length >= 2) {
    return `${normalizeClockTime(tokens[0])}-${normalizeClockTime(tokens[1])}`
  }
  if (tokens.length === 1) {
    return normalizeClockTime(tokens[0])
  }
  return str
}

module.exports = {
  normalizeClockTime,
  normalizeShiftSchedule,
  extractTimeTokenStrings,
  isNonTimeFieldLabel,
}
