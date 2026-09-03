/**
 * 任务行「异常类型」：选项、锁字段、OCR 纠错快照。
 */

const {
  parseShiftSchedule,
  parseClockToMinutes,
} = require('./shiftVarianceCore.cjs')
const {
  markHasKind,
  markContains,
  splitSmartMarkParts,
  isSignatureResultMark,
} = require('./recognitionMarkCore.cjs')

const EXCEPTION_TYPE = {
  ATTENDANCE_OK: 'attendance_ok',
  PAPER_OK_OCR_WRONG: 'paper_ok_ocr_wrong',
  PAPER_WRONG_TIME: 'paper_wrong_time',
}

const EXCEPTION_TYPE_VALUES = [
  EXCEPTION_TYPE.ATTENDANCE_OK,
  EXCEPTION_TYPE.PAPER_OK_OCR_WRONG,
  EXCEPTION_TYPE.PAPER_WRONG_TIME,
]

const EXCEPTION_TYPE_I18N_KEYS = {
  [EXCEPTION_TYPE.ATTENDANCE_OK]: 'taskEdit.exceptionTypeAttendanceOk',
  [EXCEPTION_TYPE.PAPER_OK_OCR_WRONG]: 'taskEdit.exceptionTypePaperOkOcrWrong',
  [EXCEPTION_TYPE.PAPER_WRONG_TIME]: 'taskEdit.exceptionTypePaperWrongTime',
}

/** 下拉短标签（表格窄列） */
const EXCEPTION_TYPE_SHORT_I18N_KEYS = {
  [EXCEPTION_TYPE.ATTENDANCE_OK]: 'taskEdit.exceptionTypeAttendanceOkShort',
  [EXCEPTION_TYPE.PAPER_OK_OCR_WRONG]: 'taskEdit.exceptionTypePaperOkOcrWrongShort',
  [EXCEPTION_TYPE.PAPER_WRONG_TIME]: 'taskEdit.exceptionTypePaperWrongTimeShort',
}

