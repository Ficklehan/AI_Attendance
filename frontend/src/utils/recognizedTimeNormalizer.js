/** @see shared/js/recognizedTimeNormalizer.cjs */
import * as timeMod from '@shared/recognizedTimeNormalizer.cjs'
import { importSharedCjs } from './importSharedCjs'

const time = importSharedCjs(timeMod)

export const normalizeClockTime = time.normalizeClockTime
export const normalizeShiftSchedule = time.normalizeShiftSchedule
export const extractTimeTokenStrings = time.extractTimeTokenStrings
export const isNonTimeFieldLabel = time.isNonTimeFieldLabel
