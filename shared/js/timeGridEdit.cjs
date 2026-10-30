const { isCanonicalClockTime, normalizeClockTime } = require('./recognizedTimeNormalizer.cjs')

const TIME_GRID_FIELDS = ['ARRIVEE', 'DEPAR']

function isTimeGridField(field) {
  return TIME_GRID_FIELDS.includes(field)
}

function parseClockCellText(text) {
  const clock = normalizeClockTime(String(text ?? '').trim())
  return isCanonicalClockTime(clock) ? clock : ''
}

function parseTimeGridClipboard(text) {
  const raw = String(text ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  if (!raw.trim()) return []
  const looksGrid = raw.includes('\t') || raw.includes('\n')
  if (!looksGrid) {
    const clock = parseClockCellText(raw)
    return clock ? [[clock]] : []
  }
  return raw
    .split('\n')
    .filter((line, index, lines) => line.length > 0 || index < lines.length - 1)
    .map((line) => line.split('\t').map((cell) => parseClockCellText(cell)))
    .filter((row) => row.some(Boolean))
}

function stepTimeCell(fields, field, dir) {
  const list = Array.isArray(fields) && fields.length ? fields : TIME_GRID_FIELDS
  const index = list.indexOf(field)
  if (index < 0) return { field, rowDelta: 0 }
  if (dir === 'next') {
    if (index + 1 < list.length) return { field: list[index + 1], rowDelta: 0 }
    return { field: list[0], rowDelta: 1 }
  }
  if (dir === 'prev') {
    if (index > 0) return { field: list[index - 1], rowDelta: 0 }
    return { field: list[list.length - 1], rowDelta: -1 }
  }
  if (dir === 'down') return { field, rowDelta: 1 }
  if (dir === 'up') return { field, rowDelta: -1 }
  return { field, rowDelta: 0 }
}

function findNextEditableTimeCell(rows, startIndex, field, dir, isEditable) {
  const list = Array.isArray(rows) ? rows : []
  let rowIndex = startIndex
  let nextField = field
  const step = stepTimeCell(TIME_GRID_FIELDS, nextField, dir)
  rowIndex += step.rowDelta
  nextField = step.field
  while (rowIndex >= 0 && rowIndex < list.length) {
    const record = list[rowIndex]
    if (record && typeof isEditable === 'function' && isEditable(record, nextField)) {
      return { record, field: nextField, rowIndex }
    }
    if (dir === 'down' || dir === 'up') {
      rowIndex += dir === 'down' ? 1 : -1
      continue
    }
    const again = stepTimeCell(TIME_GRID_FIELDS, nextField, dir)
    rowIndex += again.rowDelta
    nextField = again.field
  }
  return null
}

module.exports = {
  TIME_GRID_FIELDS,
  isTimeGridField,
  parseClockCellText,
  parseTimeGridClipboard,
  stepTimeCell,
  findNextEditableTimeCell,
}
