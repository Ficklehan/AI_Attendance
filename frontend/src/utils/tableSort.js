/** 表格列默认排序：为带 dataIndex 的列添加可点击排序 */

const DEFAULT_SKIP_KEYS = new Set([
  'action',
  'operation',
  'anomalyReasons',
  'workHours',
])

export function getColumnSortFn(column) {
  if (!column || column.sorter === false) return null
  return column._sortFn || column.sorter || null
}

export function columnIsSortable(column) {
  return typeof getColumnSortFn(column) === 'function'
}

const PLACEHOLDER_LAST = new Set(['', '-', '—', '???', '??'])

function readField(record, dataIndex) {
  if (!record || dataIndex == null) return ''
  if (Array.isArray(dataIndex)) {
    return dataIndex.reduce((acc, key) => acc?.[key], record)
  }
  return record[dataIndex]
}

export function compareTableValues(aVal, bVal) {
  const aEmpty = aVal == null || PLACEHOLDER_LAST.has(String(aVal).trim())
  const bEmpty = bVal == null || PLACEHOLDER_LAST.has(String(bVal).trim())
  if (aEmpty && bEmpty) return 0
  if (aEmpty) return 1
  if (bEmpty) return -1

  if (typeof aVal === 'boolean' || typeof bVal === 'boolean') {
    return Number(aVal) - Number(bVal)
  }

  const aStr = String(aVal).trim()
  const bStr = String(bVal).trim()
  const aNum = Number(aStr)
  const bNum = Number(bStr)
  if (aStr !== '' && bStr !== '' && !Number.isNaN(aNum) && !Number.isNaN(bNum) && /^-?\d/.test(aStr) && /^-?\d/.test(bStr)) {
    return aNum - bNum
  }

  const aTime = Date.parse(aStr)
  const bTime = Date.parse(bStr)
  if (!Number.isNaN(aTime) && !Number.isNaN(bTime) && /^\d{4}[-/]/.test(aStr) && /^\d{4}[-/]/.test(bStr)) {
    return aTime - bTime
  }

  return aStr.localeCompare(bStr, undefined, { numeric: true, sensitivity: 'base' })
}

export function fieldSorter(dataIndex) {
  return (a, b) => compareTableValues(readField(a, dataIndex), readField(b, dataIndex))
}

export function keyFieldSorter(key) {
  return (a, b) => compareTableValues(a?.[key], b?.[key])
}

/**
 * @param {import('vue').MaybeRefOrGetter<Array>} columnsSource
 * @param {{ skipKeys?: string[] }} [options]
 */
export function withTableSorters(columns, options = {}) {
  const skip = new Set([...DEFAULT_SKIP_KEYS, ...(options.skipKeys || [])])
  const customHeader = options.customHeader === true
  return (columns || []).map((col) => {
    if (col.sorter === false || skip.has(col.key)) return col
    const dataIndex = col.dataIndex ?? (col.key && !skip.has(col.key) ? col.key : null)
    const sortFn = col.sorter || (dataIndex ? fieldSorter(dataIndex) : null)
    if (!sortFn) return col

    if (customHeader) {
      const next = {
        ...col,
        _sortFn: sortFn,
        sortDirections: col.sortDirections ?? ['ascend', 'descend'],
      }
      delete next.sorter
      return next
    }

    return {
      ...col,
      sorter: sortFn,
      sortDirections: col.sortDirections ?? ['ascend', 'descend'],
      showSorterTooltip: col.showSorterTooltip ?? true,
    }
  })
}

export function applySortToData(data, sortState, columns) {
  if (!Array.isArray(data) || !sortState?.order || !sortState?.columnKey) {
    return data
  }
  const col = (columns || []).find((c) => (c.key || c.dataIndex) === sortState.columnKey)
  const sortFn = getColumnSortFn(col)
  if (!sortFn) return data
  const sorted = [...data].sort(sortFn)
  return sortState.order === 'descend' ? sorted.reverse() : sorted
}

export function nextSortOrder(currentOrder) {
  if (!currentOrder) return 'ascend'
  if (currentOrder === 'ascend') return 'descend'
  return null
}

export function attachSortOrder(columns, sortState) {
  return (columns || []).map((col) => ({
    ...col,
    sortOrder: sortState?.columnKey === (col.key || col.dataIndex) ? sortState.order : null,
  }))
}
