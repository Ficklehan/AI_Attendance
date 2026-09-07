/**
 * 日期归一化 → YYYY-MM-DD。
 * a/b/yyyy（或 . -）：默认按月/日/年；月不能 >12，若一侧 >12 则该侧为日、另一侧为月。
 * 例：12/06/2026→2026-12-06；13/06/2026→2026-06-13；06/13/2026→2026-06-13。
 * 已是 YYYY-MM-DD 时：月份合法则原样通过；月份非法（13–99）且日在 1–12 则交换月日；否则置空。
 */

function pad2(n) {
  return String(n).padStart(2, '0')
}

function isLeapYear(year) {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
}

function daysInMonth(year, month) {
  if (month === 2) return isLeapYear(year) ? 29 : 28
  if ([4, 6, 9, 11].includes(month)) return 30
  return 31
}

function isValidCanonicalDate(value) {
  const str = String(value || '').trim()
  const m = str.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!m) return false
  const year = parseInt(m[1], 10)
  const month = parseInt(m[2], 10)
  const day = parseInt(m[3], 10)
  if (month < 1 || month > 12) return false
  if (day < 1 || day > daysInMonth(year, month)) return false
  return true
}

function buildCanonical(year, month, day) {
  const y = String(year)
  const candidate = `${y}-${pad2(month)}-${pad2(day)}`
  return isValidCanonicalDate(candidate) ? candidate : null
}

/**
 * 模型已输出 YYYY-MM-DD 但月份位非法（如 2026-25-12）。
 * 月份 1–12：不交换。月份非法且日在 1–12：交换；否则置空。
 * 非 YYYY-MM-DD：返回 null。
 */
function correctIllegalIsoMonth(str) {
  const m = str.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!m) return null
  const month = parseInt(m[2], 10)
  const day = parseInt(m[3], 10)
  if (month >= 1 && month <= 12) return str
  if (day >= 1 && day <= 12) {
    return buildCanonical(m[1], day, month) || ''
  }
  return ''
}

/** YYYY/M/D：中间段为月；月非法且末段为合法月则交换，否则置空。 */
function resolveYearFirst(year, month, day, fallback) {
  const built = buildCanonical(year, month, day)
  if (built) return built
  if (month < 1 || month > 12) {
    if (day >= 1 && day <= 12) {
      return buildCanonical(year, day, month) || ''
    }
    return ''
  }
  return fallback
}

/** a/b/year：默认月日；月不能>12 时互换（>12 的一侧为日） */
function resolveMonthDayYear(a, b, year) {
  const p = parseInt(a, 10)
  const q = parseInt(b, 10)
  if (p > 12 && q >= 1 && q <= 12) {
    return buildCanonical(year, q, p)
  }
  if (q > 12 && p >= 1 && p <= 12) {
    return buildCanonical(year, p, q)
  }
  if (p >= 1 && p <= 12 && q >= 1) {
    return buildCanonical(year, p, q)
  }
  return null
}

function normalizeDate(raw) {
  if (raw === undefined || raw === null) return ''
  const str = String(raw).trim()
  if (!str || str === '-' || str === '???') return ''

  if (isValidCanonicalDate(str)) {
    return str
  }

  const isoCorrected = correctIllegalIsoMonth(str)
  if (isoCorrected !== null) {
    return isoCorrected
  }

  let m = str.match(/^(\d{4})[/.-](\d{1,2})[/.-](\d{1,2})$/)
  if (m) {
    return resolveYearFirst(m[1], parseInt(m[2], 10), parseInt(m[3], 10), str)
  }

  m = str.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/)
  if (m) {
    const built = resolveMonthDayYear(m[1], m[2], parseInt(m[3], 10))
    return built || str
  }

  m = str.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2})$/)
  if (m) {
    const built = resolveMonthDayYear(m[1], m[2], 2000 + parseInt(m[3], 10))
    return built || str
  }

  return str
}

function isDateFormatInvalid(value) {
  const str = String(value || '').trim()
  if (!str || str === '-' || str === '???') return false
  return !isValidCanonicalDate(str)
}

/**
 * 对调 YYYY-MM-DD 的月/日。识别把 9/2 写成 2/9 时用。
 * 输入非法、月日相同、或对调后不是合法日期时返回 null。
 */
function swapDateMonthDay(raw) {
  const str = String(raw == null ? '' : raw).trim()
  if (!isValidCanonicalDate(str)) return null
  const m = str.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!m) return null
  const year = parseInt(m[1], 10)
  const month = parseInt(m[2], 10)
  const day = parseInt(m[3], 10)
  if (month === day) return null
  return buildCanonical(year, day, month)
}

function canSwapDateMonthDay(raw) {
  return swapDateMonthDay(raw) != null
}

const DATE_RAW_RE = /^(\d{1,2})[/.,\-\s]+(\d{1,2})[/.,\-\s]+(\d{4}|\d{2})$/

function expandYear(token) {
  const year = parseInt(token, 10)
  return String(token).length <= 2 ? 2000 + year : year
}

function isDateRawToken(value) {
  const str = String(value == null ? '' : value).trim()
  return str !== '' && DATE_RAW_RE.test(str)
}

function sanitizeDateRaw(value) {
  const str = String(value == null ? '' : value).trim()
  const m = str.match(DATE_RAW_RE)
  if (!m) return ''
  return `${m[1]}/${m[2]}/${m[3]}`
}

/** 段1=月、段2=日；段1不在 1–12 且段2是月则交换。 */
function buildFromDateRaw(dateRaw) {
  const str = String(dateRaw == null ? '' : dateRaw).trim()
  const m = str.match(DATE_RAW_RE)
  if (!m) return null
  const seg1 = parseInt(m[1], 10)
  const seg2 = parseInt(m[2], 10)
  const year = expandYear(m[3])
  if (seg1 >= 1 && seg1 <= 12 && seg2 >= 1 && seg2 <= 31) {
    return buildCanonical(year, seg1, seg2)
  }
  if ((seg1 < 1 || seg1 > 12) && seg2 >= 1 && seg2 <= 12 && seg1 >= 1 && seg1 <= 31) {
    return buildCanonical(year, seg2, seg1)
  }
  return null
}

function applyDateWithRaw(date, dateRaw) {
  const raw = String(dateRaw == null ? '' : dateRaw).trim()
  if (!raw || raw === '-' || raw === '???') {
    return normalizeDate(date)
  }
  const built = buildFromDateRaw(raw)
  return built || ''
}

module.exports = {
  normalizeDate,
  applyDateWithRaw,
  isDateRawToken,
  sanitizeDateRaw,
  buildFromDateRaw,
  isValidCanonicalDate,
  isDateFormatInvalid,
  swapDateMonthDay,
  canSwapDateMonthDay,
}
