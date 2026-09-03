/**
 * 小程序异常类型 UI：选项文案、ensure 依赖、班次偏差句。
 * 注意：勿顶层 require recordDisplay，避免与 enrichRecord 循环依赖。
 */
const { t } = require('./i18n')
const { hasRequiredMissing } = require('./requiredRecordFields')
const { getInvalidFormatFieldKeys } = require('./recordFieldFormatRules')
const {
  EXCEPTION_TYPE,
  EXCEPTION_TYPE_VALUES,
  EXCEPTION_TYPE_I18N_KEYS,
  EXCEPTION_TYPE_SHORT_I18N_KEYS,
  SNAPSHOT_FIELD_KEYS,
  CALIBRATION_HIGHLIGHT_FIELDS,
  ensureExceptionType,
  onExceptionTypeChange,
  isExceptionTypeSelectDisabled,
  isExceptionTypeExempt,
  isExceptionTypeMissingForSubmit,
  normalizeExceptionType,
  canEditRequiredFields,
  shouldHighlightFieldForCalibration,
  onCalibratableFieldFocus,
  onCalibratableFieldChange,
} = require('../shared-js/exceptionTypeCore')
const {
  computeShiftVarianceMinutes,
  formatShiftVariancePhrases,
  joinShiftVarianceSentence,
  splitDurationMinutes,
} = require('../shared-js/shiftVarianceCore')

const EXCEPTION_TYPE_UNLOCKED_FIELDS = new Set([
  'NOM_PRENOM',
  'Entrepot',
  'AGENCE_INTERIMAIRE',
])

const SHIFT_PHRASE_I18N = {
  earlyArrival: 'result.shiftVarianceEarlyArrival',
  lateArrival: 'result.shiftVarianceLateArrival',
  earlyLeave: 'result.shiftVarianceEarlyLeave',
  overtime: 'result.shiftVarianceOvertime',
}

function mpKey(taskEditKey) {
  return String(taskEditKey || '').replace(/^taskEdit\./, 'result.')
}

function getIsAbsentRow() {
  return require('./recordDisplay').isAbsentRow
}

function hasFormatInvalid(record) {
  return getInvalidFormatFieldKeys(record).length > 0
}

function buildExceptionTypeDeps() {
  return {
    hasRequiredMissing,
    hasFormatInvalid,
    isAbsentRow: getIsAbsentRow(),
  }
}

function formatShiftDurationLabel(totalMinutes) {
  const parts = splitDurationMinutes(totalMinutes) || { total: 0, hours: 0, minutes: 0 }
  const total = Number(parts.total) || 0
  const hours = Number(parts.hours) || 0
  const minutes = Number(parts.minutes) || 0
  if (total < 60) {
    const text = t('result.shiftVarianceDurationMinutes', { minutes: total })
    return text !== 'result.shiftVarianceDurationMinutes' ? text : `${total} min`
  }
  if (minutes === 0) {
    const text = t('result.shiftVarianceDurationHours', { hours })
    return text !== 'result.shiftVarianceDurationHours' ? text : `${hours} h`
  }
  const text = t('result.shiftVarianceDurationHoursMinutes', { hours, minutes })
  return text !== 'result.shiftVarianceDurationHoursMinutes'
    ? text
    : `${hours} h ${minutes} min`
}

function computeShiftVarianceSentence(record) {
  const isAbsentRow = getIsAbsentRow()
  if (!record || record.isDeleted || isAbsentRow(record)) return ''
  try {
    const variance = computeShiftVarianceMinutes(record)
    const phrases = formatShiftVariancePhrases(variance, (key, minutes) => {
      const i18nKey = SHIFT_PHRASE_I18N[key]
      const duration = formatShiftDurationLabel(minutes)
      if (!i18nKey) return `${key} ${duration}`
      const translated = t(i18nKey, { duration, minutes })
      return translated && translated !== i18nKey ? translated : `${key} ${duration}`
    })
    if (!phrases || !phrases.length) return ''
    const prefix = t('result.shiftVariancePrefix')
    const join = t('result.shiftVarianceJoin')
    return joinShiftVarianceSentence(phrases, {
      prefix: prefix !== 'result.shiftVariancePrefix' ? prefix : '员工',
      join: join !== 'result.shiftVarianceJoin' ? join : '且',
    })
  } catch (e) {
    return ''
  }
}

