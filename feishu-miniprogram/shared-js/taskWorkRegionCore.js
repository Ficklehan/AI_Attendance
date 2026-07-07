/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 任务工作地区解析（PC / 小程序共用，无框架依赖）
 *
 * 规则摘要：
 * - 展示（横幅/顶部）：待核对优先用户当前工作地区；已确认仅用任务快照/行内数据
 * - 待核对 Pays 绑定：优先用户当前工作地区，切换后立即生效
 * - 已确认任务：仅任务创建快照或库内 Pays，不受当前工作地区影响
 */

const PAYS_COUNTRY_LABELS = {
  CN: 'China',
  FR: 'France',
  DE: 'Germany',
  US: 'United States',
  PL: 'Poland',
  NL: 'Netherlands',
  IT: 'Italy',
  ES: 'Spain',
  CZ: 'Czech Republic',
}

const LOCAL_COUNTRY_NAMES = {
  CN: '中国',
  FR: '法国',
  DE: '德国',
  US: '美国',
  PL: '波兰',
  NL: '荷兰',
  IT: '意大利',
  ES: '西班牙',
  CZ: '捷克',
}

const SUPPORTED_CODES = new Set(['default', ...Object.keys(PAYS_COUNTRY_LABELS)])

const LEGACY_ALIASES = {
  CHINA: 'CN',
  CHINE: 'CN',
  FRANCE: 'FR',
  FRANCIA: 'FR',
  GERMANY: 'DE',
  DEUTSCHLAND: 'DE',
  USA: 'US',
  'UNITED STATES': 'US',
  UNITEDSTATES: 'US',
  AMERICA: 'US',
  POLAND: 'PL',
  POLSKA: 'PL',
  NETHERLANDS: 'NL',
  NEDERLAND: 'NL',
  HOLLAND: 'NL',
  NL: 'NL',
  ITALY: 'IT',
  ITALIA: 'IT',
  ITALIE: 'IT',
  ITA: 'IT',
  SPAIN: 'ES',
  ESPANA: 'ES',
  ESPAGNE: 'ES',
  CZECH: 'CZ',
  CZECHIA: 'CZ',
  'CZECH REPUBLIC': 'CZ',
  CZECHREPUBLIC: 'CZ',
}

function normalizeCountryCode(country) {
  if (!country || !String(country).trim()) return 'default'
  const trimmed = String(country).trim()
  return trimmed.toLowerCase() === 'default' ? 'default' : trimmed.toUpperCase()
}

function resolveCountryCodeFromPays(pays) {
  if (!pays || !String(pays).trim()) return null
  const trimmed = String(pays).trim()
  const upper = trimmed.toUpperCase()
  if (SUPPORTED_CODES.has(upper)) return upper
  if (LEGACY_ALIASES[upper]) return LEGACY_ALIASES[upper]
  for (const code of Object.keys(PAYS_COUNTRY_LABELS)) {
    if (PAYS_COUNTRY_LABELS[code].toLowerCase() === trimmed.toLowerCase()) return code
  }
  for (const code of Object.keys(LOCAL_COUNTRY_NAMES)) {
    if (LOCAL_COUNTRY_NAMES[code] === trimmed) return code
  }
  return null
}

function resolveUserWorkingCountryCode(userWorkingCountry) {
  const userCode = normalizeCountryCode(userWorkingCountry)
  return userCode && userCode !== 'default' ? userCode : ''
}

/** 已完成：国家锁定为任务历史数据，不随当前工作国家变化 */
function isTaskCountryLocked(task) {
  return !!(task && task.status === 'confirmed')
}

/** 待核对：国家随当前工作国家变化 */
function isTaskCountryFollowingWorking(task) {
  return !!(task && task.status === 'processed')
}

function resolveTaskSnapshotCountryCode(task) {
  if (!task) return ''
  const code = normalizeCountryCode(task.promptCountry)
  return code && code !== 'default' ? code : ''
}

