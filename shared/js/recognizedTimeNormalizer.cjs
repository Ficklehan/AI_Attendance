/**
 * 欧洲时间写法归一化：14H30 / 21H → 14:30 / 21:00；班次取前两个时间点。
 * 人工录入友好：9:01 → 09:01，1 → 01:00，14 → 14:00。
 * 粘贴友好：2026-09-03 08:30:00 / 8:30 PM → 08:30 / 20:30。
 */

const TIME_IN_TEXT_RE = /(\d{1,2})[:hH](\d{2})|(\d{1,2})[hH](?!\d)/gi

/** 日期后紧跟时刻（Excel / ISO / 欧式） */
const DATE_THEN_TIME_RE = /(?:\d{4}[-/.]\d{1,2}[-/.]\d{1,2}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})[T\s]+(\d{1,2})[:hH.](\d{2})(?::\d{2})?(?:\s*([AaPp][Mm]))?/

const ISO_T_TIME_RE = /T(\d{1,2}):(\d{2})(?::\d{2})?/

const STANDALONE_CLOCK_RE = /^(\d{1,2}):(\d{2})(?::\d{2})?(?:\s*([AaPp][Mm]))?$/i

function isUnrecognized(value) {
  if (value === undefined || value === null) return true
  const s = String(value).trim()
  if (!s || s === '-' || s === '???') return true
  return false
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

function formatValidClock(hours, minutes) {
  if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) return null
  return `${pad2(hours)}:${pad2(minutes)}`
}

function applyAmPm(hours, ampm) {
  if (!ampm) return hours
  const mer = String(ampm).charAt(0).toLowerCase()
  if (mer === 'a') return hours === 12 ? 0 : hours
  if (mer === 'p') return hours < 12 ? hours + 12 : hours
  return hours
}

function isCanonicalClockTime(value) {
  return /^\d{2}:\d{2}$/.test(String(value || ''))
}

/**
 * 从粘贴的日期时间 / 带秒 / AM-PM 文本中取出 HH:mm；无法识别返回 null。
 */
function extractClockFromPastedText(raw) {
  const s = String(raw == null ? '' : raw).trim()
  if (!s) return null

  const dated = s.match(DATE_THEN_TIME_RE)
  if (dated) {
    return formatValidClock(applyAmPm(parseInt(dated[1], 10), dated[3]), parseInt(dated[2], 10))
  }

  const isoT = s.match(ISO_T_TIME_RE)
  if (isoT) {
    return formatValidClock(parseInt(isoT[1], 10), parseInt(isoT[2], 10))
  }

  const standalone = s.match(STANDALONE_CLOCK_RE)
  if (standalone && (standalone[3] || /:\d{2}:\d{2}/.test(s))) {
    return formatValidClock(
      applyAmPm(parseInt(standalone[1], 10), standalone[3]),
      parseInt(standalone[2], 10),
    )
  }

  const ampmOnly = s.match(/^(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp][Mm])$/i)
  if (ampmOnly) {
    return formatValidClock(
      applyAmPm(parseInt(ampmOnly[1], 10), ampmOnly[3]),
      parseInt(ampmOnly[2], 10),
    )
  }

  return null
}

function normalizeCompactClockToken(timeStr) {
  let str = String(timeStr).trim()
  if (!str) return str
  str = str.replace(/\s+/g, '')

  let match = str.match(/^(\d{1,2}):(\d{1,2})$/)
  if (match) {
    const formatted = formatValidClock(parseInt(match[1], 10), parseInt(match[2], 10))
    if (formatted) return formatted
  }

  const withSeconds = str.match(/^(\d{1,2}):(\d{2}):(\d{2})$/)
  if (withSeconds) {
    const formatted = formatValidClock(parseInt(withSeconds[1], 10), parseInt(withSeconds[2], 10))
    if (formatted) return formatted
  }

  const hOnly = str.match(/^(\d{1,2})[hH]$/i)
  if (hOnly) {
    const formatted = formatValidClock(parseInt(hOnly[1], 10), 0)
    if (formatted) return formatted
  }

  const hm = str.match(/^(\d{1,2})[hH](\d{1,2})$/i)
  if (hm) {
    const formatted = formatValidClock(parseInt(hm[1], 10), parseInt(hm[2], 10))
    if (formatted) return formatted
  }

  const comma = str.match(/^(\d{1,2})[,.](\d{1,2})$/)
  if (comma) {
    const formatted = formatValidClock(parseInt(comma[1], 10), parseInt(comma[2], 10))
    if (formatted) return formatted
  }

  const four = str.match(/^(\d{2})(\d{2})$/)
  if (four) {
    const formatted = formatValidClock(parseInt(four[1], 10), parseInt(four[2], 10))
    if (formatted) return formatted
  }

  const three = str.match(/^(\d)(\d{2})$/)
  if (three) {
    const formatted = formatValidClock(parseInt(three[1], 10), parseInt(three[2], 10))
    if (formatted) return formatted
  }

  // 1 / 9 / 14 → 01:00 / 09:00 / 14:00
  const hourOnly = str.match(/^(\d{1,2})$/)
  if (hourOnly) {
    const formatted = formatValidClock(parseInt(hourOnly[1], 10), 0)
    if (formatted) return formatted
  }

  return str
}

/**
 * 将单点时间规范为 HH:MM；无法识别则原样返回（空/??? → ''）。
 */
function normalizeClockTime(timeStr) {
  if (timeStr === undefined || timeStr === null) return timeStr
  const original = String(timeStr).trim()
  if (!original || isUnrecognized(original)) return isUnrecognized(original) ? '' : original

  const pasted = extractClockFromPastedText(original)
  if (pasted) return pasted

  const tokens = extractTimeTokenStrings(original)
  if (tokens.length === 1) {
    const fromToken = normalizeCompactClockToken(tokens[0])
    if (isCanonicalClockTime(fromToken)) return fromToken
  }

  return normalizeCompactClockToken(original)
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
  // 纯数字 / 简易时钟由 normalizeClockTime 处理，不算「非时间标注」
  if (/^\d{1,2}([:hH.,]\d{1,2})?$/i.test(s.replace(/\s+/g, ''))) return false
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
  // 友好录入：9:00-14:00 已由 token 覆盖；9-14 等再尝试按分隔符拆
  const parts = str.split(/\s*[-–—~～to]+\s*/i).map((p) => p.trim()).filter(Boolean)
  if (parts.length === 2) {
    const a = normalizeClockTime(parts[0])
    const b = normalizeClockTime(parts[1])
    if (/^\d{2}:\d{2}$/.test(a) && /^\d{2}:\d{2}$/.test(b)) {
      return `${a}-${b}`
    }
  }
  return str
}

module.exports = {
  normalizeClockTime,
  normalizeShiftSchedule,
  extractTimeTokenStrings,
  extractClockFromPastedText,
  isCanonicalClockTime,
  isNonTimeFieldLabel,
}
