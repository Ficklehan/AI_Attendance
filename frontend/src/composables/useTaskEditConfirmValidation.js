import { ref, h } from 'vue'
import { Modal as aModal } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  groupConfirmValidationIssues,
} from '@/utils/confirmValidationGrouping'
import {
  getMissingRequiredFieldKeys,
  REQUIRED_FIELD_I18N_KEYS,
  collectConfirmValidationIssues,
  setConfirmValidationConfig,
  getConfirmValidationConfig,
  isConfiguredRequiredField,
  DEFAULT_CONFIRM_VALIDATION,
} from '@/utils/requiredRecordFields'
import {
  collectSubmitValidationIssues,
  countSubmitBlockerLines,
  isFieldFormatInvalid,
  FORMAT_FIELD_I18N_KEYS,
} from '@/utils/recordFieldFormatRules'

/**
 * TaskEdit 确认提交必填 + 格式校验
 */
export function useTaskEditConfirmValidation() {
  const { t } = useI18n()
  const confirmRequiredFields = ref([...DEFAULT_CONFIRM_VALIDATION.requiredFields])

  const applyConfirmValidationConfig = (config) => {
    setConfirmValidationConfig(config)
    confirmRequiredFields.value = [...getConfirmValidationConfig().requiredFields]
  }

  const isRequiredFieldEmpty = (record, fieldKey) => {
    if (!isConfiguredRequiredField(fieldKey)) return false
    return getMissingRequiredFieldKeys(record).includes(fieldKey)
  }

  const isFormatFieldInvalid = (record, fieldKey) => isFieldFormatInvalid(record, fieldKey)

  const requiredInputClass = (record, fieldKey) => ({
    'required-empty': isRequiredFieldEmpty(record, fieldKey),
    'format-invalid': isFormatFieldInvalid(record, fieldKey),
  })

  const requiredTextClass = (record, fieldKey) => ({
    'cell-text': true,
    'required-empty-display': isRequiredFieldEmpty(record, fieldKey),
    'format-invalid-display': isFormatFieldInvalid(record, fieldKey),
  })

  const fieldLabelKey = (fieldKey) =>
    REQUIRED_FIELD_I18N_KEYS[fieldKey] || FORMAT_FIELD_I18N_KEYS[fieldKey] || fieldKey

  const formatFieldLabels = (fieldKeys) =>
    (fieldKeys || [])
      .map((key) => t(fieldLabelKey(key)))
      .join(t('taskEdit.confirmValidationFieldSep'))

  const formatConfirmValidationContent = (issues) => {
    const groups = groupConfirmValidationIssues(issues)
    const lineCount = new Set((issues || []).map((issue) => issue.line)).size
    const summary = t('taskEdit.submitValidationSummary', { count: lineCount })
    const groupLines = groups.map((group) => {
      const headerKey = group.issueType === 'format'
        ? 'taskEdit.confirmFormatValidationGroupHeader'
        : 'taskEdit.confirmValidationGroupHeader'
      const header = t(headerKey, {
        fields: formatFieldLabels(group.fields),
        count: group.count,
      })
      const lines = t('taskEdit.confirmValidationGroupLines', { ranges: group.lineRanges })
      return `${header}\n  ${lines}`
    })
    return `${summary}\n\n${groupLines.join('\n\n')}`
  }

  const showConfirmValidationModal = (issues) => {
    const content = formatConfirmValidationContent(issues)
    aModal.error({
      title: t('taskEdit.submitValidationTitle'),
      content: h('div', {
        style: {
          maxHeight: '360px',
          overflowY: 'auto',
          whiteSpace: 'pre-wrap',
          lineHeight: '1.6',
          fontSize: '13px',
        },
      }, content),
      width: 560,
      okText: t('common.confirm'),
    })
  }

  const validateBeforeConfirm = (preparedRecords) => {
    const issues = collectSubmitValidationIssues(preparedRecords, collectConfirmValidationIssues)
    if (issues.length > 0) {
      showConfirmValidationModal(issues)
      return false
    }
    return true
  }

  const countSubmitValidationLines = (records) =>
    countSubmitBlockerLines(records, collectConfirmValidationIssues)

  return {
    confirmRequiredFields,
    applyConfirmValidationConfig,
    isConfiguredRequiredField,
    isRequiredFieldEmpty,
    isFormatFieldInvalid,
    requiredInputClass,
    requiredTextClass,
    validateBeforeConfirm,
    collectConfirmValidationIssues,
    collectSubmitValidationIssues,
    countSubmitValidationLines,
    formatConfirmValidationContent,
    showConfirmValidationModal,
  }
}
