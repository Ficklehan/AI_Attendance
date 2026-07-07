const COMPACT_CELL_CLASS = 'col-density-compact'

const COMPACT_CENTER_KEYS = new Set(['no', 'country', 'pauseMinutes'])

/** 为紧凑列附加表头/单元格 class 与对齐方式 */
export function withColumnDensity(col) {
  if (col?.density !== 'compact') return col
  const align = COMPACT_CENTER_KEYS.has(col.key) ? 'center' : (col.align || 'left')
  return {
    ...col,
    align,
    customHeaderCell: () => ({ class: COMPACT_CELL_CLASS }),
    customCell: () => ({ class: COMPACT_CELL_CLASS }),
  }
}

export function mapColumnsWithDensity(columns) {
  return (columns || []).map((col) => withColumnDensity(col))
}
