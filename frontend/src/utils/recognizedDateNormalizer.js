/** @see shared/js/recognizedDateNormalizer.cjs */
import * as dateMod from '@shared/recognizedDateNormalizer.cjs'
import { importSharedCjs } from './importSharedCjs'

const date = importSharedCjs(dateMod)

export const normalizeDate = date.normalizeDate
export const isValidCanonicalDate = date.isValidCanonicalDate
export const isDateFormatInvalid = date.isDateFormatInvalid
