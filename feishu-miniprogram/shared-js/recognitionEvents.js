/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
const {
  splitSmartMarkParts,
  markContains,
  isSignatureResultMark,
} = require('./recognitionMarkCore')

function emptyRecognitionState() {
  return {
    records: [],
    lastSeq: 0,
    status: '',
    error: null,
    complete: false,
    seenSeqs: {},
  }
}

function eventTypeOf(ev) {
  return ev && (ev.eventType || ev.event_type || '')
}

function imageIndexOf(ev, payload) {
  if (payload && payload.imageIndex != null) return payload.imageIndex
  if (ev && ev.imageIndex != null) return ev.imageIndex
  return null
}

function cloneRecord(row, imageIndex) {
  const next = Object.assign({}, row)
  if (imageIndex != null) {
    next.__imageIndex = imageIndex
  }
  return next
}

function replaceImageRecords(records, imageIndex, pageRecords) {
  const kept = (records || []).filter((row) => row && row.__imageIndex !== imageIndex)
  const incoming = Array.isArray(pageRecords) ? pageRecords : []
  for (let i = 0; i < incoming.length; i++) {
    if (incoming[i] && typeof incoming[i] === 'object') {
      kept.push(cloneRecord(incoming[i], imageIndex))
    }
  }
  return kept
}

function applyRecognitionEvents(state, batch) {
  const next = {
    records: state && Array.isArray(state.records) ? state.records.slice() : [],
    lastSeq: state && state.lastSeq ? Number(state.lastSeq) : 0,
    status: state && state.status ? state.status : '',
    error: state && state.error ? state.error : null,
    complete: !!(state && state.complete),
    seenSeqs: Object.assign({}, state && state.seenSeqs ? state.seenSeqs : {}),
  }
  const events = Array.isArray(batch) ? batch : []
  for (let i = 0; i < events.length; i++) {
    const ev = events[i]
    if (!ev || ev.seq == null) {
      continue
    }
    const seq = Number(ev.seq)
    if (!Number.isFinite(seq)) {
      continue
    }
    const type = eventTypeOf(ev)
    if (type === 'run_start') {
      next.records = []
      next.seenSeqs = {}
      next.lastSeq = seq
      next.complete = false
      next.error = null
      next.status = (ev.payload && ev.payload.status) || 'processing'
      next.seenSeqs[seq] = 1
      if (ev.payload && Array.isArray(ev.payload.records)) {
        next.records = ev.payload.records
          .filter((row) => row && typeof row === 'object')
          .map((row) => Object.assign({}, row))
      }
      continue
    }
    if (next.seenSeqs[seq]) {
      continue
    }
    next.seenSeqs[seq] = 1
    if (seq > next.lastSeq) {
      next.lastSeq = seq
    }
    const payload = ev.payload
    if (type === 'page_done' && payload) {
      if (Array.isArray(payload.records)) {
        next.records = replaceImageRecords(next.records, imageIndexOf(ev, payload), payload.records)
      }
    } else if (type === 'status' && payload) {
      next.status = payload.status || next.status
    } else if (type === 'error') {
      next.error = payload || { message: 'error' }
      next.status = 'failed'
    } else if (type === 'complete') {
      next.complete = true
      next.status = (payload && payload.status) || 'processed'
      if (payload && Array.isArray(payload.records)) {
        next.records = payload.records.filter((row) => row && typeof row === 'object').map((row) => Object.assign({}, row))
      }
    }
  }
  return next
}

function isRecognitionTerminal(status) {
  return status === 'processed'
    || status === 'failed'
    || status === 'cancelled'
    || status === 'confirmed'
}

/** 识别说明正文（过滤「正常」与签字结果类标签） */
function liveRecognitionNote(record) {
  const row = record && typeof record === 'object' ? record : {}
  const mark = String(row.SmartMark || row.Mark || row.smartMark || '').trim()
  const parts = splitSmartMarkParts(mark).filter((part) => {
    if (isSignatureResultMark(part)) return false
    if (markContains(part, 'normal')) return false
    return true
  })
  return parts.join('；')
}

function toLiveRowPreview(record) {
  const row = record && typeof record === 'object' ? record : {}
  const note = liveRecognitionNote(row)
  return {
    name: row.NOM_PRENOM || '',
    shift: row.HORAIRES_DU_TRAVAIL || '',
    arrive: row.ARRIVEE || '',
    leave: row.DEPAR || '',
    note,
    needsConfirm: !!note,
  }
}

function summarizeLiveRows(rows) {
  const list = Array.isArray(rows) ? rows : []
  let pending = 0
  for (let i = 0; i < list.length; i++) {
    if (list[i] && list[i].needsConfirm) pending++
  }
  return { total: list.length, pending }
}

module.exports = {
  emptyRecognitionState,
  applyRecognitionEvents,
  isRecognitionTerminal,
  liveRecognitionNote,
  toLiveRowPreview,
  summarizeLiveRows,
}
