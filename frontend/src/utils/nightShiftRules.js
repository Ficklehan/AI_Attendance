import request from '@/api/index'
import { normalizeCountryCode, resolveRecordCountry } from './countryCatalog'

export { resolveRecordCountry }

const DEFAULTS = {
  startTime: '20:00',
  endTime: '06:00',
  crossMidnight: true,
  useScheduleColumn: true,
}

const rulesByCountry = new Map()
const loadPromises = new Map()

function cacheKey(country) {
  return normalizeCountryCode(country)
}

function normalizeRules(raw) {
  const rules = { ...DEFAULTS, ...(raw || {}) }
  if (!rules.startTime) rules.startTime = DEFAULTS.startTime
  if (!rules.endTime) rules.endTime = DEFAULTS.endTime
  return rules
}

export function getNightShiftRules(country) {
  const key = cacheKey(country)
  const cached = rulesByCountry.get(key)
  if (cached) return { ...cached }
  const fallback = rulesByCountry.get('default')
  return { ...(fallback || DEFAULTS) }
}

export async function loadNightShiftRules(force = false, country = 'default') {
  const key = cacheKey(country)
  if (!force && loadPromises.has(key)) {
    return loadPromises.get(key)
  }
  const promise = request({
    url: '/config/runtime/night-shift',
    method: 'get',
    params: { country: key },
    silentError: true,
  })
    .then((res) => {
      const rules = normalizeRules(res.data)
      rulesByCountry.set(key, rules)
      if (key === 'default') {
        rulesByCountry.set('default', rules)
      }
      return rules
    })
    .catch(() => {
      const rules = { ...DEFAULTS }
      rulesByCountry.set(key, rules)
      return rules
    })
    .finally(() => {
      loadPromises.delete(key)
    })
  loadPromises.set(key, promise)
  return promise
}

export function setNightShiftRulesLocal(rules, country = 'default') {
  rulesByCountry.set(cacheKey(country), normalizeRules(rules))
}

export function setNightShiftAdminConfig(adminConfig) {
  if (!adminConfig) return
  setNightShiftRulesLocal(adminConfig, 'default')
  const overrides = adminConfig.byCountry || {}
  Object.keys(overrides).forEach((code) => {
    setNightShiftRulesLocal(overrides[code], code)
  })
}

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

function isNightShiftByTimes(arrive, depart, config) {
  const arriveMin = parseClockToMinutes(arrive)
  const departMin = parseClockToMinutes(depart)
  if (arriveMin < 0 || departMin < 0) return false
  const startMin = parseClockToMinutes(config.startTime) ?? 20 * 60
  const endMin = parseClockToMinutes(config.endTime) ?? 6 * 60
  if (arriveMin >= startMin) return true
  if (departMin < endMin) return true
  return config.crossMidnight && departMin < arriveMin
}

function isNightShiftBySchedule(shift, config) {
  if (!config.useScheduleColumn || shift == null || !String(shift).trim()) return false
  const match = String(shift).match(/(\d{1,2}:\d{2})\s*[-~–]\s*(\d{1,2}:\d{2})/)
  if (!match) return false
  const startMin = parseClockToMinutes(match[1])
  const endMin = parseClockToMinutes(match[2])
  if (startMin < 0 || endMin < 0) return false
  const ruleStartMin = parseClockToMinutes(config.startTime) ?? 20 * 60
  const ruleEndMin = parseClockToMinutes(config.endTime) ?? 6 * 60
  if (endMin < startMin) return true
  if (startMin >= ruleStartMin) return true
  return endMin < ruleEndMin
}

function hasUsableArriveAndDepart(arrive, depart) {
  return parseClockToMinutes(arrive) >= 0 && parseClockToMinutes(depart) >= 0
}

export function shouldMarkNightShift(record, rulesOrCountry, taskCountry) {
  if (!record || record.isDeleted) return false
  let config
  if (rulesOrCountry && typeof rulesOrCountry === 'object' && 'startTime' in rulesOrCountry) {
    config = normalizeRules(rulesOrCountry)
  } else {
    const country = resolveRecordCountry(record, rulesOrCountry || taskCountry)
    config = getNightShiftRules(country)
  }
  const arrive = record.ARRIVEE ?? record.arrival ?? ''
  const depart = record.DEPAR ?? record.departure ?? ''
  const shift = record.HORAIRES_DU_TRAVAIL ?? record.shift ?? ''
  if (hasUsableArriveAndDepart(arrive, depart)) {
    return isNightShiftByTimes(arrive, depart, config)
  }
  if (isNightShiftByTimes(arrive, depart, config)) {
    return true
  }
  return config.useScheduleColumn && isNightShiftBySchedule(shift, config)
}
