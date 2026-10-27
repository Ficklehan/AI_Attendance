/** 与小程序 recordDisplay.js 对齐的记录行展示判定（PC 端单一来源） */

import { isAbsentByMark, stripSignatureMarksFromSmartMark } from './recognitionLabels'

export function getEffectiveSmartMark(record) {
  const raw = record?.SmartMark ?? record?.smartMark ?? record?.Mark ?? ''
  return stripSignatureMarksFromSmartMark(String(raw).trim())
}

/**
 * 未出勤只认第13位标记。含「模糊」不是未出勤；不得用到离皆空推断。
 */
export function isAbsentRow(record) {
  return isAbsentByMark(record)
}
