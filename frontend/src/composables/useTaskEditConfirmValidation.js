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

/**
 * TaskEdit 确认提交必填校验
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

  const requiredInputClass = (record, fieldKey) => ({
    'required-empty': isRequiredFieldEmpty(record, fieldKey),
  })

  const requiredTextClass = (record, fieldKey) => ({
    'cell-text': true,
    'required-empty-display': isRequiredFieldEmpty(record, fieldKey),
  })

  const formatFieldLabels = (fieldKeys) =>
    (fieldKeys || [])
      .map((key) => t(REQUIRED_FIELD_I18N_KEYS[key] || key))
      .join(t('taskEdit.confirmValidationFieldSep'))

  const formatConfirmValidationContent = (issues) => {
    const groups = groupConfirmValidationIssues(issues)
    const summary = t('taskEdit.confirmValidationSummary', { count: issues.length })
    const groupLines = groups.map((group) => {
      const header = t('taskEdit.confirmValidationGroupHeader', {
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
      title: t('taskEdit.confirmValidationTitle'),
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
    const issues = collectConfirmValidationIssues(preparedRecords)
    if (issues.length > 0) {
      showConfirmValidationModal(issues)
      return false
    }
    return true
  }

  return {
    confirmRequiredFields,
    applyConfirmValidationConfig,
    isConfiguredRequiredField,
    isRequiredFieldEmpty,
    requiredInputClass,
    requiredTextClass,
    validateBeforeConfirm,
    collectConfirmValidationIssues,
    formatConfirmValidationContent,
    showConfirmValidationModal,
  }
}
