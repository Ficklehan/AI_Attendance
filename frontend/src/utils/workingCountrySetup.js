import * as coreMod from '@shared/workingCountrySetupCore.cjs'
import { importSharedCjs } from './importSharedCjs'
import { getCachedWorkingCountry, isWorkingCountryConfigured, markWorkingCountryConfigured } from './countryHeader'

const core = importSharedCjs(coreMod)

export const normalizePersonalWorkingCountry = core.normalizePersonalWorkingCountry
export const hasPersonalWorkingCountry = core.hasPersonalWorkingCountry

export function needsWorkingCountrySetup(userInfo) {
  if (core.needsWorkingCountrySetup(userInfo)) {
    if (isWorkingCountryConfigured() && getCachedWorkingCountry() !== 'default') {
      return false
    }
    return true
  }
  return false
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
