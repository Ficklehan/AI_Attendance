const { collectConfirmValidationIssues } = require('./requiredRecordFields')
const { groupConfirmValidationIssues } = require('../shared-js/confirmValidationGrouping')

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

const MAX_MODAL_CHARS = 900

function formatFieldLabels(fieldKeys, t) {
  return (fieldKeys || [])
    .map((key) => t(REQUIRED_FIELD_I18N_KEYS[key] || key))
    .join(t('result.confirmValidationFieldSep'))
}

function formatConfirmValidationContent(issues, t) {
  const groups = groupConfirmValidationIssues(issues)
  const summary = t('result.confirmValidationSummary', { count: issues.length })
  const groupLines = groups.map((group) => {
    const header = t('result.confirmValidationGroupHeader', {
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

function countRequiredMissing(records) {
  return collectConfirmValidationIssues(records).length
}

function showRequiredValidationModal(records, t) {
  const issues = collectConfirmValidationIssues(records)
  if (!issues.length) return false
  tt.showModal({
    title: t('result.confirmValidationTitle'),
    content: truncateModalContent(formatConfirmValidationContent(issues, t)),
    showCancel: false,
    confirmText: t('common.confirm'),
  })
  return true
}

module.exports = {
  collectConfirmValidationIssues,
  countRequiredMissing,
  formatConfirmValidationContent,
  truncateModalContent,
  showRequiredValidationModal,
}
