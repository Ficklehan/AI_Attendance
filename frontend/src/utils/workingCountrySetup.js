import * as coreMod from '@shared/workingCountrySetupCore.cjs'
import { importSharedCjs } from './importSharedCjs'
import { markWorkingCountryConfigured } from './countryHeader'

const core = importSharedCjs(coreMod)

export const normalizePersonalWorkingCountry = core.normalizePersonalWorkingCountry
export const hasPersonalWorkingCountry = core.hasPersonalWorkingCountry

/** 用户在工作国家选择器中的选项（含 default），非后端解析后的有效国家 */
export function resolveSelectedWorkingCountry(userInfo) {
  const personal = normalizePersonalWorkingCountry(userInfo?.personalWorkingCountry)
  return personal || 'default'
}

/** 仅当账户工作国家为全局默认（未配置个人国家）时需要首次设置 */
export function needsWorkingCountrySetup(userInfo) {
  return core.needsWorkingCountrySetup(userInfo)
}

export function syncPersonalWorkingCountryOnUser(userInfo, countryCode) {
  if (!userInfo) return userInfo
  const personal = normalizePersonalWorkingCountry(countryCode)
  return {
    ...userInfo,
    personalWorkingCountry: personal,
    workingCountry: personal || userInfo.workingCountry,
  }
}

export function markUserWorkingCountryConfigured(countryCode) {
  markWorkingCountryConfigured()
  return normalizePersonalWorkingCountry(countryCode)
}
