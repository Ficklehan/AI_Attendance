/** @see shared/js/recordFieldFormatRules.cjs */
import { markContains } from './recognitionLabels'
import { isPlaceholderValue } from './fieldPlaceholder'
import * as formatMod from '@shared/recordFieldFormatRules.cjs'
import { importSharedCjs } from './importSharedCjs'

const formatShared = importSharedCjs(formatMod)
const api = formatShared.createRecordFieldFormatRules({ isPlaceholderValue, markContains })

export const FORMAT_FIELD_KEYS = api.FORMAT_FIELD_KEYS
export const isFieldFormatInvalid = api.isFieldFormatInvalid
export const getInvalidFormatFieldKeys = api.getInvalidFormatFieldKeys
export const collectFormatValidationIssues = api.collectFormatValidationIssues
export const collectSubmitValidationIssues = api.collectSubmitValidationIssues
export const countSubmitBlockerLines = api.countSubmitBlockerLines

export const FORMAT_FIELD_I18N_KEYS = {
  Date: 'taskEdit.date',
  HORAIRES_DU_TRAVAIL: 'taskEdit.shift',
  ARRIVEE: 'taskEdit.arrival',
  DEPAR: 'taskEdit.departure',
}
