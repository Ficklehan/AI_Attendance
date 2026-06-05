/** 识别结果展示翻译：存储层可保留中文/模型原文，UI 按当前语言渲染 */

const LEGACY_ANOMALY_KEYS = {
  工号未识别: 'recognition.missing.NO',
  日期未识别: 'recognition.missing.Date',
  到达时间未识别: 'recognition.missing.ARRIVEE',
  离开时间未识别: 'recognition.missing.DEPAR',
  休息时间未识别: 'recognition.missing.PAUSE',
  记录已删除: 'recognition.deletedRecord',
  内容模糊: 'taskEdit.blurredContent',
  手写内容: 'taskEdit.handwrittenContent',
  未出勤: 'taskEdit.absentReason',
  必填字段缺失: 'taskEdit.requiredFieldMissingShort',
}

const MARK_TOKEN_KEYS = {
  正常: 'recognition.marks.normal',
  手写: 'recognition.marks.handwriting',
  模糊: 'recognition.marks.blurred',
  夜班: 'recognition.marks.nightShift',
  未出勤: 'recognition.marks.absent',
  已删除: 'recognition.marks.deleted',
  已签字确认: 'recognition.marks.signedConfirmed',
  未签字确认: 'recognition.marks.unsignedConfirmed',
  已签字: 'recognition.marks.signed',
}

const SIGNATURE_RESULT_MARKS = new Set(['已签字确认', '未签字确认', '已签字'])

const BLANK_SIGNATURE_TOKENS = new Set([
  '', '???', '??', 'unknown', 'illegible', 'n/a', 'na', 'none', 'null',
  '员工签名', 'signature', 'signatura', 'firma', '员工签', '签名',
  'sign', 'signed', 'unsigned',
])

const MARK_DETECT = {
  absent: ['未出勤', 'absent', 'ausente', 'abwesend', 'afwezig', 'nieobecny', 'nepřítom'],
  blurred: ['模糊', 'blur', 'borroso', 'unscharf', 'wazig', 'rozmazan', 'nieostry'],
  handwriting: ['手写', 'handwritten', 'manuscrit', 'manuscrito', 'handschrift', 'handgeschreven'],
  nightShift: ['夜班', 'night', 'noche', 'nuit', 'notte', 'nacht', 'noc', 'nocka'],
  deleted: ['已删除', 'deleted', 'eliminado', 'supprimé', 'gelöscht', 'verwijderd'],
  normal: ['正常', 'normal', 'normale', 'normaal'],
}

export function translateAnomalyReason(reason, t) {
  if (reason == null || reason === '') return ''
  const text = String(reason).trim()

  if (text.startsWith('missing.')) {
    const field = text.slice('missing.'.length)
    const key = `recognition.missing.${field}`
    const translated = t(key)
    return translated !== key ? translated : text
  }
  if (text === 'deleted.record') {
    return t('recognition.deletedRecord')
  }

  const legacyKey = LEGACY_ANOMALY_KEYS[text]
  if (legacyKey) return t(legacyKey)

  const dupMatch = text.match(/^重名疑似[：:]\s*(.+)$/)
  if (dupMatch) {
    return t('taskEdit.duplicateSuspect', { names: dupMatch[1] })
  }

  return text
}

export function translateAnomalyReasons(reasons, t) {
  return (reasons || []).map((r) => translateAnomalyReason(r, t)).filter(Boolean)
}

export function splitSmartMarkParts(mark) {
  if (mark == null || mark === '' || mark === '-') return []
  return [...new Set(String(mark).split(/[;；,，]/).map((p) => p.trim()).filter(Boolean))]
}

export function isSignatureResultMark(value) {
  return SIGNATURE_RESULT_MARKS.has(String(value || '').trim())
}

export function isBlankSignature(value) {
  if (value == null) return true
  const trimmed = String(value).trim()
  if (!trimmed) return true
  const lower = trimmed.toLowerCase()
  if (BLANK_SIGNATURE_TOKENS.has(lower)) return true
  if (lower === 'signature' || lower === '员工签名') return true
  return false
}

/** 旧数据：已是三档标记则保留；空白为未签字确认；其余手写原文默认已签字确认 */
export function normalizeLegacySignature(signature) {
  if (isSignatureResultMark(signature)) return String(signature).trim()
  if (isBlankSignature(signature)) return '未签字确认'
  return '已签字确认'
}

export function getDisplaySignature(signature) {
  return normalizeLegacySignature(signature)
}

/** 标记列不展示签字结果（签字结果仅在 SIGNATURE 列） */
export function stripSignatureMarksFromSmartMark(mark) {
  const parts = splitSmartMarkParts(mark).filter((part) => !isSignatureResultMark(part))
  return parts.length ? parts.join(';') : ''
}

