/** @see shared/js/confirmValidationContent.cjs */
import * as mod from '@shared/confirmValidationContent.cjs'
import { importSharedCjs } from './importSharedCjs'

const api = importSharedCjs(mod) || {}

export const buildSubmitValidationViewModel = api.buildSubmitValidationViewModel
