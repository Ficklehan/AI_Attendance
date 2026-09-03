/**
 * 日期归一化 → YYYY-MM-DD。
 * a/b/yyyy（或 . -）：默认按月/日/年；月不能 >12，若一侧 >12 则该侧为日、另一侧为月。
 * 例：12/06/2026→2026-12-06；13/06/2026→2026-06-13；06/13/2026→2026-06-13。
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

  let m = str.match(/^(\d{4})[/.-](\d{1,2})[/.-](\d{1,2})$/)
  if (m) {
    const built = buildCanonical(m[1], parseInt(m[2], 10), parseInt(m[3], 10))
    return built || str
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

module.exports = {
  normalizeDate,
  isValidCanonicalDate,
  isDateFormatInvalid,
}
