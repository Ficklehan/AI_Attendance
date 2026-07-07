const NON_COPYABLE_COLUMN_KEYS = new Set([
  'action',
  'actions',
  'serialNo',
  'imageUrls',
  'anomalyReasons',
  'smartMark',
  'SmartMark',
  'SIGNATURE',
  'signature',
  'enabled',
])

export function isCopyableTableColumn(column) {
  if (!column?.key) return false
  if (NON_COPYABLE_COLUMN_KEYS.has(column.key)) return false
  if (String(column.key).startsWith('day-')) return false
  return true
}

export function formatTableCellText(value) {
  if (value == null || value === '') return ''
  if (Array.isArray(value)) {
    return value.map((item) => formatTableCellText(item)).filter(Boolean).join(', ')
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

export function resolveTableCellCopyText(column, record, text) {
  if (text != null && text !== '') {
    return formatTableCellText(text)
  }
  if (column?.dataIndex != null && record) {
    const dataIndex = column.dataIndex
    if (Array.isArray(dataIndex)) {
      return formatTableCellText(dataIndex.reduce((acc, key) => acc?.[key], record))
    }
    return formatTableCellText(record[dataIndex])
  }
  return ''
}
