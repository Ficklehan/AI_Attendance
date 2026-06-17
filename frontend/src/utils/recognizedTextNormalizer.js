/** @see shared/js/recognizedTextNormalizer.cjs */
import * as textMod from '@shared/recognizedTextNormalizer.cjs'
import { importSharedCjs } from './importSharedCjs'

const text = importSharedCjs(textMod)

export const normalizeWorkerNo = text.normalizeWorkerNo
export const isWorkerNoExtractable = text.isWorkerNoExtractable
export const normalizePersonName = text.normalizePersonName
export const normalizeLabelText = text.normalizeLabelText
export const normalizeRecordTextFields = text.normalizeRecordTextFields
