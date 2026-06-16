/** @see shared/js/fieldPlaceholder.cjs */
import * as fpMod from '@shared/fieldPlaceholder.cjs'
import { importSharedCjs } from './importSharedCjs'

const fp = importSharedCjs(fpMod)

export const isPlaceholderValue = fp.isPlaceholderValue
export const isExplicitUnreadableValue = fp.isExplicitUnreadableValue
export const sanitizeFieldValue = fp.sanitizeFieldValue
export const displayFieldValue = fp.displayFieldValue
export const RECORD_TEXT_FIELD_KEYS = fp.RECORD_TEXT_FIELD_KEYS
export const sanitizeRecordPlaceholders = fp.sanitizeRecordPlaceholders
export const prepareRecordPlaceholders = fp.prepareRecordPlaceholders
export const isFieldUnreadable = fp.isFieldUnreadable
export const clearFieldUnreadable = fp.clearFieldUnreadable
export const stripRecordMetadata = fp.stripRecordMetadata
