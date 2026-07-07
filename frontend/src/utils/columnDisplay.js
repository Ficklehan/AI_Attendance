const FREEZE_PREFIX = 'attendance:column-freeze:'
const HIDDEN_PREFIX = 'attendance:column-hidden:'
const WIDTH_PREFIX = 'attendance:column-widths:'

export function getColumnKey(col) {
  if (!col) return ''
  return String(col.key || col.dataIndex || col.searchField || '').trim()
}

export function getConfigurableColumns(columns = []) {
  return columns.filter((col) => getColumnKey(col))
}

export function getFreezeableColumns(columns = []) {
  return columns.filter((col) => col.fixed !== 'right' && getColumnKey(col))
}

/** 冻结列须为从左侧起的连续前缀 */
export function enforceFrozenPrefix(orderedKeys, selectedKeys = []) {
  const selected = new Set(selectedKeys)
  let maxIdx = -1
  for (let i = 0; i < orderedKeys.length; i++) {
    if (selected.has(orderedKeys[i])) maxIdx = i
  }
  return maxIdx < 0 ? [] : orderedKeys.slice(0, maxIdx + 1)
}

export function loadColumnFreeze(storageId, fallback = []) {
  return loadStringArray(FREEZE_PREFIX + storageId, fallback)
}

export function saveColumnFreeze(storageId, keys) {
  saveStringArray(FREEZE_PREFIX + storageId, keys)
}

export function loadHiddenColumns(storageId, fallback = []) {
  return loadStringArray(HIDDEN_PREFIX + storageId, fallback)
}

export function saveHiddenColumns(storageId, keys) {
  saveStringArray(HIDDEN_PREFIX + storageId, keys)
}

function loadStringArray(storageKey, fallback) {
  if (!storageKey || typeof localStorage === 'undefined') return [...fallback]
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return [...fallback]
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.map(String) : [...fallback]
  } catch {
    return [...fallback]
  }
}

function saveStringArray(storageKey, keys) {
  if (!storageKey || typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(storageKey, JSON.stringify(keys || []))
  } catch {
    /* ignore quota */
  }
}

export function applyColumnVisibility(columns = [], hiddenKeys = [], options = {}) {
  const hidden = new Set(hiddenKeys || [])
  const minVisible = options.minVisible ?? 1
  const filtered = columns.filter((col) => {
    const key = getColumnKey(col)
    if (!key) return true
    return !hidden.has(key)
  })
  if (filtered.length >= minVisible) return filtered
  return columns.slice(0, Math.max(minVisible, 1))
}

export function applyColumnFreeze(columns = [], frozenKeys = [], options = {}) {
  const preserveRight = options.preserveRightFixed !== false
  const frozenSet = new Set(frozenKeys)

  return columns.map((col) => {
    if (preserveRight && col.fixed === 'right') return col
    const key = getColumnKey(col)
    if (key && frozenSet.has(key)) {
      return { ...col, fixed: 'left' }
    }
    if (col.fixed === 'left') {
      const { fixed, ...rest } = col
      return rest
    }
    return col
  })
}

export function formatColumnTitle(title) {
  if (title == null) return ''
  if (typeof title === 'string' || typeof title === 'number') return String(title)
  if (Array.isArray(title)) return title.map(formatColumnTitle).join('')
  return String(title)
}

export function loadColumnWidths(storageId) {
  if (!storageId || typeof localStorage === 'undefined') return {}
  try {
    const raw = localStorage.getItem(WIDTH_PREFIX + storageId)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const widths = {}
    for (const [key, value] of Object.entries(parsed)) {
      const num = Number(value)
      if (key && Number.isFinite(num) && num > 0) widths[String(key)] = num
    }
    return widths
  } catch {
    return {}
  }
}

export function saveColumnWidths(storageId, widths) {
  if (!storageId || typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(WIDTH_PREFIX + storageId, JSON.stringify(widths || {}))
  } catch {
    /* ignore quota */
  }
}

export function applyUserColumnWidths(columns = [], userWidths = {}, nonResizableKeys = new Set()) {
  if (!columns?.length || !userWidths || !Object.keys(userWidths).length) return columns
  return columns.map((col) => {
    const key = getColumnKey(col)
    if (!key || nonResizableKeys.has(key)) return col
    const width = userWidths[key]
    if (!Number.isFinite(width) || width <= 0) return col
    return { ...col, width, minWidth: width }
  })
}
