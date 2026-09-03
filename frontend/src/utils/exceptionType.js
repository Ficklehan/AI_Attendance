/** @see shared/js/exceptionTypeCore.cjs */
import * as mod from '@shared/exceptionTypeCore.cjs'
import { importSharedCjs } from './importSharedCjs'

const api = importSharedCjs(mod) || {}

const FALLBACK_SNAPSHOT_FIELD_KEYS = [
  'Pays',
  'Entrepot',
  'Date',
  'NOM_PRENOM',
  'AGENCE_INTERIMAIRE',
  'HORAIRES_DU_TRAVAIL',
  'ARRIVEE',
  'DEPAR',
  'PAUSE',
]

function fallbackSnapshot(record, fieldKeys) {
  const keys = Array.isArray(fieldKeys) && fieldKeys.length ? fieldKeys : FALLBACK_SNAPSHOT_FIELD_KEYS
  const out = {}
  keys.forEach((key) => {
    const value = record ? record[key] : undefined
    out[key] = value === undefined || value === null ? '' : value
  })
  return out
}

function fallbackNormalizeExceptionType(value) {
  const v = String(value || '').trim()
  return ['attendance_ok', 'paper_ok_ocr_wrong', 'paper_wrong_time'].includes(v) ? v : ''
}

export const EXCEPTION_TYPE = api.EXCEPTION_TYPE || {
  ATTENDANCE_OK: 'attendance_ok',
  PAPER_OK_OCR_WRONG: 'paper_ok_ocr_wrong',
  PAPER_WRONG_TIME: 'paper_wrong_time',
}
export const EXCEPTION_TYPE_VALUES = Array.isArray(api.EXCEPTION_TYPE_VALUES)
  ? api.EXCEPTION_TYPE_VALUES
  : ['attendance_ok', 'paper_ok_ocr_wrong', 'paper_wrong_time']
export const EXCEPTION_TYPE_I18N_KEYS = api.EXCEPTION_TYPE_I18N_KEYS || {
  attendance_ok: 'taskEdit.exceptionTypeAttendanceOk',
  paper_ok_ocr_wrong: 'taskEdit.exceptionTypePaperOkOcrWrong',
  paper_wrong_time: 'taskEdit.exceptionTypePaperWrongTime',
}
export const EXCEPTION_TYPE_SHORT_I18N_KEYS = api.EXCEPTION_TYPE_SHORT_I18N_KEYS || {
  attendance_ok: 'taskEdit.exceptionTypeAttendanceOkShort',
  paper_ok_ocr_wrong: 'taskEdit.exceptionTypePaperOkOcrWrongShort',
  paper_wrong_time: 'taskEdit.exceptionTypePaperWrongTimeShort',
}
export const SNAPSHOT_FIELD_KEYS = Array.isArray(api.SNAPSHOT_FIELD_KEYS) && api.SNAPSHOT_FIELD_KEYS.length
  ? api.SNAPSHOT_FIELD_KEYS
  : FALLBACK_SNAPSHOT_FIELD_KEYS
export const CALIBRATION_HIGHLIGHT_FIELDS = Array.isArray(api.CALIBRATION_HIGHLIGHT_FIELDS)
  && api.CALIBRATION_HIGHLIGHT_FIELDS.length
  ? api.CALIBRATION_HIGHLIGHT_FIELDS
  : ['Date', 'HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR', 'PAUSE']

export const isValidExceptionType = typeof api.isValidExceptionType === 'function'
  ? api.isValidExceptionType
  : (value) => EXCEPTION_TYPE_VALUES.includes(String(value || '').trim())
export const normalizeExceptionType = typeof api.normalizeExceptionType === 'function'
  ? api.normalizeExceptionType
  : fallbackNormalizeExceptionType
export const isExceptionTypeExempt = typeof api.isExceptionTypeExempt === 'function'
  ? api.isExceptionTypeExempt
  : (record, isAbsentRowOrDeps) => {
    if (!record) return true
    if (record.isDeleted || record.deleted) return true
    let isAbsentRow = isAbsentRowOrDeps
    if (isAbsentRowOrDeps && typeof isAbsentRowOrDeps === 'object') {
      isAbsentRow = isAbsentRowOrDeps.isAbsentRow
    }
    if (typeof isAbsentRow === 'function' && isAbsentRow(record)) return true
    return false
  }
