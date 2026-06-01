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
}

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
