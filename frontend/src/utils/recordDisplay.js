/** 与小程序 recordDisplay.js 对齐的记录行展示判定（PC 端单一来源） */

import { markContains, stripSignatureMarksFromSmartMark } from './recognitionLabels'

function pickField(record, ...keys) {
  if (!record) return ''
  for (const key of keys) {
    const v = record[key]
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      return String(v).trim()
    }
  }
  return ''
}

export function getEffectiveSmartMark(record) {
  const raw = record?.SmartMark ?? record?.smartMark ?? record?.Mark ?? ''
  return stripSignatureMarksFromSmartMark(String(raw).trim())
}

/**
 * 未出勤行：标记含 absent/未出勤，或到离皆空（已恢复行除外；已删除行单独处理）
 */
export function isAbsentRow(record) {
  if (!record || record._restored || record.isDeleted) return false
  const mark = getEffectiveSmartMark(record)
  if (mark.includes('未出勤') || markContains(mark, 'absent')) return true
  const arrive = pickField(record, 'ARRIVEE', 'ArriveTime', 'arrival')
  const depart = pickField(record, 'DEPAR', 'DepartTime', 'departure')
  return !arrive && !depart
}
