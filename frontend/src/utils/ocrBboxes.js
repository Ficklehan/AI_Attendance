export function parseBbox(raw) {
  if (!raw) return null
  if (Array.isArray(raw) && raw.length >= 4) {
    const x = Number(raw[0])
    const y = Number(raw[1])
    const a = Number(raw[2])
    const b = Number(raw[3])
    if (![x, y, a, b].every((n) => Number.isFinite(n))) return null
    if (a > 0 && b > 0) return { x, y, w: a, h: b }
    return null
  }
  if (typeof raw === 'object') {
    if (raw.x1 != null || raw.x2 != null) {
      const x1 = Number(raw.x1)
      const y1 = Number(raw.y1)
      const x2 = Number(raw.x2)
      const y2 = Number(raw.y2)
      return {
        x: Math.min(x1, x2),
        y: Math.min(y1, y2),
        w: Math.abs(x2 - x1),
        h: Math.abs(y2 - y1),
      }
    }
    const w = Number(raw.w ?? raw.width)
    const h = Number(raw.h ?? raw.height)
    const x = Number(raw.x)
    const y = Number(raw.y)
    if (w > 0 && h > 0) return { x, y, w, h }
  }
  return null
}

function toneFor(record, confidence) {
  const mark = String(record?.Mark || record?.SmartMark || '')
  if (mark.includes('模糊') || (Number(confidence) >= 0 && Number(confidence) < 0.85)) return 'blur'
  if (mark.includes('未出勤')) return 'absent'
  return 'ok'
}

function pushBox(list, bbox, label, confidence, record) {
  const box = parseBbox(bbox)
  if (!box || box.w < 1 || box.h < 1) return
  const conf = Number(confidence)
  const confLabel = Number.isFinite(conf) && conf >= 0 ? ` ${conf.toFixed(2)}` : ''
  list.push({
    ...box,
    label: `${label}${confLabel}`.trim(),
    tone: toneFor(record, confidence),
  })
}

export function boxesForRecord(record) {
  if (!record) return []
  const list = []
  pushBox(list, record._arrivoBbox || record.arrivoBbox, 'ARRIVEE', record._arrivoConfidence, record)
  pushBox(list, record._uscitaBbox || record.uscitaBbox, 'DEPAR', record._uscitaConfidence, record)
  return list
}

export function boxesForPage(records, pageIndex, selectedKey, rowKeyFn) {
  const page = Number(pageIndex) || 0
  const rows = (records || []).filter((r) => Number(r?._pageIndex || 0) === page)
  if (!rows.length) return []
  if (selectedKey && typeof rowKeyFn === 'function') {
    const hit = rows.find((r) => rowKeyFn(r) === selectedKey)
    if (hit) return boxesForRecord(hit)
  }
  return rows.flatMap(boxesForRecord)
}
