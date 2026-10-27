/**
 * 到离格墨迹二次校验（PC 确认页）：未出勤 + 该行到离区有黑笔迹 → 改为模糊待复核。
 * 与后端 ClockCellInkDetector 同一套阈值。
 */

const DARK_RATIO_THRESHOLD = 0.028
const LUMINANCE_VARIANCE_THRESHOLD = 280
const DARK_LUMINANCE = 165

function luminance(r, g, b) {
  return (r * 299 + g * 587 + b * 114) / 1000
}

function clocksEmpty(record) {
  const arrive = String((record && (record.ARRIVEE || record.arrival)) || '').trim()
  const depart = String((record && (record.DEPAR || record.DEPART || record.departure)) || '').trim()
  return !arrive && !depart
}

function markText(record) {
  return [record && record.SmartMark, record && record.Mark, record && record.smartMark, record && record.mark]
    .filter(Boolean)
    .join(';')
}

function shouldInspect(record) {
  if (!record || record.isDeleted || record.deleted || record._manuallyAdded || record._restored) return false
  if (!clocksEmpty(record)) return false
  const mark = markText(record)
  if (/模糊|blur/i.test(mark)) return false
  return /未出勤|absent/i.test(mark)
}

function sampleClockBand(imageData, width, height, rowIndex, rowCount) {
  const tableTop = Math.floor(height * 0.30)
  const tableBottom = Math.max(tableTop + 8, Math.floor(height * 0.98))
  const x0 = Math.floor(width * 0.48)
  const x1 = Math.max(x0 + 4, Math.floor(width * 0.78))
  const slots = rowCount + 1
  const band = Math.max(4, Math.floor((tableBottom - tableTop) / slots))
  const y0 = tableTop + band * (rowIndex + 1)
  const y1 = Math.min(tableBottom, y0 + band)
  if (y1 <= y0) return { darkRatio: 0, variance: 0 }
  const data = imageData.data
  let count = 0
  let dark = 0
  let sum = 0
  let sumSq = 0
  const stepX = Math.max(1, Math.floor((x1 - x0) / 80))
  const stepY = Math.max(1, Math.floor((y1 - y0) / 40))
  for (let y = y0; y < y1; y += stepY) {
    for (let x = x0; x < x1; x += stepX) {
      const i = (y * width + x) * 4
      const lum = luminance(data[i], data[i + 1], data[i + 2])
      count++
      sum += lum
      sumSq += lum * lum
      if (lum < DARK_LUMINANCE) dark++
    }
  }
  if (!count) return { darkRatio: 0, variance: 0 }
  const mean = sum / count
  const variance = Math.max(0, sumSq / count - mean * mean)
  return { darkRatio: dark / count, variance }
}

function hasClockInkFromImageData(imageData, width, height, rowIndex, rowCount) {
  const stats = sampleClockBand(imageData, width, height, rowIndex, rowCount)
  return stats.darkRatio >= DARK_RATIO_THRESHOLD
    || (stats.variance >= LUMINANCE_VARIANCE_THRESHOLD && stats.darkRatio >= 0.012)
}

function forceBlurReview(record) {
  record.ARRIVEE = ''
  record.DEPAR = ''
  record.Mark = '模糊'
  record.SmartMark = '模糊'
  record._reviewReason = 'clock_ink'
  record.ExceptionType = ''
}

function groupRecordsBySheet(records) {
  const groups = []
  let current = []
  let key = null
  for (const record of records || []) {
    const nextKey = `${record.Entrepot || ''}|${record.Date || ''}`
    if (key !== null && nextKey !== key && current.length) {
      groups.push(current)
      current = []
    }
    key = nextKey
    current.push(record)
  }
  if (current.length) groups.push(current)
  return groups
}

/**
 * @param {object[]} records
 * @param {{ width: number, height: number, data: Uint8ClampedArray }} imageData
 * @param {number} [sheetIndex]
 * @returns {number} 改写行数
 */
function applyAbsentInkReviewOnSheet(records, imageData, sheetIndex) {
  if (!records || !records.length || !imageData) return 0
  const groups = groupRecordsBySheet(records)
  const sheet = groups[sheetIndex || 0] || records
  let changed = 0
  sheet.forEach((record, index) => {
    if (!shouldInspect(record)) return
    if (!hasClockInkFromImageData(imageData, imageData.width, imageData.height, index, sheet.length)) return
    forceBlurReview(record)
    changed++
  })
  return changed
}

module.exports = {
  DARK_RATIO_THRESHOLD,
  LUMINANCE_VARIANCE_THRESHOLD,
  shouldInspect,
  hasClockInkFromImageData,
  sampleClockBand,
  applyAbsentInkReviewOnSheet,
  groupRecordsBySheet,
  forceBlurReview,
}
