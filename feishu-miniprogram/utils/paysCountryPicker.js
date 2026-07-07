const { defaultPaysLabel, findCountry } = require('./countries')
const { getCountriesForPicker, translateCountryName, formatCountryLabel } = require('./i18n')
const { normalizeCountryCode, resolveCountryCodeFromPays } = require('./nightShiftCountry')
const { resolveRecordPaysSelectCode } = require('../shared-js/taskWorkRegionCore')

function getPaysCountryOptions() {
  return getCountriesForPicker().filter((item) => item.code && item.code !== 'default')
}

function getPaysCountryOptionsForRegion(taskWorkRegionCode) {
  const all = getPaysCountryOptions()
  const code = normalizeCountryCode(taskWorkRegionCode)
  if (!code || code === 'default') return all
  const matched = all.filter((item) => item.code === code)
  if (matched.length) return matched
  const local = findCountry(code)
  return [{
    code,
    name: translateCountryName(code, local && local.name),
    flag: (local && local.flag) || '',
  }]
}

function formatCountryOptionLabel(code, options) {
  if (!code) return ''
  const list = options || getPaysCountryOptions()
  const item = list.find((row) => row.code === code)
  if (!item) return formatCountryLabel(code, '', code)
  return formatCountryLabel(code, item.flag, item.name)
}

/** 与 PC / shared taskWorkRegionCore 一致 */
function resolvePaysSelectCode(draft, taskWorkRegionCode, isConfirmed) {
  return resolveRecordPaysSelectCode(draft, taskWorkRegionCode, isConfirmed)
}

/** 与 PC syncRecordPaysToTaskRegion：仅待核对任务同步 Pays */
function syncRecordPaysToTaskRegion(record, taskWorkRegionCode, isConfirmed, taskStatus) {
  const following = taskStatus === 'processed' || (!taskStatus && !isConfirmed)
  if (!record || isConfirmed || !following || !taskWorkRegionCode) return record
  const label = defaultPaysLabel(taskWorkRegionCode)
  if (!label) return record
  return { ...record, Pays: label }
}

/** 与 PC formatPaysFieldDisplay：表格 Pays 按当前界面语言展示 */
function formatPaysFieldDisplay(paysValue) {
  const raw = String(paysValue || '').trim()
  if (!raw) return ''
  const code = resolveCountryCodeFromPays(raw)
  if (code && code !== 'default') {
    return translateCountryName(code, raw)
  }
  return raw
}

module.exports = {
  getPaysCountryOptions,
  getPaysCountryOptionsForRegion,
  formatCountryOptionLabel,
  formatPaysFieldDisplay,
  resolvePaysSelectCode,
  syncRecordPaysToTaskRegion,
}
