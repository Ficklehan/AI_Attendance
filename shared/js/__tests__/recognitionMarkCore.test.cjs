const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  isAbsentByMark,
  markHasKind,
  reconcileAbsentMarkWithClocks,
  refreshNightShiftInSmartMark,
} = require('../recognitionMarkCore.cjs')

describe('isAbsentByMark', () => {
  it('does not infer absent from empty clocks when Mark is 正常', () => {
    assert.equal(isAbsentByMark({ Mark: '正常', ARRIVEE: '', DEPAR: '' }), false)
  })

  it('treats 模糊 as not absent even when clocks are empty', () => {
    assert.equal(isAbsentByMark({ Mark: '模糊', ARRIVEE: '', DEPAR: '' }), false)
    assert.equal(isAbsentByMark({ Mark: '模糊;未出勤', ARRIVEE: '', DEPAR: '' }), false)
  })

  it('keeps model 未出勤 when clocks are still empty', () => {
    assert.equal(isAbsentByMark({ Mark: '未出勤', ARRIVEE: '', DEPAR: '' }), true)
  })

  it('clears absent once corrected clocks are present', () => {
    assert.equal(isAbsentByMark({ Mark: '未出勤', ARRIVEE: '10:00', DEPAR: '19:00' }), false)
    assert.equal(isAbsentByMark({ SmartMark: '未出勤', ARRIVEE: '10:00', DEPAR: '19:00' }), false)
  })

  it('detects 缺卡 as missing punch, not absent', () => {
    assert.equal(markHasKind('缺卡', 'missingPunch'), true)
    assert.equal(markHasKind('到达缺位', 'missingPunch'), true)
    assert.equal(isAbsentByMark({ Mark: '缺卡', ARRIVEE: '', DEPAR: '14:00' }), false)
  })
})

describe('reconcileAbsentMarkWithClocks', () => {
  it('strips 未出勤 when both clocks are usable', () => {
    assert.equal(
      reconcileAbsentMarkWithClocks('未出勤', { ARRIVEE: '10:00', DEPAR: '19:00' }),
      '正常',
    )
  })

  it('keeps 未出勤 when clocks remain empty', () => {
    assert.equal(
      reconcileAbsentMarkWithClocks('未出勤', { ARRIVEE: '', DEPAR: '' }),
      '未出勤',
    )
  })

  it('refreshNightShift clears absent after corrected clocks', () => {
    const refreshed = refreshNightShiftInSmartMark('未出勤', {
      ARRIVEE: '10:00',
      DEPAR: '19:00',
      HORAIRES_DU_TRAVAIL: '10:00-19:00',
    })
    assert.equal(refreshed.includes('未出勤'), false)
    assert.equal(refreshed.includes('正常'), true)
  })
})
