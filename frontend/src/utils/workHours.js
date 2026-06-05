import { markContains, stripSignatureMarksFromSmartMark } from '@/utils/recognitionLabels'

function pickField(record, ...keys) {
  if (!record) return ''
  for (const key of keys) {
    const value = record[key]
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value).trim()
    }
  }
  return ''
}

export function normalizePauseMinutes(value) {
  if (value === null || value === undefined || value === '') return ''
  const normalized = String(value)
    .trim()
    .toLowerCase()
    .replace(',', '.')
    .replace(/\s+/g, '')
    .replace(/minutes?|mins?|mn/g, 'min')
  if (!normalized || normalized === '???' || normalized === '??' || normalized === 'illegible') return ''

  const hourMatch = normalized.match(/^(\d+(?:\.\d+)?)h(\d+(?:\.\d+)?)?(?:min|m)?$/)
  if (hourMatch) {
    const hours = Number(hourMatch[1])
    const minutes = hourMatch[2] ? Number(hourMatch[2]) : 0
    return Number.isNaN(hours) || Number.isNaN(minutes) ? value : Math.round(hours * 60 + minutes)
  }
  const colonMatch = normalized.match(/^(\d{1,2}):(\d{1,2})$/)
  if (colonMatch) return Number(colonMatch[1]) * 60 + Number(colonMatch[2])
  const minuteMatch = normalized.match(/^(\d+(?:\.\d+)?)(?:min|m)?$/)
  if (minuteMatch) {
    const minutes = Number(minuteMatch[1])
    return Number.isNaN(minutes) ? value : Math.round(minutes)
  }
  return value
}

export function parseTimeToMinutes(timeStr) {
  if (!timeStr || timeStr.trim() === '' || timeStr === '???') {
    return null
  }

  const cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':')
  const parts = cleanTime.split(':')

  if (parts.length === 2) {
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    if (!Number.isNaN(hours) && !Number.isNaN(minutes)) {
      return hours * 60 + minutes
    }
  } else if (parts.length === 1) {
    const num = parseFloat(parts[0])
    if (!Number.isNaN(num)) {
      return Math.floor(num) * 60 + Math.round((num % 1) * 60)
    }
  }

  return null
}

function getEffectiveMark(record) {
  const raw = record?.smartMark ?? record?.SmartMark ?? ''
  return stripSignatureMarksFromSmartMark(String(raw).trim())
}

function isAbsentRecord(record) {
  if (record?.isDeleted) return true
  return markContains(getEffectiveMark(record), 'absent')
}

/** 与任务编辑页一致：由到达/离开/休息计算出勤工时（小时，保留两位小数） */
export function calculateWorkHours(record) {
  if (isAbsentRecord(record)) {
    return '-'
  }

  const arriveTime = pickField(record, 'arrival', 'ARRIVEE')
  const departTime = pickField(record, 'departure', 'DEPAR')
  const pauseMinutes = normalizePauseMinutes(pickField(record, 'pauseMinutes', 'PAUSE'))

  if (!arriveTime || !departTime || arriveTime === '???' || departTime === '???') {
    return '-'
  }

  const arriveMinutes = parseTimeToMinutes(arriveTime)
  const departMinutes = parseTimeToMinutes(departTime)

  if (arriveMinutes === null || departMinutes === null) {
    return '-'
  }

  let totalMinutes = departMinutes - arriveMinutes
  if (totalMinutes < 0) {
    totalMinutes += 24 * 60
  }

  const pause = pauseMinutes !== '' ? Number(pauseMinutes) : 0
  const workMinutes = totalMinutes - (Number.isNaN(pause) ? 0 : pause)

  if (workMinutes < 0) {
    return '-'
  }

  return (workMinutes / 60).toFixed(2)
}
