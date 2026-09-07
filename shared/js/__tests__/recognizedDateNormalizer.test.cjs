/**
 * Run: node --test shared/js/__tests__/recognizedDateNormalizer.test.cjs
 */
const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  canSwapDateMonthDay,
  applyDateWithRaw,
  normalizeDate,
  swapDateMonthDay,
} = require('../recognizedDateNormalizer.cjs')

describe('applyDateWithRaw', () => {
  it('rebuilds Date from left-to-right segments as month/day', () => {
    assert.equal(applyDateWithRaw('2026-02-07', '07/02/26'), '2026-07-02')
    assert.equal(applyDateWithRaw('2026-01-09', '09/01/2026'), '2026-09-01')
    assert.equal(applyDateWithRaw('2026-09-03', '09/03/2026'), '2026-09-03')
  })

  it('swaps when segment1 is not a month', () => {
    assert.equal(applyDateWithRaw('2026-25-12', '25/12/2026'), '2026-12-25')
  })

  it('falls back to normalizeDate when DATE_RAW is empty', () => {
    assert.equal(applyDateWithRaw('2026-02-07', ''), '2026-02-07')
    assert.equal(applyDateWithRaw('2026-25-12', ''), '2026-12-25')
  })
})

describe('normalizeDate', () => {
  it('keeps legal ISO month/day', () => {
    assert.equal(normalizeDate('2026-09-03'), '2026-09-03')
    assert.equal(normalizeDate('2026-03-09'), '2026-03-09')
    assert.equal(normalizeDate('2026-02-07'), '2026-02-07')
  })

  it('swaps ISO when month is illegal and day is a month', () => {
    assert.equal(normalizeDate('2026-25-12'), '2026-12-25')
    assert.equal(normalizeDate('2026-13-05'), '2026-05-13')
    assert.equal(normalizeDate('2026/25/12'), '2026-12-25')
  })

  it('empties ISO when neither side is a legal month', () => {
    assert.equal(normalizeDate('2026-32-15'), '')
    assert.equal(normalizeDate('2026-32-11'), '')
  })
})

describe('swapDateMonthDay', () => {
  it('swaps ambiguous month/day', () => {
    assert.equal(swapDateMonthDay('2026-02-09'), '2026-09-02')
    assert.equal(swapDateMonthDay('2026-09-02'), '2026-02-09')
    assert.equal(canSwapDateMonthDay('2026-02-09'), true)
  })

  it('returns null when swap is invalid or a no-op', () => {
    assert.equal(swapDateMonthDay('2026-02-15'), null)
    assert.equal(swapDateMonthDay('2026-05-05'), null)
    assert.equal(swapDateMonthDay('not-a-date'), null)
    assert.equal(swapDateMonthDay(''), null)
    assert.equal(canSwapDateMonthDay('2026-02-15'), false)
  })
})