const SNAPSHOT_FIELD_KEYS = [
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

function isValidExceptionType(value) {
  return EXCEPTION_TYPE_VALUES.indexOf(String(value || '').trim()) !== -1
}

function normalizeExceptionType(value) {
  const v = String(value || '').trim()
  return isValidExceptionType(v) ? v : ''
}

function isExceptionTypeExempt(record, isAbsentRowOrDeps) {
  if (!record) return true
  if (record.isDeleted || record.deleted) return true
  let isAbsentRow = isAbsentRowOrDeps
  if (isAbsentRowOrDeps && typeof isAbsentRowOrDeps === 'object') {
    isAbsentRow = isAbsentRowOrDeps.isAbsentRow
  }
  if (typeof isAbsentRow === 'function' && isAbsentRow(record)) return true
  return false
}

/** 识别错误提交时：须至少改过其中一项（相对 AI 基线） */
const OCR_WRONG_ADJUST_FIELDS = [
  'HORAIRES_DU_TRAVAIL',
  'ARRIVEE',
  'DEPAR',
  'PAUSE',
]

function isExceptionTypeManuallySet(record) {
  if (!record) return false
  const flag = record._exceptionTypeManual
  return flag === true || flag === 1 || flag === '1' || flag === 'true'
}

/**
 * 识别说明里是否已有标签（夜班/手写/模糊/未出勤/已删除等，不含单纯「正常」）。
 * 有标签则不可自动「考勤正确」。
 */
function recordHasRecognitionTags(record) {
  if (!record) return false
  if (record.isDeleted) return true
  const mark = String(record.SmartMark || record.Mark || '').trim()
  if (!mark) return false
  if (markHasKind(mark, 'absent') || markHasKind(mark, 'deleted')) return true
  if (markHasKind(mark, 'nightShift')) return true
  if (markHasKind(mark, 'handwriting') || markHasKind(mark, 'blurred')) return true
  return splitSmartMarkParts(mark).some((part) => {
    if (isSignatureResultMark(part)) return false
    if (markContains(part, 'normal')) return false
    return Boolean(String(part || '').trim())
  })
}

/**
 * 到达=班次开始 且 离开=班次结束 → 可自动「考勤正确」。
 */
function matchesShiftExactTimes(record) {
  if (!record) return false
  const shift = parseShiftSchedule(record.HORAIRES_DU_TRAVAIL ?? record.shift ?? '')
  if (!shift) return false
  const arriveMin = parseClockToMinutes(record.ARRIVEE ?? record.arrival ?? '')
  const departMin = parseClockToMinutes(record.DEPAR ?? record.DEPART ?? record.departure ?? '')
  if (arriveMin < 0 || departMin < 0) return false
  return arriveMin === shift.startMinutes && departMin === shift.endMinutes
}

function canAutoSelectAttendanceOk(record, deps) {
  if (!matchesShiftExactTimes(record)) return false
  // 识别说明含标签（含夜班/未出勤/已删除等）→ 不自动勾选
  if (recordHasRecognitionTags(record)) return false
  if (deps && typeof deps.hasRecognitionNotes === 'function' && deps.hasRecognitionNotes(record)) {
    return false
  }
  return true
}

/**
 * 已删除 / 未出勤：不标记异常类型。
 * 缺必填 / 格式不合法 → 清空类型。
 * 用户已手动点选 → 保留。
 * 仅当「到达=班次开始且离开=班次结束」且识别说明无标签时，自动「考勤正确」；
 * 其余情况不自动勾选，须用户确认。
 * @param {object} record
 * @param {{
 *   hasRequiredMissing: (r: object) => boolean,
 *   hasFormatInvalid?: (r: object) => boolean,
 *   isAbsentRow?: (r: object) => boolean,
 *   hasRecognitionNotes?: (r: object) => boolean,
 * }} deps
 */
function ensureExceptionType(record, deps) {
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
  const manual = isExceptionTypeManuallySet(record)
  if (manual) {
    record.ExceptionType = type
    return record
  }
  if (canAutoSelectAttendanceOk(record, deps)) {
    record.ExceptionType = EXCEPTION_TYPE.ATTENDANCE_OK
    return record
  }
  // 非精确对齐班次 / 有识别说明标签：清掉自动或陈旧的「考勤正确」
  if (type === EXCEPTION_TYPE.ATTENDANCE_OK) {
    record.ExceptionType = ''
    return record
  }
  record.ExceptionType = type
  return record
}

function isExceptionTypeSelectDisabled(record, deps) {
  if (!record) return true
  if (isExceptionTypeExempt(record, deps && deps.isAbsentRow)) return true
  if (deps && typeof deps.hasRequiredMissing === 'function' && deps.hasRequiredMissing(record)) {
    return true
  }
  if (deps && typeof deps.hasFormatInvalid === 'function' && deps.hasFormatInvalid(record)) {
    return true
  }
  return false
}

/** 校准红框：日期 / 班次 / 到达 / 离开 / 休息 */
const CALIBRATION_HIGHLIGHT_FIELDS = [
  'Date',
  'HORAIRES_DU_TRAVAIL',
  'ARRIVEE',
  'DEPAR',
  'PAUSE',
]

/** 考勤正确时：配置内必填字段只读 */
function areRequiredFieldsLocked(record) {
  return normalizeExceptionType(record && record.ExceptionType) === EXCEPTION_TYPE.ATTENDANCE_OK
}

/** 识别错误 / 纸质错误：需要红框提示校准 */
function shouldHighlightRequiredForCalibration(record) {
  const type = normalizeExceptionType(record && record.ExceptionType)
  return type === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG || type === EXCEPTION_TYPE.PAPER_WRONG_TIME
}

function isCalibrationHighlightField(field) {
  return CALIBRATION_HIGHLIGHT_FIELDS.indexOf(String(field || '')) !== -1
}

function shouldHighlightFieldForCalibration(record, field) {
  return shouldHighlightRequiredForCalibration(record) && isCalibrationHighlightField(field)
}

function canEditRequiredFields(record) {
  const type = normalizeExceptionType(record && record.ExceptionType)
  if (!type) return true
  if (type === EXCEPTION_TYPE.ATTENDANCE_OK) return false
  return true
}

function snapshotAttendanceFields(record, fieldKeys) {
  const keys = Array.isArray(fieldKeys) && fieldKeys.length ? fieldKeys : SNAPSHOT_FIELD_KEYS
  const out = {}
  keys.forEach((key) => {
    const value = record ? record[key] : undefined
    out[key] = value === undefined || value === null ? '' : value
  })
  return out
}

function normalizeSnapshotValue(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

/**
 * AI 基线 vs 当前值的变更列表（用于识别说明展示「由什么改成了什么」）。
 * @returns {Array<{ field: string, from: string, to: string }>}
 */
function getBaselineFieldDiffs(record, fieldKeys) {
  if (!record || !record._aiBaseline || typeof record._aiBaseline !== 'object') return []
  const keys = Array.isArray(fieldKeys) && fieldKeys.length ? fieldKeys : SNAPSHOT_FIELD_KEYS
  const diffs = []
  keys.forEach((key) => {
    const from = normalizeSnapshotValue(record._aiBaseline[key])
    const to = normalizeSnapshotValue(record[key])
    if (from === to) return
    diffs.push({ field: key, from, to })
  })
  return diffs
}

function ensureAiBaseline(record, fieldKeys) {
  if (!record) return
  if (record._aiBaseline && typeof record._aiBaseline === 'object') return
  record._aiBaseline = snapshotAttendanceFields(record, fieldKeys)
}

function updateLastEditSnapshot(record, fieldKeys) {
  if (!record) return
  record._lastEditSnapshot = snapshotAttendanceFields(record, fieldKeys)
}

/**
 * 切入「纸质正确、识别错误 / 纸质错误」时拍 AI 基线。
 * 字段变更时只刷新最终快照，禁止在变更后补拍基线（否则会变成「改后=基线」看不到 diff）。
 */
function onExceptionTypeChange(record, nextType, fieldKeys) {
  if (!record) return
  const normalized = normalizeExceptionType(nextType)
  record.ExceptionType = normalized
  // 仅用户点选才算手动确认；用于区分历史自动「考勤正确」
  record._exceptionTypeManual = Boolean(normalized)
  if (normalized === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG || normalized === EXCEPTION_TYPE.PAPER_WRONG_TIME) {
    ensureAiBaseline(record, fieldKeys)
    updateLastEditSnapshot(record, fieldKeys)
  }
}

/** 编辑前（focus）补拍基线：必须在值被改掉之前调用 */
function onCalibratableFieldFocus(record, fieldKeys) {
  if (!record) return
  const type = normalizeExceptionType(record.ExceptionType)
  if (type !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG && type !== EXCEPTION_TYPE.PAPER_WRONG_TIME) return
  ensureAiBaseline(record, fieldKeys)
}

function onCalibratableFieldChange(record, fieldKeys) {
  if (!record) return
  const type = normalizeExceptionType(record.ExceptionType)
  if (type !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG && type !== EXCEPTION_TYPE.PAPER_WRONG_TIME) return
  // 仅更新最终快照；基线应已在 load / 选类型 / focus 时拍好
  updateLastEditSnapshot(record, fieldKeys)
}

function isExceptionTypeMissingForSubmit(record, deps) {
  if (isExceptionTypeExempt(record, deps && deps.isAbsentRow)) return false
  return !normalizeExceptionType(record && record.ExceptionType)
}

/** 相对 AI 基线，班次/到达/离开/休息是否至少改过一项 */
function hasOcrWrongTimeFieldAdjusted(record) {
  return getBaselineFieldDiffs(record, OCR_WRONG_ADJUST_FIELDS).length > 0
}

/**
 * 选了「识别错误」但班次/到达/离开/休息均未相对 AI 基线改动 → 提交拦截。
 * 未出勤/删除行豁免。
 */
function isOcrWrongMissingFieldAdjustment(record, deps) {
  if (isExceptionTypeExempt(record, deps && deps.isAbsentRow)) return false
  if (normalizeExceptionType(record && record.ExceptionType) !== EXCEPTION_TYPE.PAPER_OK_OCR_WRONG) {
    return false
  }
  return !hasOcrWrongTimeFieldAdjusted(record)
}

module.exports = {
  EXCEPTION_TYPE,
  EXCEPTION_TYPE_VALUES,
  EXCEPTION_TYPE_I18N_KEYS,
  EXCEPTION_TYPE_SHORT_I18N_KEYS,
  SNAPSHOT_FIELD_KEYS,
  CALIBRATION_HIGHLIGHT_FIELDS,
  OCR_WRONG_ADJUST_FIELDS,
  isValidExceptionType,
  normalizeExceptionType,
  isExceptionTypeExempt,
  ensureExceptionType,
  matchesShiftExactTimes,
  canAutoSelectAttendanceOk,
  recordHasRecognitionTags,
  isExceptionTypeSelectDisabled,
  isExceptionTypeManuallySet,
  areRequiredFieldsLocked,
  shouldHighlightRequiredForCalibration,
  isCalibrationHighlightField,
  shouldHighlightFieldForCalibration,
  canEditRequiredFields,
  snapshotAttendanceFields,
  getBaselineFieldDiffs,
  ensureAiBaseline,
  updateLastEditSnapshot,
  onExceptionTypeChange,
  onCalibratableFieldFocus,
  onCalibratableFieldChange,
  isExceptionTypeMissingForSubmit,
  hasOcrWrongTimeFieldAdjusted,
  isOcrWrongMissingFieldAdjustment,
}
