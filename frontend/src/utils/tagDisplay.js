/** 表格/列表内过长标签的展示文案 */
export function shortenTagLabel(text, maxLength = 26) {
  const value = String(text || '').trim()
  if (!value) return { short: '', truncated: false }
  if (value.length <= maxLength) return { short: value, truncated: false }

  const colonMatch = value.match(/^(.+?[：:])\s*.+$/u)
  if (colonMatch) {
    const prefix = colonMatch[1]
    if (prefix.length <= maxLength) {
      return { short: `${prefix}…`, truncated: true }
    }
  }

  return { short: `${value.slice(0, maxLength)}…`, truncated: true }
}

/** 弹层内将长名单拆成多行便于阅读 */
export function formatTagPopoverBody(text) {
  const value = String(text || '').trim()
  if (!value) return ''

  const colonMatch = value.match(/^(.+?[：:])\s*(.+)$/u)
  if (!colonMatch) return value

  const prefix = colonMatch[1]
  const rest = colonMatch[2]
  const parts = rest.split(/[、,]\s*/).map((s) => s.trim()).filter(Boolean)
  if (parts.length <= 2) return value
  return `${prefix}\n${parts.join('\n')}`
}
