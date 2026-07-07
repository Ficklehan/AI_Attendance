/** 根据表头文案与数据内容估算列宽（避免写死 width） */

const HEADER_TOOLBAR_WITH_FILTER = 88
const HEADER_TOOLBAR_SORT_ONLY = 44
const COMPACT_HEADER_TOOLBAR_WITH_FILTER = 38
const COMPACT_HEADER_TOOLBAR_SORT_ONLY = 22
const CELL_H_PADDING = 28
const COMPACT_CELL_H_PADDING = 16

const FIXED_WIDTH_KEYS = new Set(['action', 'operation'])

const DEFAULT_MAX_BY_KEY = {
  serialNo: 44,
  NO: 96,
  Pays: 84,
  NOM_PRENOM: 360,
  name: 360,
  Observations: 280,
  observations: 280,
  fileKey: 260,
  Entrepot: 240,
  warehouse: 240,
  AGENCE_INTERIMAIRE: 240,
  agency: 240,
  anomalyReasons: 260,
}

const COMPACT_MAX_BY_KEY = {
  no: 72,
  country: 76,
  date: 108,
  shift: 96,
  arrival: 84,
  departure: 84,
  pauseMinutes: 72,
}

const COMPACT_MIN_BY_KEY = {
  no: 56,
  country: 60,
  date: 88,
  shift: 76,
  arrival: 72,
  departure: 72,
  pauseMinutes: 56,
}

let measureCanvas

function getMeasureContext() {
  if (typeof document === 'undefined') return null
  if (!measureCanvas) {
    measureCanvas = document.createElement('canvas')
  }
  return measureCanvas.getContext('2d')
}

function measureTextPx(text, font) {
  const ctx = getMeasureContext()
  if (!ctx) {
    const s = String(text || '')
    let w = 0
    for (const ch of s) {
      w += ch.charCodeAt(0) > 255 ? 14 : 8
    }
    return w
  }
  ctx.font = font
  return ctx.measureText(String(text || '')).width
}

function titleText(title) {
  if (title == null) return ''
  if (typeof title === 'string' || typeof title === 'number') return String(title)
  return ''
}

function sampleCellText(record, col, getCellSample) {
  if (!record || !col) return ''
  if (typeof getCellSample === 'function') {
    const custom = getCellSample(col, record)
    if (custom != null && custom !== '') return String(custom).replace(/\s+/g, ' ').trim()
  }
  const key = col.dataIndex ?? col.key
  if (!key) return ''
  const v = record[key]
  if (v == null) return ''
  if (typeof v === 'number') return `${v}`
  if (Array.isArray(v)) return v.length ? v.join(', ') : ''
  return String(v).replace(/\s+/g, ' ').trim()
}

function headerToolbarWidth(col) {
  const compact = col.density === 'compact'
  const hasFilter = !!(col.searchField || col.filterType)
  const hasSort = !!(col._sortFn || col.sorter)
  if (compact) {
    let width = 8
    if (hasSort) width += COMPACT_HEADER_TOOLBAR_SORT_ONLY
    if (hasFilter) width += COMPACT_HEADER_TOOLBAR_WITH_FILTER
    return width
  }
  let width = 16
  if (hasSort) width += HEADER_TOOLBAR_SORT_ONLY
  if (hasFilter) width += HEADER_TOOLBAR_WITH_FILTER
  return width
}

/** 横向滚动宽度 = 各列 width 之和（Ant Design scroll.x 需数值，不能用 max-content） */
export function sumTableScrollX(columns, buffer = 24) {
  if (!columns?.length) return undefined
  const total = columns.reduce((sum, col) => {
    const w = col.width ?? col.minWidth ?? 0
    return sum + (Number.isFinite(w) ? w : 0)
  }, 0)
  return total > 0 ? Math.ceil(total + buffer) : undefined
}

/**
 * @param {Array} columns
 * @param {Array} records
 * @param {{ sampleSize?: number, defaultMin?: number, defaultMax?: number, font?: string }} [options]
 */
export function autoSizeTableColumns(columns, records, options = {}) {
  const sampleSize = options.sampleSize ?? 120
  const defaultMin = options.defaultMin ?? 72
  const defaultMax = options.defaultMax ?? 320
  const getCellSample = options.getCellSample
  const font = options.font ?? '600 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
  const bodyFont = options.bodyFont ?? '13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
  const sample = (records || []).slice(0, sampleSize)

  return (columns || []).map((col) => {
    if (col.width != null && col.autoWidth === false) return col
    if (col.fixed || FIXED_WIDTH_KEYS.has(col.key)) {
      const width = col.width ?? options.actionWidth ?? 56
      return { ...col, width, minWidth: width }
    }

    const isCompact = col.density === 'compact'
    const cellPadding = isCompact ? COMPACT_CELL_H_PADDING : CELL_H_PADDING
    const headerFont = isCompact
      ? (options.compactHeaderFont ?? '600 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif')
      : font
    const compactBodyFont = options.compactBodyFont
      ?? '11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'
    const measureBodyFont = isCompact ? compactBodyFont : bodyFont

    const headerW = measureTextPx(titleText(col.title), headerFont) + headerToolbarWidth(col) + cellPadding
    let contentW = headerW

    for (const row of sample) {
      const text = sampleCellText(row, col, getCellSample)
      if (text) {
        contentW = Math.max(contentW, measureTextPx(text, measureBodyFont) + cellPadding)
      }
    }

    const maxW = col.maxWidth
      ?? (isCompact ? COMPACT_MAX_BY_KEY[col.key] : undefined)
      ?? DEFAULT_MAX_BY_KEY[col.key]
      ?? DEFAULT_MAX_BY_KEY[col.dataIndex]
      ?? (isCompact ? 88 : defaultMax)
    const minW = col.minWidth
      ?? (isCompact ? COMPACT_MIN_BY_KEY[col.key] : undefined)
      ?? (isCompact ? 36 : defaultMin)
    const width = Math.ceil(Math.min(maxW, Math.max(minW, contentW)))

    const next = { ...col, width, minWidth: width, ellipsis: false }
    return next
  })
}
