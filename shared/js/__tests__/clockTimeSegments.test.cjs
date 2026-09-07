/**
 * Run: node --test shared/js/__tests__/clockTimeSegments.test.cjs
 */
const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  applyClockBackspace,
  applyClockDigit,
  commitClockDraft,
  draftFromClockValue,
  draftFromPastedText,
  emptyClockDraft,
  segmentText,
} = require('../clockTimeSegments.cjs')

describe('clockTimeSegments', () => {
  it('types hour then minute separately', () => {
    let draft = emptyClockDraft()
    draft = applyClockDigit(draft, '0')
    draft = applyClockDigit(draft, '9')
    assert.deepEqual(draft, { hour: '09', minute: '', segment: 'minute' })
    draft = applyClockDigit(draft, '3')
    draft = applyClockDigit(draft, '0')
    assert.deepEqual(draft, { hour: '09', minute: '30', segment: 'minute' })
    assert.equal(commitClockDraft(draft), '09:30')
  })

  it('auto-advances when first hour digit is 3-9', () => {
    const draft = applyClockDigit(emptyClockDraft(), '9')
    assert.deepEqual(draft, { hour: '09', minute: '', segment: 'minute' })
  })

  it('replaces a completed hour segment', () => {
    let draft = draftFromClockValue('09:00')
    draft = applyClockDigit(draft, '1')
    draft = applyClockDigit(draft, '6')
    assert.equal(draft.hour, '16')
    assert.equal(draft.minute, '00')
    assert.equal(draft.segment, 'minute')
  })

  it('backspace from empty minutes returns to hour', () => {
    let draft = { hour: '09', minute: '', segment: 'minute' }
    draft = applyClockBackspace(draft)
    assert.equal(draft.segment, 'hour')
    draft = applyClockBackspace(draft)
    assert.equal(draft.hour, '0')
  })

  it('commits a bare hour as HH:00', () => {
    assert.equal(commitClockDraft({ hour: '9', minute: '', segment: 'hour' }), '09:00')
    assert.equal(commitClockDraft(emptyClockDraft()), '')
  })

  it('fills both segments from a pasted datetime', () => {
    const pasted = draftFromPastedText('2026-09-03 08:30:00')
    assert.equal(pasted.clock, '08:30')
    assert.equal(pasted.hour, '08')
    assert.equal(pasted.minute, '30')
    assert.equal(draftFromPastedText('09:00').clock, '09:00')
    assert.equal(draftFromPastedText('8:30').clock, '08:30')
    assert.equal(draftFromPastedText('not-a-time'), null)
  })

  it('shows placeholder for empty segments', () => {
    assert.equal(segmentText(''), '--')
    assert.equal(segmentText('09'), '09')
  })
})
