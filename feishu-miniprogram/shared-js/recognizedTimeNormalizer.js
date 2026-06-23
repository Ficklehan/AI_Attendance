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
    const hour = parseInt(h, 10)
    const minute = parseInt(m, 10)
    if (hour === 24 && minute >= 0 && minute <= 59) {
      return `24:${String(minute).padStart(2, '0')}`
    }
    return `${String(hour).padStart(2, '0')}:${m}`
  }
  const hOnly = str.match(/^(\d{1,2})[hH]$/i)
  if (hOnly) {
    const hour = parseInt(hOnly[1], 10)
    if (hour === 24) return '24:00'
    return `${String(hour).padStart(2, '0')}:00`
  }
  const hm = str.match(/^(\d{1,2})[hH](\d{1,2})$/i)
  if (hm) {
    const hour = parseInt(hm[1], 10)
    const minute = parseInt(hm[2], 10)
    if (hour === 24 && minute >= 0 && minute <= 59) {
      return `24:${String(minute).padStart(2, '0')}`
    }
    return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
  }
  const comma = str.match(/^(\d{1,2})[,.](\d{1,2})$/)
  if (comma) {
    return `${String(parseInt(comma[1], 10)).padStart(2, '0')}:${String(parseInt(comma[2], 10)).padStart(2, '0')}`
  }
  const four = str.match(/^(\d{2})(\d{2})$/)
  if (four) {
    const hour = parseInt(four[1], 10)
    const minute = parseInt(four[2], 10)
    if (hour === 24 && minute >= 0 && minute <= 59) {
      return `24:${String(minute).padStart(2, '0')}`
    }
    return `${four[1]}:${four[2]}`
  }
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

function isValidClockTime(value) {
  const s = String(value || '').trim()
  if (!/^\d{1,2}:\d{2}$/.test(s)) return false
  const [h, m] = s.split(':')
  const hour = parseInt(h, 10)
  const minute = parseInt(m, 10)
  if (hour === 24) return minute >= 0 && minute <= 59
  return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59
}

function extendedMidnightDayOffset(timeStr) {
  if (timeStr === undefined || timeStr === null || isUnrecognized(timeStr)) return 0
  const normalized = normalizeClockTime(timeStr)
  if (!isValidClockTime(normalized)) return 0
  return parseInt(normalized.split(':')[0], 10) === 24 ? 1 : 0
}

function clockTimeToMinutesOfDay(timeStr) {
  if (timeStr === undefined || timeStr === null || isUnrecognized(timeStr)) return null
  const normalized = normalizeClockTime(timeStr)
  if (!isValidClockTime(normalized)) return null
  let hour = parseInt(normalized.split(':')[0], 10)
  const minute = parseInt(normalized.split(':')[1], 10)
  if (hour === 24) hour = 0
  return hour * 60 + minute
}

function computeAttendanceDurationMinutes(arriveRaw, departRaw) {
  const arriveOffset = extendedMidnightDayOffset(arriveRaw)
  const departOffset = extendedMidnightDayOffset(departRaw)
  const arriveMinutes = clockTimeToMinutesOfDay(arriveRaw)
  const departMinutes = clockTimeToMinutesOfDay(departRaw)
  if (arriveMinutes === null || departMinutes === null) return null
  const arriveTimeline = arriveOffset * 1440 + arriveMinutes
  let departTimeline = departOffset * 1440 + departMinutes
  if (departTimeline < arriveTimeline) departTimeline += 1440
  return departTimeline - arriveTimeline
}

module.exports = {
  normalizeClockTime,
  normalizeShiftSchedule,
  extractTimeTokenStrings,
  isNonTimeFieldLabel,
  isValidClockTime,
  extendedMidnightDayOffset,
  clockTimeToMinutesOfDay,
  computeAttendanceDurationMinutes,
}
