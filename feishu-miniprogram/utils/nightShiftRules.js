const { isApiSuccess, getApiData } = require('./response')

const DEFAULTS = {
  startTime: '20:00',
  endTime: '06:00',
  crossMidnight: true,
  useScheduleColumn: true,
}

let cachedRules = { ...DEFAULTS }
let loadPromise = null

function parseClockToMinutes(timeStr) {
  if (timeStr == null) return -1
  const str = String(timeStr).trim()
  if (!str || str === '???' || /^illegible$/i.test(str)) return -1
  const match = str.match(/^(\d{1,2}):(\d{2})$/)
  if (!match) return -1
  const hour = parseInt(match[1], 10)
  const minute = parseInt(match[2], 10)
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1
  return hour * 60 + minute
}

function normalizeRules(raw) {
  const rules = { ...DEFAULTS, ...(raw || {}) }
  if (!rules.startTime) rules.startTime = DEFAULTS.startTime
  if (!rules.endTime) rules.endTime = DEFAULTS.endTime
  return rules
}

function getNightShiftRules() {
  return { ...cachedRules }
}

function loadNightShiftRules(force) {
  if (!force && loadPromise) return loadPromise
  const { apiCall } = require('./request')
  loadPromise = apiCall({ url: '/config/runtime/night-shift', timeout: 15000 })
    .then((res) => {
      if (isApiSuccess(res.data)) {
        cachedRules = normalizeRules(getApiData(res.data))
      } else {
        cachedRules = { ...DEFAULTS }
      }
      return cachedRules
    })
    .catch(() => {
      cachedRules = { ...DEFAULTS }
      return cachedRules
    })
    .finally(() => {
      loadPromise = null
    })
  return loadPromise
}

function isNightShiftByTimes(arrive, depart, config) {
  const arriveMin = parseClockToMinutes(arrive)
  const departMin = parseClockToMinutes(depart)
  if (arriveMin < 0 || departMin < 0) return false
  const startMin = parseClockToMinutes(config.startTime)
  const endMin = parseClockToMinutes(config.endTime)
  const ruleStart = startMin >= 0 ? startMin : 20 * 60
  const ruleEnd = endMin >= 0 ? endMin : 6 * 60
  if (arriveMin >= ruleStart) return true
  if (departMin < ruleEnd) return true
  return config.crossMidnight && departMin < arriveMin
}

function isNightShiftBySchedule(shift, config) {
  if (!config.useScheduleColumn || shift == null || !String(shift).trim()) return false
  const match = String(shift).match(/(\d{1,2}:\d{2})\s*[-~–]\s*(\d{1,2}:\d{2})/)
  if (!match) return false
  const startMin = parseClockToMinutes(match[1])
  const endMin = parseClockToMinutes(match[2])
  if (startMin < 0 || endMin < 0) return false
  const ruleStartMin = parseClockToMinutes(config.startTime)
  const ruleEndMin = parseClockToMinutes(config.endTime)
  const rs = ruleStartMin >= 0 ? ruleStartMin : 20 * 60
  const re = ruleEndMin >= 0 ? ruleEndMin : 6 * 60
  if (endMin < startMin) return true
  if (startMin >= rs) return true
  return endMin < re
}

function hasUsableArriveAndDepart(arrive, depart) {
  return parseClockToMinutes(arrive) >= 0 && parseClockToMinutes(depart) >= 0
}

function shouldMarkNightShift(record, rules) {
  if (!record || record.isDeleted) return false
  const config = normalizeRules(rules || cachedRules)
  const arrive = record.ARRIVEE ?? record.arrival ?? ''
  const depart = record.DEPAR ?? record.departure ?? ''
  const shift = record.HORAIRES_DU_TRAVAIL ?? record.shift ?? ''
  if (hasUsableArriveAndDepart(arrive, depart)) {
    return isNightShiftByTimes(arrive, depart, config)
  }
  if (isNightShiftByTimes(arrive, depart, config)) return true
  return config.useScheduleColumn && isNightShiftBySchedule(shift, config)
}

module.exports = {
  getNightShiftRules,
  loadNightShiftRules,
  shouldMarkNightShift,
}