function buildExceptionTypeOptions() {
  return EXCEPTION_TYPE_VALUES.map((value) => {
    const shortKey = mpKey(EXCEPTION_TYPE_SHORT_I18N_KEYS[value])
    const longKey = mpKey(EXCEPTION_TYPE_I18N_KEYS[value])
    const label = t(shortKey)
    const title = t(longKey)
    return {
      value,
      label: label !== shortKey ? label : value,
      title: title !== longKey ? title : label,
    }
  })
}

function exceptionTypeDisabledHint(record, deps) {
  if (!isExceptionTypeSelectDisabled(record, deps)) return ''
  if (deps.hasRequiredMissing(record)) {
    const text = t('result.exceptionTypeDisabledMissing')
    return text !== 'result.exceptionTypeDisabledMissing' ? text : ''
  }
  if (deps.hasFormatInvalid(record)) {
    const text = t('result.exceptionTypeDisabledFormat')
    return text !== 'result.exceptionTypeDisabledFormat' ? text : ''
  }
  return ''
}

function exceptionTypeDisplayLabel(record) {
  if (isExceptionTypeExempt(record, getIsAbsentRow())) return '-'
  const type = normalizeExceptionType(record && record.ExceptionType)
  if (!type) {
    const text = t('result.exceptionTypePlaceholder')
    return text !== 'result.exceptionTypePlaceholder' ? text : ''
  }
  const key = mpKey(EXCEPTION_TYPE_SHORT_I18N_KEYS[type] || EXCEPTION_TYPE_I18N_KEYS[type])
  const text = t(key)
  return text !== key ? text : type
}

function isCalibFieldEditable(record, field) {
  const isAbsentRow = getIsAbsentRow()
  if (!record || record.isDeleted || isAbsentRow(record)) return false
  if (EXCEPTION_TYPE_UNLOCKED_FIELDS.has(field)) return true
  const formatInvalid = getInvalidFormatFieldKeys(record).indexOf(field) !== -1
  if (formatInvalid) return true
  if (!canEditRequiredFields(record)) {
    // 与 PC 一致：考勤正确时锁配置内必填（未解锁字段）
    const { isConfiguredRequiredField } = require('./requiredRecordFields')
    if (isConfiguredRequiredField(field)) return false
  }
  return true
}

function countPendingExceptionTypes(records) {
  const deps = buildExceptionTypeDeps()
  let count = 0
  ;(records || []).forEach((record) => {
    if (isExceptionTypeMissingForSubmit(record, deps)) count += 1
  })
  return count
}

function syncRecordsExceptionType(records) {
  const deps = buildExceptionTypeDeps()
  ;(records || []).forEach((record) => {
    ensureExceptionType(record, deps)
  })
  return records
}

module.exports = {
  EXCEPTION_TYPE,
  EXCEPTION_TYPE_VALUES,
  SNAPSHOT_FIELD_KEYS,
  CALIBRATION_HIGHLIGHT_FIELDS,
  EXCEPTION_TYPE_UNLOCKED_FIELDS,
  buildExceptionTypeDeps,
  buildExceptionTypeOptions,
  ensureExceptionType,
  onExceptionTypeChange,
  isExceptionTypeSelectDisabled,
  isExceptionTypeExempt,
  isExceptionTypeMissingForSubmit,
  normalizeExceptionType,
  canEditRequiredFields,
  shouldHighlightFieldForCalibration,
  onCalibratableFieldFocus,
  onCalibratableFieldChange,
  exceptionTypeDisabledHint,
  exceptionTypeDisplayLabel,
  isCalibFieldEditable,
  countPendingExceptionTypes,
  syncRecordsExceptionType,
  computeShiftVarianceSentence,
  hasFormatInvalid,
}
