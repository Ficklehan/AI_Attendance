const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  findNextEditableTimeCell,
  parseTimeGridClipboard,
  stepTimeCell,
} = require('../timeGridEdit.cjs')

describe('timeGridEdit', () => {
  it('steps Tab from ARRIVEE to DEPAR then next row ARRIVEE', () => {
    assert.deepEqual(stepTimeCell(undefined, 'ARRIVEE', 'next'), { field: 'DEPAR', rowDelta: 0 })
    assert.deepEqual(stepTimeCell(undefined, 'DEPAR', 'next'), { field: 'ARRIVEE', rowDelta: 1 })
    assert.deepEqual(stepTimeCell(undefined, 'ARRIVEE', 'prev'), { field: 'DEPAR', rowDelta: -1 })
    assert.deepEqual(stepTimeCell(undefined, 'ARRIVEE', 'down'), { field: 'ARRIVEE', rowDelta: 1 })
  })

  it('parses Excel TSV clocks', () => {
    assert.deepEqual(parseTimeGridClipboard('10:00\t19:00\n10:00\t19:00\n'), [
      ['10:00', '19:00'],
      ['10:00', '19:00'],
    ])
    assert.deepEqual(parseTimeGridClipboard('0930'), [['09:30']])
  })

  it('skips locked rows when finding the next cell', () => {
    const rows = [
      { id: 1 },
      { id: 2, locked: true },
      { id: 3 },
    ]
    const editable = (row) => !row.locked
    const next = findNextEditableTimeCell(rows, 0, 'DEPAR', 'next', editable)
    assert.equal(next.record.id, 3)
    assert.equal(next.field, 'ARRIVEE')
  })
})