function inferWorkRegionCodeFromRecords(records) {
  const counts = new Map()
  for (const record of records || []) {
    if (!record || record.isDeleted || record.deleted) continue
    const code = resolveCountryCodeFromPays(record.Pays)
    if (!code || code === 'default') continue
    counts.set(code, (counts.get(code) || 0) + 1)
  }
  let bestCode = ''
  let bestCount = 0
  for (const [code, count] of counts) {
    if (count > bestCount) {
      bestCode = code
      bestCount = count
    }
  }
  return bestCode
}

/** 已确认任务历史地区：行内 Pays 众数 → 任务快照（不用当前工作地区） */
function resolveTaskWorkRegionHistoricalCode(task, records) {
  return inferWorkRegionCodeFromRecords(records)
    || resolveTaskSnapshotCountryCode(task)
    || ''
}

function resolveTaskWorkRegionHistoricalCountryCode(task, records) {
  const code = resolveTaskWorkRegionHistoricalCode(task, records)
  return code && code !== 'default' ? code : ''
}

/** 任务详情横幅推断：待核对可含用户工作地区；已完成仅历史 */
function resolveTaskWorkRegionBannerCode(task, userWorkingCountry, records, isConfirmed) {
  if (isConfirmed || isTaskCountryLocked(task)) {
    return resolveTaskWorkRegionHistoricalCode(task, records)
  }
  if (isTaskCountryFollowingWorking(task)) {
    return resolveTaskSnapshotCountryCode(task)
      || resolveUserWorkingCountryCode(userWorkingCountry)
      || inferWorkRegionCodeFromRecords(records)
      || ''
  }
  return resolveTaskWorkRegionHistoricalCode(task, records)
    || resolveTaskSnapshotCountryCode(task)
    || ''
}

/** 展示用：待核对优先用户工作地区；已完成仅历史 */
function resolveTaskWorkRegionCodeForDisplay(task, userWorkingCountry, records, isConfirmed) {
  if (isConfirmed || isTaskCountryLocked(task)) {
    return resolveTaskWorkRegionHistoricalCode(task, records) || 'default'
  }
  if (isTaskCountryFollowingWorking(task)) {
    return resolveUserWorkingCountryCode(userWorkingCountry)
      || resolveTaskSnapshotCountryCode(task)
      || 'default'
  }
  return resolveTaskWorkRegionHistoricalCode(task, records)
    || resolveTaskSnapshotCountryCode(task)
    || 'default'
}

function resolveTaskWorkRegionDisplayCountryCode(task, userWorkingCountry, records, isConfirmed) {
  const code = resolveTaskWorkRegionCodeForDisplay(task, userWorkingCountry, records, isConfirmed)
  return code && code !== 'default' ? code : ''
}

/** 待核对绑定回退：任务快照 → 用户工作地区 */
function resolveTaskWorkRegionCode(task, userWorkingCountry) {
  return resolveTaskSnapshotCountryCode(task)
    || resolveUserWorkingCountryCode(userWorkingCountry)
    || 'default'
}

function resolveTaskWorkRegionCountryCode(task, userWorkingCountry) {
  const code = resolveTaskWorkRegionCode(task, userWorkingCountry)
  return code && code !== 'default' ? code : ''
}

/** 待核对 Pays：用户工作地区 → 任务快照 */
function resolveTaskWorkRegionCodeForPending(task, userWorkingCountry) {
  return resolveUserWorkingCountryCode(userWorkingCountry)
    || resolveTaskSnapshotCountryCode(task)
    || 'default'
}

function resolveTaskWorkRegionPendingCountryCode(task, userWorkingCountry) {
  const code = resolveTaskWorkRegionCodeForPending(task, userWorkingCountry)
  return code && code !== 'default' ? code : ''
}

