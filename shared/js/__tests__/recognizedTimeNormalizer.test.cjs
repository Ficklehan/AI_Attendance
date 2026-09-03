/**
 * Run: node --test shared/js/__tests__/recognizedTimeNormalizer.test.cjs
 */
const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  normalizeClockTime,
  normalizeShiftSchedule,
} = require('../recognizedTimeNormalizer.cjs')
const { createRecordFieldFormatRules } = require('../recordFieldFormatRules.cjs')
const { isPlaceholderValue } = require('../fieldPlaceholder.cjs')
const { markContains } = require('../recognitionMarkCore.cjs')

describe('normalizeClockTime friendly input', () => {
  it('pads H:MM and accepts HH:MM', () => {
    assert.equal(normalizeClockTime('9:01'), '09:01')
    assert.equal(normalizeClockTime('09:01'), '09:01')
    assert.equal(normalizeClockTime('9:1'), '09:01')
    assert.equal(normalizeClockTime('14:00'), '14:00')
  })

  it('expands bare hour to HH:00', () => {
    assert.equal(normalizeClockTime('1'), '01:00')
    assert.equal(normalizeClockTime('9'), '09:00')
    assert.equal(normalizeClockTime('14'), '14:00')
  })

  it('keeps european styles', () => {
    assert.equal(normalizeClockTime('9h'), '09:00')
    assert.equal(normalizeClockTime('14H30'), '14:30')
  })

  it('extracts clock from pasted date-time', () => {
    assert.equal(normalizeClockTime('2026-09-03 08:30:00'), '08:30')
    assert.equal(normalizeClockTime('2026-09-03T08:30:00.000Z'), '08:30')
    assert.equal(normalizeClockTime('03/09/2026 8:30'), '08:30')
    assert.equal(normalizeClockTime('9/3/2026 8:30:00 PM'), '20:30')
    assert.equal(normalizeClockTime('08:30:00'), '08:30')
    assert.equal(normalizeClockTime('8:30 AM'), '08:30')
  })

  it('does not invent a clock from a date-only paste', () => {
    assert.equal(normalizeClockTime('2026-09-03'), '2026-09-03')
  })
})

describe('arrival/departure format accepts friendly input', () => {
  const api = createRecordFieldFormatRules({ isPlaceholderValue, markContains })

  it('accepts 9:01 and 1 after conceptual normalize', () => {
    assert.equal(api.isFieldFormatInvalid({ ARRIVEE: '9:01', DEPAR: '14:00' }, 'ARRIVEE'), false)
    assert.equal(api.isFieldFormatInvalid({ ARRIVEE: '1', DEPAR: '14:00' }, 'ARRIVEE'), false)
    assert.equal(api.isFieldFormatInvalid({ ARRIVEE: '09:00', DEPAR: '14:00+1' }, 'DEPAR'), true)
  })
})

describe('normalizeShiftSchedule', () => {
  it('pads both ends', () => {
    assert.equal(normalizeShiftSchedule('9:00-14:00'), '09:00-14:00')
    assert.equal(normalizeShiftSchedule('9-14'), '09:00-14:00')
  })
})
