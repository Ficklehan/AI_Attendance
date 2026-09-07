/**
 * Run: node --test shared/js/__tests__/recognitionEvents.test.cjs
 */
const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  applyRecognitionEvents,
  emptyRecognitionState,
  isRecognitionTerminal,
  toLiveRowPreview,
  summarizeLiveRows,
} = require('../recognitionEvents.cjs')

describe('recognitionEvents', () => {
  it('ignores incremental record events so historical rows cannot stack', () => {
    let state = emptyRecognitionState()
    state = applyRecognitionEvents(state, [
      { seq: 1, eventType: 'status', payload: { status: 'processing' } },
      { seq: 2, eventType: 'record', payload: { NOM_PRENOM: 'Ada', ARRIVEE: '08:00' } },
      { seq: 2, eventType: 'record', payload: { NOM_PRENOM: 'Dup' } },
      { seq: 3, eventType: 'record', payload: { NOM_PRENOM: 'Bob', DEPAR: '17:00' } },
    ])
    assert.equal(state.lastSeq, 3)
    assert.equal(state.status, 'processing')
    assert.equal(state.records.length, 0)
  })

  it('run_start can restore a resume baseline without stacking record events', () => {
    const state = applyRecognitionEvents(emptyRecognitionState(), [
      {
        seq: 1,
        eventType: 'run_start',
        payload: { status: 'processing', records: [{ NOM_PRENOM: 'Ada' }, { NOM_PRENOM: 'Bob' }] },
      },
      { seq: 2, eventType: 'record', payload: { NOM_PRENOM: 'Dup' } },
    ])
    assert.equal(state.records.length, 2)
    assert.equal(state.records[1].NOM_PRENOM, 'Bob')
  })

  it('run_start clears previous rows so a new recognition does not stack', () => {
    let state = applyRecognitionEvents(emptyRecognitionState(), [
      { seq: 1, eventType: 'page_done', payload: { imageIndex: 0, records: [{ NOM_PRENOM: 'Old' }, { NOM_PRENOM: 'Older' }] } },
    ])
    state = applyRecognitionEvents(state, [
      { seq: 1, eventType: 'run_start', payload: { status: 'processing' } },
      { seq: 2, eventType: 'page_done', payload: { imageIndex: 0, records: [{ NOM_PRENOM: 'Ada' }] } },
    ])
    assert.equal(state.records.length, 1)
    assert.equal(state.records[0].NOM_PRENOM, 'Ada')
    assert.equal(state.status, 'processing')
  })

  it('page_done replaces that image slice and complete uses official records', () => {
    let state = applyRecognitionEvents(emptyRecognitionState(), [
      { seq: 1, eventType: 'record', payload: { NOM_PRENOM: 'Junk' }, imageIndex: 0 },
      { seq: 2, eventType: 'record', payload: { NOM_PRENOM: 'Ada' }, imageIndex: 0 },
      {
        seq: 3,
        eventType: 'page_done',
        payload: { imageIndex: 0, records: [{ NOM_PRENOM: 'Ada' }] },
      },
    ])
    assert.equal(state.records.length, 1)
    assert.equal(state.records[0].NOM_PRENOM, 'Ada')

    state = applyRecognitionEvents(state, [
      {
        seq: 4,
        eventType: 'complete',
        payload: { status: 'processed', records: [{ NOM_PRENOM: 'Ada' }, { NOM_PRENOM: 'Bob' }] },
      },
    ])
    assert.equal(state.complete, true)
    assert.equal(state.records.length, 2)
    assert.equal(state.records[1].NOM_PRENOM, 'Bob')
  })

  it('marks complete and error without dropping published rows', () => {
    let state = applyRecognitionEvents(emptyRecognitionState(), [
      { seq: 1, eventType: 'page_done', payload: { imageIndex: 0, records: [{ NOM_PRENOM: 'Ada' }] } },
      { seq: 2, eventType: 'error', payload: { message: 'boom' } },
    ])
    assert.equal(state.status, 'failed')
    assert.equal(state.records.length, 1)
    assert.equal(state.error.message, 'boom')

    state = applyRecognitionEvents(emptyRecognitionState(), [
      { seq: 1, eventType: 'page_done', payload: { imageIndex: 0, records: [{ NOM_PRENOM: 'Ada' }] } },
      { seq: 2, eventType: 'complete', payload: { recordCount: 1, status: 'processed' } },
    ])
    assert.equal(state.complete, true)
    assert.equal(state.status, 'processed')
    assert.equal(isRecognitionTerminal(state.status), true)
  })

  it('maps preview fields for the mini-program list', () => {
    assert.deepEqual(toLiveRowPreview({
      NOM_PRENOM: 'Ada',
      HORAIRES_DU_TRAVAIL: '08-17',
      ARRIVEE: '08:01',
      DEPAR: '17:02',
      SmartMark: '正常',
    }), {
      name: 'Ada',
      shift: '08-17',
      arrive: '08:01',
      leave: '17:02',
      note: '',
      needsConfirm: false,
    })

    assert.deepEqual(toLiveRowPreview({
      NOM_PRENOM: 'Bob',
      HORAIRES_DU_TRAVAIL: '08-17',
      ARRIVEE: '08:10',
      DEPAR: '17:00',
      SmartMark: '模糊;已签字',
    }), {
      name: 'Bob',
      shift: '08-17',
      arrive: '08:10',
      leave: '17:00',
      note: '模糊',
      needsConfirm: true,
    })

    assert.deepEqual(
      summarizeLiveRows([
        { needsConfirm: false },
        { needsConfirm: true },
        { needsConfirm: true },
      ]),
      { total: 3, pending: 2 },
    )
  })
})
