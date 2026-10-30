import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { boxesForPage, boxesForRecord, parseBbox } from './ocrBboxes.js'

describe('ocrBboxes', () => {
  it('parses xywh from Java records', () => {
    assert.deepEqual(parseBbox([410, 40, 70, 30]), { x: 410, y: 40, w: 70, h: 30 })
  })

  it('builds blur box for low confidence', () => {
    const boxes = boxesForRecord({
      Mark: '模糊',
      _arrivoBbox: [410, 40, 70, 30],
      _arrivoConfidence: 0.72,
    })
    assert.equal(boxes[0].tone, 'blur')
    assert.ok(boxes[0].label.includes('0.72'))
  })

  it('filters page and selected row', () => {
    const records = [
      { _pageIndex: 0, NO: '1', _arrivoBbox: [1, 1, 10, 10] },
      { _pageIndex: 1, NO: '2', _arrivoBbox: [2, 2, 10, 10] },
    ]
    assert.equal(boxesForPage(records, 1, null).length, 1)
    assert.equal(boxesForPage(records, 0, '1', (r) => r.NO)[0].x, 1)
  })
})
