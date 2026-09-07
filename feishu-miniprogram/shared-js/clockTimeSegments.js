/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
const { isCanonicalClockTime, normalizeClockTime } = require('./recognizedTimeNormalizer')

function emptyClockDraft() {
  return { hour: '', minute: '', segment: 'hour' }
}

function draftFromClockValue(value) {
  const raw = String(value ?? '').trim()
  if (isCanonicalClockTime(raw)) {
    return { hour: raw.slice(0, 2), minute: raw.slice(3, 5), segment: 'hour' }
  }
  return emptyClockDraft()
}

function segmentText(part, placeholder) {
  const mark = placeholder == null ? '--' : String(placeholder)
  if (part == null || part === '') return mark
  return String(part)
}

function applyHourDigit(draft, digit) {
  const n = Number(digit)
  if (draft.hour.length === 0 || draft.hour.length >= 2) {
    if (n >= 3) {
      return { hour: `0${digit}`, minute: draft.minute, segment: 'minute' }
    }
    return { hour: digit, minute: draft.minute, segment: 'hour' }
  }
  let hour = `${draft.hour}${digit}`
  if (parseInt(hour, 10) > 23) hour = '23'
  return { hour, minute: draft.minute, segment: 'minute' }
}

function applyMinuteDigit(draft, digit) {
  const n = Number(digit)
  if (draft.minute.length === 0 || draft.minute.length >= 2) {
    if (n >= 6) {
      return { hour: draft.hour, minute: `0${digit}`, segment: 'minute' }
    }
    return { hour: draft.hour, minute: digit, segment: 'minute' }
  }
  let minute = `${draft.minute}${digit}`
  if (parseInt(minute, 10) > 59) minute = '59'
  return { hour: draft.hour, minute, segment: 'minute' }
}

function applyClockDigit(draft, digitChar) {
  const current = draft && typeof draft === 'object' ? draft : emptyClockDraft()
  const digit = String(digitChar)
  if (!/^\d$/.test(digit)) return { ...current }
  if (current.segment === 'minute') {
    return applyMinuteDigit(current, digit)
  }
  return applyHourDigit(current, digit)
}

function applyClockBackspace(draft) {
  const current = draft && typeof draft === 'object' ? draft : emptyClockDraft()
  if (current.segment === 'minute') {
    if (current.minute) {
      return { ...current, minute: current.minute.slice(0, -1) }
    }
    return { ...current, segment: 'hour' }
  }
  if (current.hour) {
    return { ...current, hour: current.hour.slice(0, -1) }
  }
  return { ...current, segment: 'hour' }
}

function moveClockSegment(draft, segment) {
  const current = draft && typeof draft === 'object' ? draft : emptyClockDraft()
  return { ...current, segment: segment === 'minute' ? 'minute' : 'hour' }
}

function commitClockDraft(draft) {
  const current = draft && typeof draft === 'object' ? draft : emptyClockDraft()
  const hour = String(current.hour || '').replace(/\D/g, '')
  const minute = String(current.minute || '').replace(/\D/g, '')
  if (!hour && !minute) return ''
  const raw = minute ? `${hour}:${minute}` : hour
  const next = normalizeClockTime(raw)
  return next == null ? '' : String(next)
}

function draftFromPastedText(text) {
  const next = normalizeClockTime(text)
  if (!isCanonicalClockTime(next)) return null
  return {
    hour: next.slice(0, 2),
    minute: next.slice(3, 5),
    segment: 'minute',
    clock: next,
  }
}

function draftFromCanonicalClock(clock) {
  if (!isCanonicalClockTime(clock)) return null
  return {
    hour: clock.slice(0, 2),
    minute: clock.slice(3, 5),
    segment: 'minute',
    clock,
  }
}

module.exports = {
  emptyClockDraft,
  draftFromClockValue,
  segmentText,
  applyClockDigit,
  applyClockBackspace,
  moveClockSegment,
  commitClockDraft,
  draftFromPastedText,
  draftFromCanonicalClock,
}
