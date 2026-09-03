/** @see shared/js/confirmValidationGrouping.cjs */

/** @param {number[]} lines 1-based row numbers */
export function formatLineRanges(lines) {
  if (!lines || !lines.length) return ''
  const sorted = Array.from(new Set(lines)).sort((a, b) => a - b)
  const parts = []
  let start = sorted[0]
  let end = sorted[0]
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i] === end + 1) {
      end = sorted[i]
    } else {
      parts.push(start === end ? String(start) : `${start}–${end}`)
      start = end = sorted[i]
    }
  }
  parts.push(start === end ? String(start) : `${start}–${end}`)
  return parts.join('、')
}

/**
 * @param {Array<{ line: number, fields: string[], issueType?: string }>} issues
 */
export function groupConfirmValidationIssues(issues) {
  const map = new Map()
  ;(issues || []).forEach((issue) => {
    const fields = issue.fields || []
    const issueType = issue.issueType || 'missing'
    const key = `${issueType}\0${fields.join('\0')}`
    if (!map.has(key)) {
      map.set(key, { fields: fields.slice(), lines: [], issueType })
    }
    map.get(key).lines.push(issue.line)
  })
  return Array.from(map.values())
    .map((group) => {
      const lines = group.lines.sort((a, b) => a - b)
      return {
        fields: group.fields,
        lines,
        count: lines.length,
        lineRanges: formatLineRanges(lines),
        issueType: group.issueType,
      }
    })
    .sort((a, b) => b.count - a.count || a.lines[0] - b.lines[0])
}
