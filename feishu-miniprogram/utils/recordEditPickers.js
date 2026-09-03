/**
 * 修改页日期/时间选择器与修改前后文案。
 */
const { t, tOr } = require('./i18n')
const { parseShiftSchedule } = require('../shared-js/shiftVarianceCore')
const { normalizeClockTime } = require('../shared-js/recognizedTimeNormalizer')
const { normalizeDate } = require('../shared-js/recognizedDateNormalizer')
const { getBaselineFieldDiffs } = require('../shared-js/exceptionTypeCore')

function pad2(n) {
  return String(n).padStart(2, '0')
}

function todayYmd() {
  const d = new Date()
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** picker mode=date 需要 YYYY-MM-DD */
function toPickerDate(value) {
  const normalized = normalizeDate(value)
  if (/^\d{4}-\d{2}-\d{2}$/.test(normalized)) return normalized
  return todayYmd()
}

/** picker mode=time 需要 HH:mm */
function toPickerTime(value, fallback) {
  const normalized = normalizeClockTime(value)
  if (/^\d{2}:\d{2}$/.test(normalized)) return normalized
  const fb = String(fallback || '09:00')
  return /^\d{2}:\d{2}$/.test(fb) ? fb : '09:00'
}

function parseShiftParts(value) {
  const shift = parseShiftSchedule(value)
  if (shift) {
    return { start: shift.shiftStart, end: shift.shiftEnd }
  }
  const raw = String(value || '').trim()
  const m = raw.match(/(\d{1,2}:\d{2})\s*[-~–—]\s*(\d{1,2}:\d{2})/)
  if (m) {
    return { start: toPickerTime(m[1]), end: toPickerTime(m[2]) }
  }
  return { start: '09:00', end: '18:00' }
}

function composeShift(start, end) {
  return `${toPickerTime(start)}-${toPickerTime(end)}`
}

function formatDiffValue(value) {
  const v = String(value == null ? '' : value).trim()
  if (!v) {
    const empty = tOr('result.fieldChangeEmpty', null, '（空）')
    return empty
  }
  return v
}

function formatFieldChangeHint(from, to) {
  const a = formatDiffValue(from)
  const b = formatDiffValue(to)
  if (String(from == null ? '' : from).trim() === String(to == null ? '' : to).trim()) {
    return ''
  }
  const line = tOr('result.fieldChangeInline', { from: a, to: b }, `${a}→${b}`)
  return line
}

/**
 * 相对 AI 基线或打开编辑时的快照，生成字段修改前后文案。
 */
function resolveFieldChangeHint(record, fieldKey, currentValue, openBaseline) {
  if (!record || !fieldKey) return ''
  const baseline = (record._aiBaseline && typeof record._aiBaseline === 'object')
    ? record._aiBaseline
    : openBaseline
  if (!baseline || typeof baseline !== 'object') return ''
  return formatFieldChangeHint(baseline[fieldKey], currentValue)
}

function enrichCalibFieldForPicker(field, record, openBaseline) {
  if (!field) return field
  const key = field.key
  const value = field.value
  const out = { ...field }

  if (key === 'Date') {
    out.inputType = 'date'
    out.pickerValue = toPickerDate(value)
    out.displayValue = value || out.pickerValue
  } else if (key === 'ARRIVEE' || key === 'DEPAR') {
    out.inputType = 'time'
    out.pickerValue = toPickerTime(value, key === 'DEPAR' ? '18:00' : '09:00')
    out.displayValue = value || out.pickerValue
  } else if (key === 'HORAIRES_DU_TRAVAIL') {
    const parts = parseShiftParts(value)
    out.inputType = 'shift'
    out.shiftStart = parts.start
    out.shiftEnd = parts.end
    out.displayValue = value || composeShift(parts.start, parts.end)
  } else if (key === 'PAUSE') {
    out.inputType = 'number'
    out.displayValue = value
  } else {
    out.displayValue = value
  }

  out.changeHint = resolveFieldChangeHint(record, key, value, openBaseline)
  return out
}

module.exports = {
  toPickerDate,
  toPickerTime,
  parseShiftParts,
  composeShift,
  formatFieldChangeHint,
  resolveFieldChangeHint,
  enrichCalibFieldForPicker,
  getBaselineFieldDiffs,
}
