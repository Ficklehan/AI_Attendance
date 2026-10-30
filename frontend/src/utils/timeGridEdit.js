import * as timeGridMod from '@shared/timeGridEdit.cjs'
import { importSharedCjs } from './importSharedCjs'

const timeGrid = importSharedCjs(timeGridMod)

export const TIME_GRID_FIELDS = timeGrid.TIME_GRID_FIELDS
export const isTimeGridField = timeGrid.isTimeGridField
export const parseClockCellText = timeGrid.parseClockCellText
export const parseTimeGridClipboard = timeGrid.parseTimeGridClipboard
export const stepTimeCell = timeGrid.stepTimeCell
export const findNextEditableTimeCell = timeGrid.findNextEditableTimeCell
