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
  const markSources = [mark, record.SmartMark, record.Mark, record.mark, record.smartMark]
    .filter(Boolean)
    .join(';')
  if (markContains(markSources, 'absent') || markContains(markSources, 'deleted')) {
    return stripNightShiftMarkParts(mark || record.SmartMark || '').join(';')
  }
  let parts = stripNightShiftMarkParts(mark || record.SmartMark || record.Mark || '')
  if (!parts.length) parts = ['正常']
  if (shouldMarkNightShiftByRecord(record)) {
    parts = [...parts, '夜班']
  }
  return [...new Set(parts)].join(';')
}

function withInferredNightShiftMark(mark, record) {
  return refreshNightShiftInSmartMark(mark, record)
}

module.exports = {
  MARK_DETECT,
  SIGNATURE_RESULT_MARKS,
  splitSmartMarkParts,
  isSignatureResultMark,
  markContains,
  markHasKind,
  stripSignatureMarksFromSmartMark,
  withInferredNightShiftMark,
  refreshNightShiftInSmartMark,
}
