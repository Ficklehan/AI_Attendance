/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 确认提交必填字段校验（纯逻辑，依赖注入 isPlaceholderValue / markContains）
 * PC Web 与飞书小程序共用
 */

const {
  formatLineRanges,
  groupConfirmValidationIssues,
} = require('./confirmValidationGrouping')
const {
  isExceptionTypeMissingForSubmit,
  isOcrWrongMissingFieldAdjustment,
  OCR_WRONG_ADJUST_FIELDS,
} = require('./exceptionTypeCore')

const CONFIRM_FIELD_KEYS = [
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

const DEFAULT_CONFIRM_VALIDATION = {
  scope: 'except_deleted_absent',
  requiredFields: ['NOM_PRENOM', 'Date', 'ARRIVEE', 'DEPAR', 'PAUSE'],
}

const FIELD_ALIASES = {
  NOM_PRENOM: ['NOM_PRENOM', 'Name'],
  Date: ['Date', 'WorkDate'],
  DEPAR: ['DEPAR', 'DEPART'],
}

function pickField(record, keys) {
  if (!record) return ''
  for (let i = 0; i < keys.length; i++) {
    const v = record[keys[i]]
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      return String(v).trim()
    }
  }
  return ''
}

/**
 * @param {{ isPlaceholderValue: (v: unknown) => boolean, markContains: (mark: string, kind: string) => boolean }} deps
 */
function createRequiredRecordFields(deps) {
  if (!deps || typeof deps.isPlaceholderValue !== 'function' || typeof deps.markContains !== 'function') {
    throw new Error('createRequiredRecordFields requires isPlaceholderValue and markContains')
  }

  const { isPlaceholderValue, markContains } = deps
  const REQUIRED_SUBMIT_FIELD_KEYS = DEFAULT_CONFIRM_VALIDATION.requiredFields.slice()

  let activeConfig = {
    scope: DEFAULT_CONFIRM_VALIDATION.scope,
    requiredFields: DEFAULT_CONFIRM_VALIDATION.requiredFields.slice(),
  }

  function syncRequiredSubmitFieldKeys(fields) {
    REQUIRED_SUBMIT_FIELD_KEYS.length = 0
    Array.prototype.push.apply(REQUIRED_SUBMIT_FIELD_KEYS, fields)
  }

  function setConfirmValidationConfig(config) {
    if (!config) {
      activeConfig = {
        scope: DEFAULT_CONFIRM_VALIDATION.scope,
        requiredFields: DEFAULT_CONFIRM_VALIDATION.requiredFields.slice(),
      }
      syncRequiredSubmitFieldKeys(activeConfig.requiredFields)
      return
    }
    const fields = Array.isArray(config.requiredFields) && config.requiredFields.length
      ? config.requiredFields.filter((k) => CONFIRM_FIELD_KEYS.indexOf(k) !== -1)
      : DEFAULT_CONFIRM_VALIDATION.requiredFields.slice()
    activeConfig = {
      scope: config.scope || DEFAULT_CONFIRM_VALIDATION.scope,
      requiredFields: fields,
    }
    syncRequiredSubmitFieldKeys(fields)
  }

  function getConfirmValidationConfig() {
    return activeConfig
  }

  function isConfiguredRequiredField(fieldKey, config) {
    const cfg = config || activeConfig
    return (cfg.requiredFields || []).indexOf(fieldKey) !== -1
  }

  function appendRequiredMark(label) {
    return `${label} *`
  }

  function hasFilledText(value) {
    return !isPlaceholderValue(value)
  }

  function isPauseMissing(value) {
    if (value === 0 || value === '0') return false
    if (isPlaceholderValue(value)) return true
    const s = String(value).trim()
    if (!s) return true
    if (s === '00:00' || s === '0:00') return false
    return false
  }

  function getRecordMark(record) {
    return String((record && record.SmartMark) || (record && record.Mark) || '').trim()
  }

  function isRequiredValidationExempt(record, config) {
    const cfg = config || activeConfig
    if (!record) return true
    if (record.isDeleted) return true
    const mark = getRecordMark(record)
    if (markContains(mark, 'deleted')) return true
    if (!record._restored && (mark.indexOf('未出勤') !== -1 || markContains(mark, 'absent'))) return true
    return false
  }

  function isFieldMissing(record, fieldKey) {
    if (fieldKey === 'PAUSE') {
      return isPauseMissing(record.PAUSE)
    }
    const keys = FIELD_ALIASES[fieldKey] || [fieldKey]
    return !hasFilledText(pickField(record, keys))
  }

  function getMissingRequiredFieldKeys(record, config) {
    const cfg = config || activeConfig
    if (isRequiredValidationExempt(record, cfg)) return []
    return (cfg.requiredFields || []).filter((key) => isFieldMissing(record, key))
  }

  function hasRequiredMissing(record, config) {
    return getMissingRequiredFieldKeys(record, config).length > 0
  }

  function collectConfirmValidationIssues(records, config) {
    const cfg = config || activeConfig
    const isAbsentByMark = (row) => {
      const mark = getRecordMark(row)
      return mark.indexOf('未出勤') !== -1 || markContains(mark, 'absent')
    }
    const issues = []
    ;(records || []).forEach((record, index) => {
      const fields = getMissingRequiredFieldKeys(record, cfg)
      if (fields.length) {
        issues.push({
          line: index + 1,
          no: pickField(record, ['NO']) || '?',
          name: pickField(record, ['NOM_PRENOM', 'Name']),
          fields,
          issueType: 'missing',
        })
      }
      if (isExceptionTypeMissingForSubmit(record, { isAbsentRow: isAbsentByMark })) {
        issues.push({
          line: index + 1,
          no: pickField(record, ['NO']) || '?',
          name: pickField(record, ['NOM_PRENOM', 'Name']),
          fields: ['ExceptionType'],
          issueType: 'exceptionType',
        })
      }
      if (isOcrWrongMissingFieldAdjustment(record, { isAbsentRow: isAbsentByMark })) {
        issues.push({
          line: index + 1,
          no: pickField(record, ['NO']) || '?',
          name: pickField(record, ['NOM_PRENOM', 'Name']),
          fields: OCR_WRONG_ADJUST_FIELDS.slice(),
          issueType: 'ocrCalibration',
        })
      }
    })
    return issues
  }

  return {
    CONFIRM_FIELD_KEYS,
    DEFAULT_CONFIRM_VALIDATION,
    REQUIRED_SUBMIT_FIELD_KEYS,
    setConfirmValidationConfig,
    getConfirmValidationConfig,
    isConfiguredRequiredField,
    appendRequiredMark,
    isRequiredValidationExempt,
    getMissingRequiredFieldKeys,
    hasRequiredMissing,
    collectConfirmValidationIssues,
    formatLineRanges,
    groupConfirmValidationIssues,
  }
}

module.exports = {
  CONFIRM_FIELD_KEYS,
  DEFAULT_CONFIRM_VALIDATION,
  createRequiredRecordFields,
  formatLineRanges,
  groupConfirmValidationIssues,
}