/** 原始 SmartMark（合并多字段、去掉签字标记，不做手写等展示推断） */
export function getRawSmartMark(record) {
  const sourceMarks = [
    record?.SmartMark,
    record?.Mark,
    record?.mark,
    record?.smartMark,
  ].map((v) => String(v || '').trim()).filter(Boolean)
  if (!sourceMarks.length) return ''
  return stripSignatureMarksFromSmartMark(
    [...new Set(sourceMarks.join(';').split(/[;；,，]/).map((v) => v.trim()).filter(Boolean))].join(';')
  )
}

export function translateSignatureMark(value, t) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return translateSmartMarkPart(text, t)
}

export function getSignatureMarkColor(value) {
  const p = String(value || '').trim()
  if (p === '已签字确认') return 'success'
  if (p === '未签字确认') return 'warning'
  if (p === '已签字') return 'processing'
  return 'default'
}

export function translateSmartMarkPart(part, t) {
  const p = String(part || '').trim()
  if (!p) return '-'
  const key = MARK_TOKEN_KEYS[p]
  if (key) return t(key)
  for (const [token, i18nKey] of Object.entries(MARK_TOKEN_KEYS)) {
    if (p.includes(token)) {
      return p.replace(token, t(i18nKey))
    }
  }
  return p
}

export function getMarkColorForPart(part) {
  if (!part) return 'default'
  if (isSignatureResultMark(part)) return 'default'
  if (markContains(part, 'absent')) return 'error'
  if (markContains(part, 'blurred')) return 'warning'
  if (markContains(part, 'handwriting')) return 'processing'
  if (markContains(part, 'nightShift')) return 'purple'
  if (markContains(part, 'normal')) return 'success'
  return 'default'
}

export function translateSmartMark(mark, t) {
  if (mark == null || mark === '' || mark === '-') return '-'
  const parts = splitSmartMarkParts(mark)
  if (!parts.length) return '-'
  return [...new Set(parts.map((part) => translateSmartMarkPart(part, t)))].join('; ')
}

/**
 * 标记列多标签：识别标记分开展示，人工校准作为额外标签。
 */
export function buildRecordMarkTags(record, { getDisplayMark, isAbsentRow, t, hasManualCalibration }) {
  if (record?.isDeleted) {
    return [{ key: 'deleted', label: t('recognition.marks.deleted'), color: 'default' }]
  }
  if (isAbsentRow(record)) {
    return [{ key: 'absent', label: t('recognition.marks.absent'), color: 'error' }]
  }
  let parts = splitSmartMarkParts(getDisplayMark(record))
  if (!parts.length) {
    parts = ['正常']
  }
  const tags = parts.map((part, index) => ({
    key: `mark-${part}-${index}`,
    label: translateSmartMarkPart(part, t),
    color: getMarkColorForPart(part),
  }))
  if (hasManualCalibration?.(record)) {
    tags.push({
      key: 'manual-calibration',
      label: t('calibration.manualTag'),
      color: 'orange',
      showCalibrationHistory: true,
    })
  }
  return tags
}

export function markContains(mark, kind) {
  if (!mark) return false
  const text = String(mark).toLowerCase()
  const patterns = MARK_DETECT[kind] || []
  return patterns.some((p) => text.includes(p.toLowerCase()))
}

/** 多值标记（; 分隔）中是否含某类标记 */
export function markHasKind(mark, kind) {
  return splitSmartMarkParts(mark).some((part) => markContains(part, kind))
}

/**
 * 识别结果统计卡片：按原始 SmartMark 显式标记计数，各类可叠加（如 正常;夜班 同时计入两卡）。
 * 手写仅统计标记中显式含「手写」的行，不含工号/姓名推断。
 * 已删除行仅计入「已删除」，不参与其余统计。
 */
export function calculateRecordStats(records, { getDisplayMark } = {}) {
  const resolveMark = getDisplayMark || getRawSmartMark
  const stats = { normal: 0, handwriting: 0, blurred: 0, night: 0, absent: 0, deleted: 0 }
  for (const record of records || []) {
    if (record?.isDeleted) {
      stats.deleted++
      continue
    }
    const mark = resolveMark(record) || ''
    if (markHasKind(mark, 'normal')) stats.normal++
    if (markHasKind(mark, 'handwriting')) stats.handwriting++
    if (markHasKind(mark, 'blurred')) stats.blurred++
    if (markHasKind(mark, 'nightShift')) stats.night++
    if (markHasKind(mark, 'absent')) stats.absent++
  }
  return stats
}

export function anomalyReasonKind(reason) {
  const text = String(reason || '')
  if (text.startsWith('missing.') || /未识别|必填|required|missing/i.test(text)) return 'missing'
  if (/删除|deleted|record/i.test(text)) return 'deleted'
  if (/重名|duplicate/i.test(text)) return 'duplicate'
  if (/模糊|blur/i.test(text)) return 'blurred'
  if (/手写|handwritten|manuscrit/i.test(text)) return 'handwriting'
  if (/未出勤|absent|ausente/i.test(text)) return 'absent'
  if (/夜班|night|noche|nuit/i.test(text)) return 'night'
  return 'info'
}
