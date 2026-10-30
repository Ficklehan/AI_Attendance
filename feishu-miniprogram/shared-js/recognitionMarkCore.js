/**
 * 识别标记解析（PC / 小程序共用，无 i18n 依赖）
 */

const MARK_DETECT = {
  absent: ['未出勤', 'absent', 'ausente', 'abwesend', 'afwezig', 'nieobecny', 'nepřítom'],
  blurred: ['模糊', 'blur', 'borroso', 'unscharf', 'wazig', 'rozmazan', 'nieostry'],
  handwriting: ['手写', 'handwritten', 'manuscrit', 'manuscrito', 'handschrift', 'handgeschreven'],
  nightShift: ['夜班', 'night', 'noche', 'nuit', 'notte', 'nacht', 'noc', 'nocka'],
  deleted: ['已删除', 'deleted', 'eliminado', 'supprimé', 'gelöscht', 'verwijderd'],
  normal: ['正常', 'normal', 'normale', 'normaal'],
  missingPunch: ['缺卡', '到达缺卡', '离开缺卡', '到达缺位', '离开缺位', '缺位'],
}

const SIGNATURE_RESULT_MARKS = new Set(['已签字', '未签字', '已签字确认', '未签字确认'])

function splitSmartMarkParts(mark) {
  if (mark == null || mark === '' || mark === '-') return []
  return [...new Set(String(mark).split(/[;；,，]/).map((p) => p.trim()).filter(Boolean))]
}

function isSignatureResultMark(value) {
  return SIGNATURE_RESULT_MARKS.has(String(value || '').trim())
}

function markContains(mark, kind) {
  if (!mark) return false
  const text = String(mark).toLowerCase()
  const patterns = MARK_DETECT[kind] || []
  return patterns.some((p) => text.includes(p.toLowerCase()))
}

function markHasKind(mark, kind) {
  return splitSmartMarkParts(mark).some((part) => markContains(part, kind))
}

function stripSignatureMarksFromSmartMark(mark) {
  const parts = splitSmartMarkParts(mark).filter((part) => !isSignatureResultMark(part))
  return parts.length ? parts.join(';') : ''
}

function stripAbsentMarkParts(mark) {
  return splitSmartMarkParts(mark).filter((part) => !markContains(part, 'absent'))
}

function parseClockToMinutes(timeStr) {
  if (timeStr == null) return -1
  const str = String(timeStr).trim()
  if (!str || str === '???' || /^illegible$/i.test(str)) return -1
  let match = str.match(/^(\d{1,2}):(\d{2})$/)
  if (match) return parseInt(match[1], 10) * 60 + parseInt(match[2], 10)
  match = str.match(/^(\d{1,2})[hH](\d{2})?$/)
  if (match) return parseInt(match[1], 10) * 60 + (match[2] ? parseInt(match[2], 10) : 0)
  return -1
}

function isNightShiftByTimes(arrive, depart) {
  const arriveMin = parseClockToMinutes(arrive)
  const departMin = parseClockToMinutes(depart)
  if (arriveMin < 0 || departMin < 0) return false
  const arriveHour = Math.floor(arriveMin / 60)
  const departHour = Math.floor(departMin / 60)
  if (arriveHour >= 20 || departHour < 6) return true
  return departMin < arriveMin
}

function isNightShiftBySchedule(shift) {
  if (shift == null || !String(shift).trim()) return false
  const match = String(shift).match(/(\d{1,2}:\d{2})\s*[-~–]\s*(\d{1,2}:\d{2})/)
  if (!match) return false
  const startMin = parseClockToMinutes(match[1])
  const endMin = parseClockToMinutes(match[2])
  if (startMin < 0 || endMin < 0) return false
  const startHour = Math.floor(startMin / 60)
  return endMin < startMin || startHour >= 20 || endMin < 6 * 60
}

function stripNightShiftMarkParts(mark) {
  return splitSmartMarkParts(mark).filter((part) => !markContains(part, 'nightShift'))
}

function hasUsableArriveAndDepart(arrive, depart) {
  return parseClockToMinutes(arrive) >= 0 && parseClockToMinutes(depart) >= 0
}

function recordHasUsablePunchTimes(record) {
  if (!record) return false
  const arrive = record.ARRIVEE ?? record.arrival ?? ''
  const depart = record.DEPAR ?? record.departure ?? ''
  return hasUsableArriveAndDepart(arrive, depart)
}

/**
 * 模型「未出勤」只是初判。当前行已有有效到离（人工更正后的事实）时去掉未出勤。
 */
function reconcileAbsentMarkWithClocks(mark, record) {
  const source = mark == null || mark === '' ? (record?.SmartMark || record?.Mark || '') : mark
  if (!recordHasUsablePunchTimes(record)) {
    return source == null ? '' : String(source)
  }
  let parts = stripAbsentMarkParts(source)
  if (!parts.length) parts = ['正常']
  return [...new Set(parts)].join(';')
}

function shouldMarkNightShiftByRecord(record) {
  if (!record || record.isDeleted) return false
  const arrive = record.ARRIVEE ?? record.arrival ?? ''
  const depart = record.DEPAR ?? record.departure ?? ''
  const shift = record.HORAIRES_DU_TRAVAIL ?? record.shift ?? ''
  if (hasUsableArriveAndDepart(arrive, depart)) {
    return isNightShiftByTimes(arrive, depart)
  }
  if (isNightShiftByTimes(arrive, depart)) return true
  return isNightShiftBySchedule(shift)
}

function refreshNightShiftInSmartMark(mark, record) {
  if (!record || record.isDeleted) {
    return stripNightShiftMarkParts(mark || '').join(';')
  }
  const reconciled = reconcileAbsentMarkWithClocks(mark || record.SmartMark || '', record)
  if (markContains(reconciled, 'deleted')) {
    return stripNightShiftMarkParts(reconciled).join(';')
  }
  // 仍带未出勤 = 到离未齐，保持未出勤，不推夜班
  if (markContains(reconciled, 'absent')) {
    return stripNightShiftMarkParts(reconciled).join(';')
  }
  let parts = stripNightShiftMarkParts(reconciled)
  if (!parts.length) parts = ['正常']
  if (shouldMarkNightShiftByRecord(record)) {
    parts = [...parts, '夜班']
  }
  return [...new Set(parts)].join(';')
}

function withInferredNightShiftMark(mark, record) {
  return refreshNightShiftInSmartMark(mark, record)
}

/**
 * 未出勤以模型第13位为初判；含「模糊」不是未出勤。
 * 当前行已有有效到离时，视为已更正出勤，不再只凭模型标记判未出勤。
 */
function isAbsentByMark(record) {
  if (!record || record._restored || record.isDeleted || record.deleted || record._manuallyAdded) {
    return false
  }
  const mark = stripSignatureMarksFromSmartMark(String(
    record.SmartMark || record.smartMark || record.Mark || record.mark || '',
  ).trim())
  if (markHasKind(mark, 'blurred')) return false
  if (!markHasKind(mark, 'absent')) return false
  if (recordHasUsablePunchTimes(record)) return false
  return true
}

module.exports = {
  MARK_DETECT,
  SIGNATURE_RESULT_MARKS,
  splitSmartMarkParts,
  isSignatureResultMark,
  markContains,
  markHasKind,
  stripSignatureMarksFromSmartMark,
  stripAbsentMarkParts,
  reconcileAbsentMarkWithClocks,
  recordHasUsablePunchTimes,
  withInferredNightShiftMark,
  refreshNightShiftInSmartMark,
  isAbsentByMark,
}
