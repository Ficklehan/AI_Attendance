const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const { isAbsentByMark, markHasKind } = require('../recognitionMarkCore.cjs')

describe('isAbsentByMark', () => {
  it('does not infer absent from empty clocks when Mark is 正常', () => {
    assert.equal(isAbsentByMark({ Mark: '正常', ARRIVEE: '', DEPAR: '' }), false)
  })

  it('treats 模糊 as not absent even when clocks are empty', () => {
    assert.equal(isAbsentByMark({ Mark: '模糊', ARRIVEE: '', DEPAR: '' }), false)
    assert.equal(isAbsentByMark({ Mark: '模糊;未出勤', ARRIVEE: '', DEPAR: '' }), false)
  })

  it('treats 未出勤 mark as absent even if times are filled', () => {
    assert.equal(isAbsentByMark({ Mark: '未出勤', ARRIVEE: '09:00', DEPAR: '14:00' }), true)
  })

  it('detects 缺卡 as missing punch, not absent', () => {
    assert.equal(markHasKind('缺卡', 'missingPunch'), true)
    assert.equal(markHasKind('到达缺位', 'missingPunch'), true)
    assert.equal(isAbsentByMark({ Mark: '缺卡', ARRIVEE: '', DEPAR: '14:00' }), false)
  })
})
