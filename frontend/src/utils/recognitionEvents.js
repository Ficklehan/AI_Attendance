/** @see shared/js/recognitionEvents.cjs */
import * as eventsMod from '@shared/recognitionEvents.cjs'
import { importSharedCjs } from './importSharedCjs'

const events = importSharedCjs(eventsMod)

export const emptyRecognitionState = events.emptyRecognitionState
export const applyRecognitionEvents = events.applyRecognitionEvents
export const isRecognitionTerminal = events.isRecognitionTerminal
export const toLiveRowPreview = events.toLiveRowPreview
