const { collectConfirmValidationIssues } = require('./requiredRecordFields')
const { groupConfirmValidationIssues } = require('../shared-js/confirmValidationGrouping')
const {
  collectSubmitValidationIssues,
  countSubmitBlockerLines,
} = require('./recordFieldFormatRules')

const REQUIRED_FIELD_I18N_KEYS = {
  Pays: 'result.fieldCountry',
  Entrepot: 'result.fieldWarehouse',
  Date: 'result.fieldDate',
  NOM_PRENOM: 'result.fieldName',
  AGENCE_INTERIMAIRE: 'result.fieldAgency',
  HORAIRES_DU_TRAVAIL: 'result.fieldShift',
  ARRIVEE: 'result.fieldArrival',
  DEPAR: 'result.fieldDeparture',
  PAUSE: 'result.fieldBreakMinutes',
}

const FORMAT_FIELD_I18N_KEYS = {
  Date: 'result.fieldDate',
  HORAIRES_DU_TRAVAIL: 'result.fieldShift',
  ARRIVEE: 'result.fieldArrival',
  DEPAR: 'result.fieldDeparture',
}

const MAX_MODAL_CHARS = 900

function fieldLabelKey(fieldKey) {
  return REQUIRED_FIELD_I18N_KEYS[fieldKey] || FORMAT_FIELD_I18N_KEYS[fieldKey] || fieldKey
}

function formatFieldLabels(fieldKeys, t) {
  return (fieldKeys || [])
    .map((key) => t(fieldLabelKey(key)))
    .join(t('result.confirmValidationFieldSep'))
}

function formatConfirmValidationContent(issues, t) {
  const groups = groupConfirmValidationIssues(issues)
  const lineCount = new Set((issues || []).map((issue) => issue.line)).size
  const summary = t('result.submitValidationSummary', { count: lineCount })
  const groupLines = groups.map((group) => {
    const headerKey = group.issueType === 'format'
      ? 'result.confirmFormatValidationGroupHeader'
      : 'result.confirmValidationGroupHeader'
    const header = t(headerKey, {
      fields: formatFieldLabels(group.fields, t),
      count: group.count,
    })
    const lines = t('result.confirmValidationGroupLines', { ranges: group.lineRanges })
    return `${header}\n${lines}`
  })
  return `${summary}\n\n${groupLines.join('\n\n')}`
}

function truncateModalContent(content) {
  if (!content || content.length <= MAX_MODAL_CHARS) return content
  return `${content.slice(0, MAX_MODAL_CHARS)}\n…`
}

function countSubmitValidationLines(records) {
  return countSubmitBlockerLines(records, collectConfirmValidationIssues)
}

function findFirstValidationIssue(records) {
  const issues = collectSubmitValidationIssues(records, collectConfirmValidationIssues)
  if (!issues.length) return null
  const first = issues[0]
  const record = records[first.line - 1]
  const field = (first.fields && first.fields[0]) || null
  return {
    line: first.line,
    field,
    rowKey: record && record._rowKey ? record._rowKey : null,
  }
}

function showSubmitValidationModal(records, t, options) {
  const opts = options || {}
  const issues = collectSubmitValidationIssues(records, collectConfirmValidationIssues)
  if (!issues.length) return false
  if (typeof opts.beforeModal === 'function') {
    opts.beforeModal(findFirstValidationIssue(records), issues)
  }
  tt.showModal({
    title: t('result.submitValidationTitle'),
    content: truncateModalContent(formatConfirmValidationContent(issues, t)),
    showCancel: false,
    confirmText: t('common.confirm'),
  })
  return true
}

module.exports = {
  collectConfirmValidationIssues,
  collectSubmitValidationIssues,
  countSubmitValidationLines,
  formatConfirmValidationContent,
  truncateModalContent,
  showSubmitValidationModal,
  showRequiredValidationModal: showSubmitValidationModal,
  countRequiredMissing: countSubmitValidationLines,
  findFirstValidationIssue,
}