/**
 * TaskEdit / 结果页统一绑定码
 * - 待核对：当前工作国家优先
 * - 已完成：仅历史（快照/行内）
 * - 其他状态：快照/历史，不跟当前工作国家
 */
function resolveTaskWorkRegionBindingCode(task, userWorkingCountry, records, isConfirmed) {
  if (isConfirmed || isTaskCountryLocked(task)) {
    return resolveTaskWorkRegionHistoricalCode(task, records)
  }
  if (isTaskCountryFollowingWorking(task)) {
    const pending = resolveTaskWorkRegionPendingCountryCode(task, userWorkingCountry)
    if (pending) return pending
    return resolveTaskWorkRegionCountryCode(task, userWorkingCountry)
      || inferWorkRegionCodeFromRecords(records)
  }
  return resolveTaskWorkRegionHistoricalCode(task, records)
    || resolveTaskSnapshotCountryCode(task)
    || ''
}

/** 手动补行 / 添加考勤记录默认国家（仅待核对任务） */
function resolveManualRecordCountryCode(task, userWorkingCountry) {
  if (!isTaskCountryFollowingWorking(task)) {
    return resolveTaskWorkRegionHistoricalCountryCode(task, [])
      || resolveTaskSnapshotCountryCode(task)
      || ''
  }
  return resolveTaskWorkRegionPendingCountryCode(task, userWorkingCountry)
}

/** Pays 列展示码：待核对跟绑定地区；已完成仅行内 Pays / 历史 */
function resolveRecordPaysSelectCode(record, taskWorkRegionCode, isConfirmed) {
  const locked = isConfirmed
  if (locked) {
    const fromPays = resolveCountryCodeFromPays(record && record.Pays)
    if (fromPays && fromPays !== 'default') return fromPays
    const historical = taskWorkRegionCode ? normalizeCountryCode(taskWorkRegionCode) : ''
    if (historical && historical !== 'default') return historical
    return ''
  }
  if (taskWorkRegionCode) {
    return normalizeCountryCode(taskWorkRegionCode)
  }
  const fromPays = resolveCountryCodeFromPays(record && record.Pays)
  if (fromPays && fromPays !== 'default') return fromPays
  return ''
}

/** 夜班规则用国家：已完成仅历史，不回落到当前工作国家 */
function resolveTaskNightShiftCountryCode(task, userWorkingCountry, records, isConfirmed) {
  if (isConfirmed || isTaskCountryLocked(task)) {
    return resolveTaskWorkRegionHistoricalCode(task, records)
      || resolveTaskSnapshotCountryCode(task)
      || ''
  }
  if (isTaskCountryFollowingWorking(task)) {
    return resolveTaskWorkRegionBindingCode(task, userWorkingCountry, records, false)
      || resolveUserWorkingCountryCode(userWorkingCountry)
      || ''
  }
  return resolveTaskWorkRegionHistoricalCode(task, records)
    || resolveTaskSnapshotCountryCode(task)
    || ''
}

module.exports = {
  normalizeCountryCode,
  resolveCountryCodeFromPays,
  resolveUserWorkingCountryCode,
  isTaskCountryLocked,
  isTaskCountryFollowingWorking,
  resolveTaskSnapshotCountryCode,
  inferWorkRegionCodeFromRecords,
  resolveTaskWorkRegionHistoricalCode,
  resolveTaskWorkRegionHistoricalCountryCode,
  resolveTaskWorkRegionBannerCode,
  resolveTaskWorkRegionCodeForDisplay,
  resolveTaskWorkRegionDisplayCountryCode,
  resolveTaskWorkRegionCode,
  resolveTaskWorkRegionCountryCode,
  resolveTaskWorkRegionCodeForPending,
  resolveTaskWorkRegionPendingCountryCode,
  resolveTaskWorkRegionBindingCode,
  resolveManualRecordCountryCode,
  resolveRecordPaysSelectCode,
  resolveTaskNightShiftCountryCode,
}
