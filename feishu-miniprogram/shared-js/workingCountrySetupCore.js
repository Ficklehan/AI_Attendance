/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 判断用户是否仍需选择个人工作国家（非全局默认）
 */

function normalizePersonalWorkingCountry(value) {
  if (value === undefined || value === null) return null
  const trimmed = String(value).trim()
  if (!trimmed || trimmed.toLowerCase() === 'default') return null
  return trimmed.toUpperCase()
}

function needsWorkingCountrySetup(userInfo) {
  if (!userInfo) return true
  const personal = userInfo.personalWorkingCountry
  if (personal !== undefined) {
    return !normalizePersonalWorkingCountry(personal)
  }
  return true
}

function hasPersonalWorkingCountry(userInfo) {
  return !needsWorkingCountrySetup(userInfo)
}

module.exports = {
  normalizePersonalWorkingCountry,
  needsWorkingCountrySetup,
  hasPersonalWorkingCountry,
}
