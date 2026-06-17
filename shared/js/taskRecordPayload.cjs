/**
 * 合并 raw_data 与 confirmed_data：保留全部行，确认数据覆盖同 _rowKey 行的编辑内容。
 */
function rowKeyOf(row, index) {
  if (row && row._rowKey) return String(row._rowKey)
  return `__idx_${index}`
}

function mergeTaskRecordArrays(rawArr, confArr) {
  const raw = Array.isArray(rawArr) ? rawArr : []
  const conf = Array.isArray(confArr) ? confArr : []
  if (!conf.length) return raw
  if (!raw.length) return conf

  const hasStableKeys = raw.some((r) => r && r._rowKey) || conf.some((r) => r && r._rowKey)
  if (!hasStableKeys && raw.length === conf.length) {
    return raw.map((row, i) => ({ ...row, ...conf[i] }))
  }

  const confirmedByKey = new Map()
  conf.forEach((row, i) => {
    confirmedByKey.set(rowKeyOf(row, i), row)
  })

  const usedConfirmed = new Set()
  const merged = []

  raw.forEach((rawRow, i) => {
    const key = rowKeyOf(rawRow, i)
    const confirmedRow = confirmedByKey.get(key)
    if (confirmedRow) {
      merged.push({ ...rawRow, ...confirmedRow })
      usedConfirmed.add(key)
    } else {
      merged.push(rawRow)
    }
  })

  conf.forEach((confRow, i) => {
    const key = rowKeyOf(confRow, i)
    if (!usedConfirmed.has(key)) {
      merged.push(confRow)
    }
  })

  return merged
}

/**
 * 解析任务记录 JSON（对象或已解析数组）。
 */
function resolveTaskRecordsJson(task) {
  if (!task) return null
  const raw = task.rawData
  const confirmed = task.confirmedData
  if (task.status !== 'confirmed') {
    return raw || confirmed || null
  }
  if (!confirmed) return raw || null
  if (!raw) return confirmed
  try {
    const merged = mergeTaskRecordArrays(JSON.parse(raw), JSON.parse(confirmed))
    return JSON.stringify(merged)
  } catch (e) {
    return confirmed
  }
}

module.exports = {
  mergeTaskRecordArrays,
  resolveTaskRecordsJson,
}
