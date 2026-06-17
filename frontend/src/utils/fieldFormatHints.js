/** @see shared/js/fieldFormatHints.cjs */
import * as hintsMod from '@shared/fieldFormatHints.cjs'
import { importSharedCjs } from './importSharedCjs'

const hints = importSharedCjs(hintsMod)

export const FIELD_FORMAT_HINT_KEYS = hints.FIELD_FORMAT_HINT_KEYS
export const isFormatHintField = hints.isFormatHintField
export const getFormatHintKeys = hints.getFormatHintKeys
