import { ref } from 'vue'
import { Modal as aModal } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
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

  const formatValidationIssueLine = (issue) => {
    const fieldLabels = (issue.fields || [])
      .map((key) => t(REQUIRED_FIELD_I18N_KEYS[key] || key))
      .join(t('taskEdit.confirmValidationFieldSep'))
    return t('taskEdit.confirmValidationLine', {
      line: issue.line,
      fields: fieldLabels,
    })
  }

  const showConfirmValidationModal = (issues) => {
    const lines = issues.map((issue) => formatValidationIssueLine(issue))
    aModal.error({
      title: t('taskEdit.confirmValidationTitle'),
      content: `${t('taskEdit.confirmValidationSummary', { count: issues.length })}\n\n${lines.join('\n')}`,
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
  }
}