export const ensureExceptionType = typeof api.ensureExceptionType === 'function'
  ? api.ensureExceptionType
  : (record, deps) => {
    if (!record || !deps || typeof deps.hasRequiredMissing !== 'function') return record
    if (isExceptionTypeExempt(record, deps.isAbsentRow)) {
      record.ExceptionType = ''
      delete record._exceptionTypeManual
      return record
    }
    const incomplete = deps.hasRequiredMissing(record)
      || (typeof deps.hasFormatInvalid === 'function' && deps.hasFormatInvalid(record))
    if (incomplete) {
      record.ExceptionType = ''
      return record
    }
    const type = normalizeExceptionType(record.ExceptionType)
    const manual = record._exceptionTypeManual === true
      || record._exceptionTypeManual === 1
      || record._exceptionTypeManual === '1'
      || record._exceptionTypeManual === 'true'
    if (manual) {
      record.ExceptionType = type
      return record
    }
    if (typeof api.canAutoSelectAttendanceOk === 'function'
      ? api.canAutoSelectAttendanceOk(record, deps)
      : false) {
      record.ExceptionType = EXCEPTION_TYPE.ATTENDANCE_OK
      return record
    }
    if (type === EXCEPTION_TYPE.ATTENDANCE_OK) {
      record.ExceptionType = ''
      return record
    }
    record.ExceptionType = type
    return record
  }
export const matchesShiftExactTimes = typeof api.matchesShiftExactTimes === 'function'
  ? api.matchesShiftExactTimes
  : () => false
export const canAutoSelectAttendanceOk = typeof api.canAutoSelectAttendanceOk === 'function'
  ? api.canAutoSelectAttendanceOk
  : () => false
export const recordHasRecognitionTags = typeof api.recordHasRecognitionTags === 'function'
  ? api.recordHasRecognitionTags
  : () => false
export const isExceptionTypeSelectDisabled = typeof api.isExceptionTypeSelectDisabled === 'function'
  ? api.isExceptionTypeSelectDisabled
  : (record, deps) => {
    if (!record) return true
    if (isExceptionTypeExempt(record, deps && deps.isAbsentRow)) return true
    if (deps && typeof deps.hasRequiredMissing === 'function' && deps.hasRequiredMissing(record)) return true
    if (deps && typeof deps.hasFormatInvalid === 'function' && deps.hasFormatInvalid(record)) return true
    return false
  }
export const areRequiredFieldsLocked = typeof api.areRequiredFieldsLocked === 'function'
  ? api.areRequiredFieldsLocked
  : (record) => normalizeExceptionType(record && record.ExceptionType) === EXCEPTION_TYPE.ATTENDANCE_OK
export const shouldHighlightRequiredForCalibration = typeof api.shouldHighlightRequiredForCalibration === 'function'
  ? api.shouldHighlightRequiredForCalibration
  : (record) => {
    const type = normalizeExceptionType(record && record.ExceptionType)
    return type === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG || type === EXCEPTION_TYPE.PAPER_WRONG_TIME
  }
export const isCalibrationHighlightField = typeof api.isCalibrationHighlightField === 'function'
  ? api.isCalibrationHighlightField
  : (field) => CALIBRATION_HIGHLIGHT_FIELDS.includes(String(field || ''))
export const shouldHighlightFieldForCalibration = typeof api.shouldHighlightFieldForCalibration === 'function'
  ? api.shouldHighlightFieldForCalibration
  : (record, field) => shouldHighlightRequiredForCalibration(record) && isCalibrationHighlightField(field)
export const canEditRequiredFields = typeof api.canEditRequiredFields === 'function'
  ? api.canEditRequiredFields
  : (record) => {
    const type = normalizeExceptionType(record && record.ExceptionType)
    if (!type) return true
    if (type === EXCEPTION_TYPE.ATTENDANCE_OK) return false
    return true
  }
export const snapshotAttendanceFields = typeof api.snapshotAttendanceFields === 'function'
  ? api.snapshotAttendanceFields
  : fallbackSnapshot
