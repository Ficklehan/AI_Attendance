/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 日期归一化：明确格式 → YYYY-MM-DD；歧义如 03/04/2026 保留原样；无法解析保留原样（提交时拦截）。
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

function tryDmyOrMdy(a, b, year) {
  const p = parseInt(a, 10)
  const q = parseInt(b, 10)
  if (p > 12 && q <= 12) {
    return buildCanonical(year, q, p)
  }
  if (q > 12 && p <= 12) {
    return buildCanonical(year, p, q)
  }
  if (p <= 12 && q <= 12) {
    return null
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

  let m = str.match(/^(\d{4})[/.-](\d{1,2})[/.-](\d{1,2})$/)
  if (m) {
    const built = buildCanonical(m[1], parseInt(m[2], 10), parseInt(m[3], 10))
    return built || str
  }

  m = str.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/)
  if (m) {
    const built = tryDmyOrMdy(m[1], m[2], parseInt(m[3], 10))
    return built || str
  }

  m = str.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2})$/)
  if (m) {
    const built = tryDmyOrMdy(m[1], m[2], 2000 + parseInt(m[3], 10))
    return built || str
  }

  return str
}

function isDateFormatInvalid(value) {
  const str = String(value || '').trim()
  if (!str || str === '-' || str === '???') return false
  return !isValidCanonicalDate(str)
}

module.exports = {
  normalizeDate,
  isValidCanonicalDate,
  isDateFormatInvalid,
}
