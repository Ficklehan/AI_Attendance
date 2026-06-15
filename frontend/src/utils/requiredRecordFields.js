/** @see shared/js/requiredRecordFields.cjs */
import { markContains } from './recognitionLabels'
import { isPlaceholderValue } from './fieldPlaceholder'
import * as requiredMod from '@shared/requiredRecordFields.cjs'
import { importSharedCjs } from './importSharedCjs'

const requiredShared = importSharedCjs(requiredMod)
const api = requiredShared.createRequiredRecordFields({ isPlaceholderValue, markContains })

export const CONFIRM_FIELD_KEYS = api.CONFIRM_FIELD_KEYS
export const DEFAULT_CONFIRM_VALIDATION = api.DEFAULT_CONFIRM_VALIDATION
export const REQUIRED_SUBMIT_FIELD_KEYS = api.REQUIRED_SUBMIT_FIELD_KEYS
export const setConfirmValidationConfig = api.setConfirmValidationConfig
export const getConfirmValidationConfig = api.getConfirmValidationConfig
export const isConfiguredRequiredField = api.isConfiguredRequiredField
export const appendRequiredMark = api.appendRequiredMark
export const isRequiredValidationExempt = api.isRequiredValidationExempt
export const getMissingRequiredFieldKeys = api.getMissingRequiredFieldKeys
export const hasRequiredMissing = api.hasRequiredMissing
export const collectConfirmValidationIssues = api.collectConfirmValidationIssues

export { formatLineRanges, groupConfirmValidationIssues } from './confirmValidationGrouping'

/** TaskEdit 提交错误详情：字段 key -> i18n key */
export const REQUIRED_FIELD_I18N_KEYS = {
  Pays: 'taskEdit.countryField',
  Entrepot: 'taskEdit.warehouse',
  Date: 'taskEdit.date',
  NOM_PRENOM: 'taskEdit.name',
  AGENCE_INTERIMAIRE: 'taskEdit.agency',
  HORAIRES_DU_TRAVAIL: 'taskEdit.shift',
  ARRIVEE: 'taskEdit.arrival',
  DEPAR: 'taskEdit.departure',
  PAUSE: 'taskEdit.breakTime',
}