export const getBaselineFieldDiffs = typeof api.getBaselineFieldDiffs === 'function'
  ? api.getBaselineFieldDiffs
  : (record, fieldKeys) => {
    if (!record || !record._aiBaseline || typeof record._aiBaseline !== 'object') return []
    const keys = Array.isArray(fieldKeys) && fieldKeys.length ? fieldKeys : SNAPSHOT_FIELD_KEYS
    const diffs = []
    keys.forEach((key) => {
      const from = String(record._aiBaseline[key] ?? '').trim()
      const to = String(record[key] ?? '').trim()
      if (from === to) return
      diffs.push({ field: key, from, to })
    })
    return diffs
  }
export const ensureAiBaseline = typeof api.ensureAiBaseline === 'function'
  ? api.ensureAiBaseline
  : (record, fieldKeys) => {
    if (!record) return
    if (record._aiBaseline && typeof record._aiBaseline === 'object') return
    record._aiBaseline = snapshotAttendanceFields(record, fieldKeys)
  }
export const updateLastEditSnapshot = typeof api.updateLastEditSnapshot === 'function'
  ? api.updateLastEditSnapshot
  : (record, fieldKeys) => {
    if (!record) return
    record._lastEditSnapshot = snapshotAttendanceFields(record, fieldKeys)
  }
export const onExceptionTypeChange = typeof api.onExceptionTypeChange === 'function'
  ? api.onExceptionTypeChange
  : (record, nextType, fieldKeys) => {
    if (!record) return
    const normalized = normalizeExceptionType(nextType)
    record.ExceptionType = normalized
    record._exceptionTypeManual = Boolean(normalized)
    if (
      normalized === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG
      || normalized === EXCEPTION_TYPE.PAPER_WRONG_TIME
    ) {
      ensureAiBaseline(record, fieldKeys)
      updateLastEditSnapshot(record, fieldKeys)
    }
  }
export const onCalibratableFieldFocus = typeof api.onCalibratableFieldFocus === 'function'
  ? api.onCalibratableFieldFocus
  : (record, fieldKeys) => {
    if (!record) return
    const type = normalizeExceptionType(record.ExceptionType)
    if (type !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG && type !== EXCEPTION_TYPE.PAPER_WRONG_TIME) return
    ensureAiBaseline(record, fieldKeys)
  }
export const onCalibratableFieldChange = typeof api.onCalibratableFieldChange === 'function'
  ? api.onCalibratableFieldChange
  : (record, fieldKeys) => {
    if (!record) return
    const type = normalizeExceptionType(record.ExceptionType)
    if (type !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG && type !== EXCEPTION_TYPE.PAPER_WRONG_TIME) return
    updateLastEditSnapshot(record, fieldKeys)
  }
export const isExceptionTypeMissingForSubmit = typeof api.isExceptionTypeMissingForSubmit === 'function'
  ? api.isExceptionTypeMissingForSubmit
  : (record, deps) => {
    if (isExceptionTypeExempt(record, deps && deps.isAbsentRow)) return false
    return !normalizeExceptionType(record && record.ExceptionType)
  }

export const OCR_WRONG_ADJUST_FIELDS = Array.isArray(api.OCR_WRONG_ADJUST_FIELDS)
  && api.OCR_WRONG_ADJUST_FIELDS.length
  ? api.OCR_WRONG_ADJUST_FIELDS
  : ['HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR', 'PAUSE']

export const hasOcrWrongTimeFieldAdjusted = typeof api.hasOcrWrongTimeFieldAdjusted === 'function'
  ? api.hasOcrWrongTimeFieldAdjusted
  : (record) => getBaselineFieldDiffs(record, OCR_WRONG_ADJUST_FIELDS).length > 0

export const isOcrWrongMissingFieldAdjustment = typeof api.isOcrWrongMissingFieldAdjustment === 'function'
  ? api.isOcrWrongMissingFieldAdjustment
  : (record, deps) => {
    if (isExceptionTypeExempt(record, deps && deps.isAbsentRow)) return false
    if (normalizeExceptionType(record && record.ExceptionType) !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG) {
      return false
    }
    return !hasOcrWrongTimeFieldAdjusted(record)
  }
