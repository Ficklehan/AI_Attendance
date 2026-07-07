import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { resolveCountryDisplayLabel } from '@/utils/countryLabels'
import * as coreMod from '@shared/taskWorkRegionCore.cjs'
import { importSharedCjs } from './importSharedCjs'

const core = importSharedCjs(coreMod)

function coreCall(method, ...args) {
  const fn = core[method]
  if (typeof fn === 'function') return fn(...args)
  return undefined
}

function withWorkingCountry(userWorkingCountry) {
  return userWorkingCountry || getCachedWorkingCountry()
}

function resolveHistoricalCodeFallback(task, records) {
  return coreCall('resolveTaskSnapshotCountryCode', task)
    || coreCall('inferWorkRegionCodeFromRecords', records)
    || ''
}

export const normalizeCountryCode = (...args) => coreCall('normalizeCountryCode', ...args)
export const resolveCountryCodeFromPays = (...args) => coreCall('resolveCountryCodeFromPays', ...args)
export const resolveTaskSnapshotCountryCode = (...args) => coreCall('resolveTaskSnapshotCountryCode', ...args)
export const inferWorkRegionCodeFromRecords = (...args) => coreCall('inferWorkRegionCodeFromRecords', ...args)

export function resolveTaskWorkRegionHistoricalCode(task, records) {
  return coreCall('resolveTaskWorkRegionHistoricalCode', task, records) ?? resolveHistoricalCodeFallback(task, records)
}

export const resolveTaskWorkRegionHistoricalCountryCode = (...args) => coreCall('resolveTaskWorkRegionHistoricalCountryCode', ...args)
export const resolveTaskWorkRegionCode = (...args) => coreCall('resolveTaskWorkRegionCode', ...args)
export const resolveTaskWorkRegionCountryCode = (...args) => coreCall('resolveTaskWorkRegionCountryCode', ...args)
export const resolveTaskWorkRegionCodeForPending = (...args) => coreCall('resolveTaskWorkRegionCodeForPending', ...args)
export const resolveTaskWorkRegionPendingCountryCode = (...args) => coreCall('resolveTaskWorkRegionPendingCountryCode', ...args)
export const resolveTaskWorkRegionBindingCode = (...args) => coreCall('resolveTaskWorkRegionBindingCode', ...args)
export const resolveManualRecordCountryCode = (...args) => coreCall('resolveManualRecordCountryCode', ...args)
export const resolveRecordPaysSelectCode = (...args) => coreCall('resolveRecordPaysSelectCode', ...args)
export const isTaskCountryLocked = (...args) => coreCall('isTaskCountryLocked', ...args)
export const isTaskCountryFollowingWorking = (...args) => coreCall('isTaskCountryFollowingWorking', ...args)

export function resolveTaskNightShiftCountryCode(task, userWorkingCountry, records = [], isConfirmed = false) {
  return coreCall(
    'resolveTaskNightShiftCountryCode',
    task,
    withWorkingCountry(userWorkingCountry),
    records,
    isConfirmed,
  ) || ''
}

export function resolveUserWorkingCountryCode(userWorkingCountry) {
  return coreCall('resolveUserWorkingCountryCode', withWorkingCountry(userWorkingCountry))
}

export function resolveTaskWorkRegionBannerCode(task, userWorkingCountry, records = [], isConfirmed = false) {
  return coreCall(
    'resolveTaskWorkRegionBannerCode',
    task,
    withWorkingCountry(userWorkingCountry),
    records,
    isConfirmed,
  )
}

export function resolveTaskWorkRegionCodeForDisplay(task, userWorkingCountry, records = [], isConfirmed = false) {
  return coreCall(
    'resolveTaskWorkRegionCodeForDisplay',
    task,
    withWorkingCountry(userWorkingCountry),
    records,
    isConfirmed,
  )
}

export function resolveTaskWorkRegionDisplayCountryCode(task, userWorkingCountry, records = [], isConfirmed = false) {
  return coreCall(
    'resolveTaskWorkRegionDisplayCountryCode',
    task,
    withWorkingCountry(userWorkingCountry),
    records,
    isConfirmed,
  )
}

export function resolveTaskWorkRegionBannerLabel(task, options = [], userWorkingCountry, records = [], isConfirmed = false) {
  const code = resolveTaskWorkRegionBannerCode(task, userWorkingCountry, records, isConfirmed)
  if (!code) return ''
  return resolveCountryDisplayLabel(code, options)
}

export function resolveTaskWorkRegionLabel(task, options = [], userWorkingCountry, records = [], isConfirmed = false) {
  const code = resolveTaskWorkRegionDisplayCountryCode(task, userWorkingCountry, records, isConfirmed)
  if (!code) return ''
  return resolveCountryDisplayLabel(code, options)
}

export function resolveActiveTaskWorkRegionCode(task, userWorkingCountry, records = [], isConfirmed = false) {
  return resolveTaskWorkRegionBindingCode(
    task,
    withWorkingCountry(userWorkingCountry),
    records,
    isConfirmed,
  )
}
