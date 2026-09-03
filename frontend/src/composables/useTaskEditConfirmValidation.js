import { ref, h } from 'vue'
import { Modal as aModal } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { buildSubmitValidationViewModel } from '@/utils/confirmValidationContent'
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
    const vm = buildSubmitValidationViewModel(
      issues,
      (key, params) => {
        const fullKey = `taskEdit.${key}`
        const text = t(fullKey, params)
        return text !== fullKey ? text : key
      },
      formatFieldLabels,
    )
    return vm
  }

  const renderValidationModalContent = (viewModel) => h('div', {
    style: {
      maxHeight: '360px',
      overflowY: 'auto',
    },
  }, [
    h('p', {
      style: {
        margin: '0 0 14px',
        color: '#64748b',
        fontSize: '13px',
        lineHeight: '1.5',
      },
    }, viewModel.summary),
    ...viewModel.groups.map((group) => h('div', {
      key: group.id,
      style: {
        marginBottom: '10px',
        padding: '10px 12px',
        background: '#f8fafc',
        borderRadius: '8px',
        border: '1px solid #e2e8f0',
      },
    }, [
      h('div', {
        style: {
          fontWeight: '600',
          fontSize: '13px',
          color: '#0f172a',
          lineHeight: '1.45',
          marginBottom: group.hint ? '4px' : '6px',
        },
      }, group.title),
      group.hint
        ? h('div', {
          style: {
            fontSize: '12px',
            color: '#64748b',
            lineHeight: '1.5',
            marginBottom: '6px',
          },
        }, group.hint)
        : null,
      h('div', {
        style: {
          fontSize: '12px',
          color: '#2563eb',
          fontWeight: '600',
        },
      }, group.records),
    ])),
  ])

  const showConfirmValidationModal = (issues) => {
    const viewModel = formatConfirmValidationContent(issues)
    aModal.error({
      title: viewModel.title,
      content: renderValidationModalContent(viewModel),
      width: 480,
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
