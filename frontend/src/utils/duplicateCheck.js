/** 重名检测：纯逻辑来自 shared，行状态依赖 recordDisplay */
import { isAbsentRow } from './recordDisplay'
import * as coreMod from '@shared/duplicateCheckCore.cjs'
import { importSharedCjs } from './importSharedCjs'

const core = importSharedCjs(coreMod)

export const stripSerialSuffix = core.stripSerialSuffix
export const duplicateGroupKey = core.duplicateGroupKey
export const buildDuplicateMember = core.buildDuplicateMember
export const mergeDuplicateMembers = core.mergeDuplicateMembers
export const buildDuplicatePayload = core.buildDuplicatePayload
export const ensureRecordRowKeys = core.ensureRecordRowKeys

export function isEligibleForDuplicate(record) {
  return core.isEligibleForDuplicate(record, isAbsentRow)
}

export function applyDuplicateDecorations(records, remoteMetaMap, taskId) {
  return core.applyDuplicateDecorations(records, remoteMetaMap, taskId, isAbsentRow)
}
