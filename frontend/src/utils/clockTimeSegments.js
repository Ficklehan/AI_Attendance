/** @see shared/js/clockTimeSegments.cjs */
import * as segmentMod from '@shared/clockTimeSegments.cjs'
import { importSharedCjs } from './importSharedCjs'

const segments = importSharedCjs(segmentMod)

export const emptyClockDraft = segments.emptyClockDraft
export const draftFromClockValue = segments.draftFromClockValue
export const segmentText = segments.segmentText
export const applyClockDigit = segments.applyClockDigit
export const applyClockBackspace = segments.applyClockBackspace
export const moveClockSegment = segments.moveClockSegment
export const commitClockDraft = segments.commitClockDraft
export const draftFromPastedText = segments.draftFromPastedText
export const draftFromCanonicalClock = segments.draftFromCanonicalClock
