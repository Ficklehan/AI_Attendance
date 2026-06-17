/** @see shared/js/recordFieldFormatRules.cjs */
import { markContains } from './recognitionLabels'
import { isPlaceholderValue } from './fieldPlaceholder'
import * as formatMod from '@shared/recordFieldFormatRules.cjs'
import { importSharedCjs } from './importSharedCjs'

const formatShared = importSharedCjs(formatMod)
const formatDeps = { isPlaceholderValue, markContains }

let api

function getApi() {
  if (!api) {
    const factory = formatShared.createRecordFieldFormatRules
    if (typeof factory !== 'function') {
      throw new Error('recordFieldFormatRules: createRecordFieldFormatRules is not available')
    }
    api = factory(formatDeps)
  }
  return api
}

export const FORMAT_FIELD_KEYS = formatShared.FORMAT_FIELD_KEYS

export function isArrivalDepartureSameTime(record) {
  const standalone = formatShared.isArrivalDepartureSameTime
  if (typeof standalone === 'function') {
    return standalone(record, formatDeps)
  }
  const fn = getApi().isArrivalDepartureSameTime
  return typeof fn === 'function' ? fn(record) : false
}

export function isFieldFormatInvalid(record, fieldKey) {
  if ((fieldKey === 'ARRIVEE' || fieldKey === 'DEPAR') && isArrivalDepartureSameTime(record)) {
    return true
  }
  return getApi().isFieldFormatInvalid(record, fieldKey)
}

export function getInvalidFormatFieldKeys(record) {
  return getApi().getInvalidFormatFieldKeys(record)
}

export function collectFormatValidationIssues(records) {
  return getApi().collectFormatValidationIssues(records)
}

export function collectSubmitValidationIssues(records, collectRequiredIssues) {
  return getApi().collectSubmitValidationIssues(records, collectRequiredIssues)
}

export function countSubmitBlockerLines(records, collectRequiredIssues) {
  return getApi().countSubmitBlockerLines(records, collectRequiredIssues)
}

export const FORMAT_FIELD_I18N_KEYS = {
  Date: 'taskEdit.date',
  HORAIRES_DU_TRAVAIL: 'taskEdit.shift',
  ARRIVEE: 'taskEdit.arrival',
  DEPAR: 'taskEdit.departure',
}
