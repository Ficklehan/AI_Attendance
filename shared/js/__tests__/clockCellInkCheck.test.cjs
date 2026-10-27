const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  shouldInspect,
  hasClockInkFromImageData,
  applyAbsentInkReviewOnSheet,
  groupRecordsBySheet,
} = require('../clockCellInkCheck.cjs')

describe('clockCellInkCheck', () => {
  it('inspects empty-clock absent rows only', () => {
    assert.equal(shouldInspect({ Mark: '未出勤', ARRIVEE: '', DEPAR: '' }), true)
    assert.equal(shouldInspect({ Mark: '模糊', ARRIVEE: '', DEPAR: '' }), false)
    assert.equal(shouldInspect({ Mark: '未出勤', ARRIVEE: '09:00', DEPAR: '14:00' }), false)
  })

  it('blank white band is not ink; dark scribble is', () => {
    const blank = makeSheet(400, 300, false)
    assert.equal(hasClockInkFromImageData(blank, 400, 300, 1, 2), false)
    const inked = makeSheet(400, 300, true)
    assert.equal(hasClockInkFromImageData(inked, 400, 300, 1, 2), true)
  })

  it('overrides 未出勤 to 模糊 when clock band has ink', () => {
    const rows = [
      { NOM_PRENOM: 'JOSEPH', Entrepot: 'MARLY', Date: '2026-09-21', ARRIVEE: '', DEPAR: '', Mark: '未出勤', SmartMark: '未出勤' },
      { NOM_PRENOM: 'THIAM', Entrepot: 'MARLY', Date: '2026-09-21', ARRIVEE: '', DEPAR: '', Mark: '未出勤', SmartMark: '未出勤' },
    ]
    const changed = applyAbsentInkReviewOnSheet(rows, makeSheet(400, 300, true), 0)
    assert.equal(changed, 1)
    assert.equal(rows[0].Mark, '未出勤')
    assert.equal(rows[1].Mark, '模糊')
    assert.equal(rows[1]._reviewReason, 'clock_ink')
  })

  it('groups consecutive sheets by warehouse and date', () => {
    const groups = groupRecordsBySheet([
      { Entrepot: 'MARLY', Date: '2026-09-21' },
      { Entrepot: 'MARLY', Date: '2026-09-21' },
      { Entrepot: 'AMS', Date: '2026-10-05' },
    ])
    assert.equal(groups.length, 2)
    assert.equal(groups[0].length, 2)
    assert.equal(groups[1].length, 1)
  })
})

function makeSheet(width, height, withInkOnSecondRow) {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < data.length; i += 4) {
    data[i] = 255
    data[i + 1] = 255
    data[i + 2] = 255
    data[i + 3] = 255
  }
  if (withInkOnSecondRow) {
    const tableTop = Math.floor(height * 0.30)
    const tableBottom = Math.floor(height * 0.98)
    const x0 = Math.floor(width * 0.48)
    const band = Math.floor((tableBottom - tableTop) / 3)
    const y0 = tableTop + band * 2
    for (let y = y0; y < y0 + 20; y++) {
      for (let x = x0; x < x0 + 80; x++) {
        const i = (y * width + x) * 4
        data[i] = 20
        data[i + 1] = 20
        data[i + 2] = 20
      }
    }
  }
  return { width, height, data }
}
