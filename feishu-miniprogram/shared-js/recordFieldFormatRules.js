/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 确认提交业务格式校验：班次（一对起止时间）、到达/离开（单个时间）
 * 空值仅走必填；已删除/未出勤整行豁免；固定开启。
 */

const FORMAT_FIELD_KEYS = ['Date', 'HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR']

const FIELD_ALIASES = {
  Date: ['Date', 'WorkDate'],
  ARRIVEE: ['ARRIVEE', 'arrival'],
  DEPAR: ['DEPAR', 'DEPART', 'departure'],
  HORAIRES_DU_TRAVAIL: ['HORAIRES_DU_TRAVAIL', 'shift'],
}


const { extractTimeTokenStrings, normalizeClockTime } = require('./recognizedTimeNormalizer')
const { isDateFormatInvalid: isDateValueInvalid } = require('./recognizedDateNormalizer')

function pickField(record, keys) {
  if (!record) return ''
  const list = Array.isArray(keys) ? keys : [keys]
  for (const key of list) {
    const value = record[key]
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value).trim()
    }
  }
  return ''
}

function extractTimeTokens(raw) {
  const s = String(raw || '').trim()
  if (!s) return []
  return extractTimeTokenStrings(s)
    .map((token) => {
      const normalized = normalizeClockTime(token)
      const match = normalized.match(/^(\d{1,2}):(\d{2})$/)
      if (!match) return null
      const hours = parseInt(match[1], 10)
      const minutes = parseInt(match[2], 10)
      if (hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59) {
        return { hours, minutes, raw: token }
      }
      return null
    })
    .filter(Boolean)
}

function createRecordFieldFormatRules({ isPlaceholderValue, markContains }) {
  function getRecordMark(record) {
    return String((record && record.SmartMark) || (record && record.Mark) || '').trim()
  }

  function isValidationExempt(record) {
    if (!record) return true
    if (record.isDeleted) return true
    const mark = getRecordMark(record)
    if (markContains(mark, 'deleted')) return true
    if (!record._restored && (mark.indexOf('未出勤') !== -1 || markContains(mark, 'absent'))) return true
    return false
  }

  function shouldSkipFormatCheck(value) {
    return isPlaceholderValue(value) || !String(value || '').trim()
  }

  function isShiftFormatValid(value) {
    if (shouldSkipFormatCheck(value)) return null
    return extractTimeTokens(value).length === 2
  }

  function isArrivalDepartureFormatValid(value) {
    if (shouldSkipFormatCheck(value)) return null
    return extractTimeTokens(value).length === 1
  }

  function isDateFormatValid(value) {
    if (shouldSkipFormatCheck(value)) return null
    return !isDateValueInvalid(value)
  }

  function isFieldFormatInvalid(record, fieldKey) {
    const keys = FIELD_ALIASES[fieldKey] || [fieldKey]
    const value = pickField(record, keys)
    if (fieldKey === 'Date') {
      const status = isDateFormatValid(value)
      return status === false
    }
    if (fieldKey === 'HORAIRES_DU_TRAVAIL') {
      const status = isShiftFormatValid(value)
      return status === false
    }
    if (fieldKey === 'ARRIVEE' || fieldKey === 'DEPAR') {
      const status = isArrivalDepartureFormatValid(value)
      return status === false
    }
    return false
  }

  function getInvalidFormatFieldKeys(record) {
    if (isValidationExempt(record)) return []
    return FORMAT_FIELD_KEYS.filter((key) => isFieldFormatInvalid(record, key))
  }

  function collectFormatValidationIssues(records) {
    const issues = []
    ;(records || []).forEach((record, index) => {
      const fields = getInvalidFormatFieldKeys(record)
      if (fields.length) {
        issues.push({
          line: index + 1,
          no: pickField(record, ['NO']) || '?',
          name: pickField(record, ['NOM_PRENOM', 'Name']),
          fields,
          issueType: 'format',
        })
      }
    })
    return issues
  }

  function collectSubmitValidationIssues(records, collectRequiredIssues) {
    const required = (collectRequiredIssues || (() => []))(records).map((issue) => ({
      ...issue,
      issueType: issue.issueType || 'missing',
    }))
    const format = collectFormatValidationIssues(records)
    return required.concat(format)
  }

  function countSubmitBlockerLines(records, collectRequiredIssues) {
    const issues = collectSubmitValidationIssues(records, collectRequiredIssues)
    return new Set(issues.map((issue) => issue.line)).size
  }

  return {
    FORMAT_FIELD_KEYS,
    isValidationExempt,
    isFieldFormatInvalid,
    getInvalidFormatFieldKeys,
    collectFormatValidationIssues,
    collectSubmitValidationIssues,
    countSubmitBlockerLines,
    extractTimeTokens,
    isShiftFormatValid,
    isArrivalDepartureFormatValid,
    isDateFormatValid,
  }
}

module.exports = {
  FORMAT_FIELD_KEYS,
  createRecordFieldFormatRules,
  extractTimeTokens,
}
