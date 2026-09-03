/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 班次拆分与到离比对：早到 / 迟到 / 早退 / 晚离开（分钟）。
 * 班次标准形：09:00-14:00
 */

function parseClockToMinutes(timeStr) {
  if (timeStr == null) return -1
  const str = String(timeStr).trim()
  if (!str || str === '???' || str === '??' || /^illegible$/i.test(str)) return -1
  let match = str.match(/^(\d{1,2}):(\d{2})$/)
  if (match) {
    const h = parseInt(match[1], 10)
    const m = parseInt(match[2], 10)
    if (h > 23 || m > 59) return -1
    return h * 60 + m
  }
  match = str.match(/^(\d{1,2})[hH](\d{2})?$/)
  if (match) {
    const h = parseInt(match[1], 10)
    const m = match[2] ? parseInt(match[2], 10) : 0
    if (h > 23 || m > 59) return -1
    return h * 60 + m
  }
  return -1
}

function formatMinutesClock(totalMinutes) {
  const day = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
  const h = Math.floor(day / 60)
  const m = day % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/**
 * @returns {{ shiftStart: string, shiftEnd: string, startMinutes: number, endMinutes: number }|null}
 */
function parseShiftSchedule(raw) {
  if (raw == null || !String(raw).trim()) return null
  const str = String(raw).trim()
  const match = str.match(/(\d{1,2}:\d{2}|\d{1,2}[hH]\d{0,2})\s*[-~–—]\s*(\d{1,2}:\d{2}|\d{1,2}[hH]\d{0,2})/)
  if (!match) return null
  const startMinutes = parseClockToMinutes(match[1])
  const endMinutes = parseClockToMinutes(match[2])
  if (startMinutes < 0 || endMinutes < 0) return null
  return {
    shiftStart: formatMinutesClock(startMinutes),
    shiftEnd: formatMinutesClock(endMinutes),
    startMinutes,
    endMinutes,
  }
}

/**
 * @returns {{
 *   earlyArrivalMin: number,
 *   lateArrivalMin: number,
 *   earlyLeaveMin: number,
 *   overtimeMin: number
 * }}
 */
function computeShiftVarianceMinutes(record) {
  const empty = {
    earlyArrivalMin: 0,
    lateArrivalMin: 0,
    earlyLeaveMin: 0,
    overtimeMin: 0,
  }
  if (!record || record.isDeleted) return empty
  const shift = parseShiftSchedule(record.HORAIRES_DU_TRAVAIL ?? record.shift ?? '')
  if (!shift) return empty

  const arriveMin = parseClockToMinutes(record.ARRIVEE ?? record.arrival ?? '')
  const departMinRaw = parseClockToMinutes(record.DEPAR ?? record.DEPART ?? record.departure ?? '')

  let earlyArrivalMin = 0
  let lateArrivalMin = 0
  if (arriveMin >= 0) {
    const delta = arriveMin - shift.startMinutes
    if (delta < 0) earlyArrivalMin = -delta
    else if (delta > 0) lateArrivalMin = delta
  }

  let earlyLeaveMin = 0
  let overtimeMin = 0
  if (departMinRaw >= 0) {
    let departMin = departMinRaw
    let endMin = shift.endMinutes
    // 跨日班次：下班时刻小于上班时刻，离开若落在次日需 +24h 对齐
    if (endMin < shift.startMinutes) {
      endMin += 24 * 60
      if (departMin < shift.startMinutes) {
        departMin += 24 * 60
      }
    } else if (departMin < shift.startMinutes - 12 * 60) {
      // 白天班偶发跨日离开
      departMin += 24 * 60
    }
    const delta = departMin - endMin
    if (delta < 0) earlyLeaveMin = -delta
    else if (delta > 0) overtimeMin = delta
  }

  return { earlyArrivalMin, lateArrivalMin, earlyLeaveMin, overtimeMin }
}

/**
 * @param {number} totalMinutes
 * @returns {{ total: number, hours: number, minutes: number }}
 */
function splitDurationMinutes(totalMinutes) {
  const total = Math.max(0, Math.floor(Number(totalMinutes) || 0))
  return {
    total,
    hours: Math.floor(total / 60),
    minutes: total % 60,
  }
}

/**
 * 时长展示：不足 1 小时 →「N min」；整点小时 →「N h」；否则「N h M min」
 */
function formatDurationZh(totalMinutes) {
  const { total, hours, minutes } = splitDurationMinutes(totalMinutes)
  if (total < 60) return `${total} min`
  if (minutes === 0) return `${hours} h`
  return `${hours} h ${minutes} min`
}

/**
 * @param {object} variance computeShiftVarianceMinutes 结果
 * @param {(key: string, totalMinutes: number) => string} formatPhrase
 *   key: earlyArrival | lateArrival | earlyLeave | overtime
 */
function formatShiftVariancePhrases(variance, formatPhrase) {
  if (!variance || typeof formatPhrase !== 'function') return []
  const phrases = []
  if (variance.earlyArrivalMin > 0) {
    phrases.push(formatPhrase('earlyArrival', variance.earlyArrivalMin))
  }
  if (variance.lateArrivalMin > 0) {
    phrases.push(formatPhrase('lateArrival', variance.lateArrivalMin))
  }
  if (variance.earlyLeaveMin > 0) {
    phrases.push(formatPhrase('earlyLeave', variance.earlyLeaveMin))
  }
  if (variance.overtimeMin > 0) {
    phrases.push(formatPhrase('overtime', variance.overtimeMin))
  }
  return phrases
}

/** 无 i18n 时的中文默认句式（单测 / 后端日志） */
function formatShiftVarianceZh(variance) {
  return formatShiftVariancePhrases(variance, (key, minutes) => {
    const duration = formatDurationZh(minutes)
    if (key === 'earlyArrival') return `早到 ${duration}`
    if (key === 'lateArrival') return `迟到 ${duration}`
    if (key === 'earlyLeave') return `早离开 ${duration}`
    if (key === 'overtime') return `晚离开 ${duration}`
    return `${key} ${duration}`
  })
}

/**
 * 多条班次比对合并为一句：员工早到 x 分钟且晚离开 y 分钟
 * @param {string[]} phrases
 * @param {{ prefix?: string, join?: string }} [opts]
 */
function joinShiftVarianceSentence(phrases, opts) {
  const list = Array.isArray(phrases) ? phrases.filter(Boolean) : []
  if (!list.length) return ''
  const prefix = opts && opts.prefix != null ? String(opts.prefix) : '员工'
  const join = opts && opts.join != null ? String(opts.join) : '且'
  return `${prefix}${list.join(join)}`
}

function formatShiftVarianceSentenceZh(variance) {
  return joinShiftVarianceSentence(formatShiftVarianceZh(variance))
}

module.exports = {
  parseClockToMinutes,
  parseShiftSchedule,
  computeShiftVarianceMinutes,
  splitDurationMinutes,
  formatDurationZh,
  formatShiftVariancePhrases,
  formatShiftVarianceZh,
  joinShiftVarianceSentence,
  formatShiftVarianceSentenceZh,
}
