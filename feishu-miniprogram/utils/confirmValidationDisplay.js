const { collectConfirmValidationIssues } = require('./requiredRecordFields')
const { buildSubmitValidationViewModel } = require('../shared-js/confirmValidationContent')
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

function translateValidationKey(t, key, params) {
  const fullKey = `result.${key}`
  const text = t(fullKey, params)
  return text !== fullKey ? text : key
}

function buildValidationViewModel(issues, t) {
  return buildSubmitValidationViewModel(
    issues,
    (key, params) => translateValidationKey(t, key, params),
    (fields) => formatFieldLabels(fields, t),
  )
}

/** @deprecated 仅供无自定义 UI 时的文本兜底 */
function formatConfirmValidationContent(issues, t) {
  const vm = buildValidationViewModel(issues, t)
  const blocks = vm.groups.map((group) => {
    const lines = [group.title]
    if (group.hint) lines.push(group.hint)
    lines.push(group.records)
    return lines.join('\n')
  })
  return `${vm.summary}\n\n${blocks.join('\n\n')}`
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

  const viewModel = buildValidationViewModel(issues, t)
  const firstIssue = findFirstValidationIssue(records)

  if (typeof opts.beforeModal === 'function') {
    opts.beforeModal(firstIssue, issues)
  }
  if (typeof opts.onShow === 'function') {
    opts.onShow(viewModel, firstIssue, issues)
    return true
  }

  tt.showModal({
    title: viewModel.title,
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
  buildValidationViewModel,
  formatConfirmValidationContent,
  truncateModalContent,
  showSubmitValidationModal,
  showRequiredValidationModal: showSubmitValidationModal,
  countRequiredMissing: countSubmitValidationLines,
  findFirstValidationIssue,
}
