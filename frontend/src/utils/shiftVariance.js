/** @see shared/js/shiftVarianceCore.cjs */
import * as mod from '@shared/shiftVarianceCore.cjs'
import { importSharedCjs } from './importSharedCjs'

const api = importSharedCjs(mod) || {}

function fallbackSplitDurationMinutes(totalMinutes) {
  const total = Math.max(0, Math.floor(Number(totalMinutes) || 0))
  return { total, hours: Math.floor(total / 60), minutes: total % 60 }
}

export const parseClockToMinutes = api.parseClockToMinutes
export const parseShiftSchedule = api.parseShiftSchedule
export const computeShiftVarianceMinutes = typeof api.computeShiftVarianceMinutes === 'function'
  ? api.computeShiftVarianceMinutes
  : () => ({ earlyArrivalMin: 0, lateArrivalMin: 0, earlyLeaveMin: 0, overtimeMin: 0 })
export const splitDurationMinutes = typeof api.splitDurationMinutes === 'function'
  ? api.splitDurationMinutes
  : fallbackSplitDurationMinutes
export const formatDurationZh = typeof api.formatDurationZh === 'function'
  ? api.formatDurationZh
  : (totalMinutes) => {
    const { total, hours, minutes } = fallbackSplitDurationMinutes(totalMinutes)
    if (total < 60) return `${total} min`
    if (minutes === 0) return `${hours} h`
    return `${hours} h ${minutes} min`
  }
export const formatShiftVariancePhrases = typeof api.formatShiftVariancePhrases === 'function'
  ? api.formatShiftVariancePhrases
  : () => []
export const formatShiftVarianceZh = typeof api.formatShiftVarianceZh === 'function'
  ? api.formatShiftVarianceZh
  : () => []
export const joinShiftVarianceSentence = typeof api.joinShiftVarianceSentence === 'function'
  ? api.joinShiftVarianceSentence
  : (phrases, opts) => {
    const list = Array.isArray(phrases) ? phrases.filter(Boolean) : []
    if (!list.length) return ''
    const prefix = opts && opts.prefix != null ? String(opts.prefix) : '员工'
    const join = opts && opts.join != null ? String(opts.join) : '且'
    return `${prefix}${list.join(join)}`
  }
export const formatShiftVarianceSentenceZh = typeof api.formatShiftVarianceSentenceZh === 'function'
  ? api.formatShiftVarianceSentenceZh
  : () => ''
