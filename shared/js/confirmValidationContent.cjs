const { groupConfirmValidationIssues } = require('./confirmValidationGrouping.cjs')

const GROUP_HINT_KEYS = {
  exceptionType: 'confirmExceptionTypeValidationHint',
  ocrCalibration: 'confirmOcrCalibrationValidationHint',
}

/**
 * @param {Array<object>} issues
 * @param {(key: string, params?: object) => string} translate
 * @param {(fieldKeys: string[]) => string} formatFieldLabels
 */
function buildSubmitValidationViewModel(issues, translate, formatFieldLabels) {
  const groups = groupConfirmValidationIssues(issues)
  const lineCount = new Set((issues || []).map((issue) => issue.line)).size

  return {
    title: translate('submitValidationTitle'),
    summary: translate('submitValidationSummary', { count: lineCount }),
    groups: groups.map((group, index) => {
      const issueType = group.issueType || 'missing'
      let title = ''
      if (issueType === 'exceptionType') {
        title = translate('confirmExceptionTypeValidationGroupHeader', { count: group.count })
      } else if (issueType === 'ocrCalibration') {
        title = translate('confirmOcrCalibrationValidationGroupHeader', { count: group.count })
      } else if (issueType === 'format') {
        title = translate('confirmFormatValidationGroupHeader', {
          fields: formatFieldLabels(group.fields),
          count: group.count,
        })
      } else {
        title = translate('confirmValidationGroupHeader', {
          fields: formatFieldLabels(group.fields),
          count: group.count,
        })
      }

      const hintKey = GROUP_HINT_KEYS[issueType]
      let hint = hintKey ? translate(hintKey) : ''
      if (hint === hintKey) hint = ''

      let records = translate('confirmValidationGroupLines', { ranges: group.lineRanges })
      if (records === 'confirmValidationGroupLines') records = group.lineRanges

      return {
        id: `${issueType}-${index}`,
        issueType,
        title,
        hint,
        records,
      }
    }),
  }
}

module.exports = {
  buildSubmitValidationViewModel,
}
