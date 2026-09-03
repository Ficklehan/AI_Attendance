/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */
/**
 * 确认提交业务格式校验：班次（一对起止时间）、到达/离开（单个时间）
 * 空值仅走必填；已删除/未出勤整行豁免；固定开启。
 */

const FORMAT_FIELD_KEYS = ['Date', 'HORAIRES_DU_TRAVAIL', 'ARRIVEE', 'DEPAR']

const FIELD_ALIASES = {
  Date: ['Date', 'WorkDate'],
  ARRIVEE: ['ARRIVEE', 'arrival', 'ArriveTime', 'ARRIVAL'],
  DEPAR: ['DEPAR', 'DEPART', 'departure', 'DepartTime', 'DEPARTURE'],
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

  // 单点录入友好：先整串归一（1 → 01:00，9:01 → 09:01），再校验
  if (!/[-–—]/.test(s)) {
    const compact = s.replace(/\s+/g, '')
    const looksLikeSingleClock = /^(?:\d{1,2}(?::\d{1,2})?|\d{1,2}[hH]\d{0,2}|\d{1,2}[,.]\d{1,2}|\d{3,4})$/i.test(compact)
    if (looksLikeSingleClock) {
      const normalized = normalizeClockTime(s)
      const match = String(normalized || '').match(/^(\d{2}):(\d{2})$/)
      if (match) {
        const hours = parseInt(match[1], 10)
        const minutes = parseInt(match[2], 10)
        if (hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59) {
          return [{ hours, minutes, raw: s }]
        }
      }
    }
    // 14:00+1 等带额外符号：禁止从子串抽时间冒充合法
    if (/[^\d:hH.,\s]/i.test(compact)) {
      return []
    }
  }

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

function isValidationExempt(record, { isPlaceholderValue, markContains }) {
  if (!record) return true
  if (record.isDeleted) return true
  const mark = String((record && record.SmartMark) || (record && record.Mark) || '').trim()
  if (markContains(mark, 'deleted')) return true
  if (!record._restored && (mark.indexOf('未出勤') !== -1 || markContains(mark, 'absent'))) return true
  return false
}

function shouldSkipFormatCheck(value, isPlaceholderValue) {
  return isPlaceholderValue(value) || !String(value || '').trim()
}

/** 到达与离开时间相同（独立导出，供各端直接调用） */
function isArrivalDepartureSameTime(record, { isPlaceholderValue, markContains }) {
  if (isValidationExempt(record, { isPlaceholderValue, markContains })) return false
  const arrive = pickField(record, FIELD_ALIASES.ARRIVEE)
  const depart = pickField(record, FIELD_ALIASES.DEPAR)
  if (shouldSkipFormatCheck(arrive, isPlaceholderValue) || shouldSkipFormatCheck(depart, isPlaceholderValue)) {
    return false
  }
  const arriveTokens = extractTimeTokens(arrive)
  const departTokens = extractTimeTokens(depart)
  if (arriveTokens.length !== 1 || departTokens.length !== 1) return false
  const a = arriveTokens[0]
  const d = departTokens[0]
  return a.hours === d.hours && a.minutes === d.minutes
}

function createRecordFieldFormatRules({ isPlaceholderValue, markContains }) {
  const deps = { isPlaceholderValue, markContains }
  const checkSameTime = (record) => isArrivalDepartureSameTime(record, deps)

  function isShiftFormatValid(value) {
    if (shouldSkipFormatCheck(value, isPlaceholderValue)) return null
    return extractTimeTokens(value).length === 2
  }

  function isArrivalDepartureFormatValid(value) {
    if (shouldSkipFormatCheck(value, isPlaceholderValue)) return null
    return extractTimeTokens(value).length === 1
  }

  function isDateFormatValid(value) {
    if (shouldSkipFormatCheck(value, isPlaceholderValue)) return null
    return !isDateValueInvalid(value)
  }

  function isSingleFieldFormatInvalid(record, fieldKey) {
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

  function isFieldFormatInvalid(record, fieldKey) {
    if ((fieldKey === 'ARRIVEE' || fieldKey === 'DEPAR') && checkSameTime(record)) {
      return true
    }
    return isSingleFieldFormatInvalid(record, fieldKey)
  }

  function getInvalidFormatFieldKeys(record) {
    if (isValidationExempt(record, deps)) return []
    const invalid = FORMAT_FIELD_KEYS.filter((key) => isSingleFieldFormatInvalid(record, key))
    if (checkSameTime(record)) {
      ;['ARRIVEE', 'DEPAR'].forEach((key) => {
        if (invalid.indexOf(key) === -1) invalid.push(key)
      })
    }
    return invalid
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
    isValidationExempt: (record) => isValidationExempt(record, deps),
    isArrivalDepartureSameTime: checkSameTime,
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
  isArrivalDepartureSameTime,
}
