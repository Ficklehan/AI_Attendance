/** 小程序识别结果展示翻译（与 PC recognitionLabels 对齐） */

const LEGACY_ANOMALY_KEYS = {
  工号未识别: 'recognition.missing.NO',
  日期未识别: 'recognition.missing.Date',
  到达时间未识别: 'recognition.missing.ARRIVEE',
  离开时间未识别: 'recognition.missing.DEPAR',
  休息时间未识别: 'recognition.missing.PAUSE',
  记录已删除: 'recognition.deletedRecord',
  内容模糊: 'result.blurredContent',
  手写内容: 'result.handwrittenContent',
  未出勤: 'result.absentReason',
  必填字段缺失: 'result.requiredFieldMissingShort'
}

const SIGNATURE_RESULT_MARKS = { 未签字: true, 已签字: true, 已签字确认: true, 未签字确认: true }

const BLANK_SIGNATURE_TOKENS = {
  'n/a': true, na: true, none: true, null: true,
  员工签名: true, signature: true, signatura: true, firma: true,
  员工签: true, 签名: true,
}

const ILLEGIBLE_SIGNATURE_TOKENS = {
  '???': true, '??': true, unknown: true, illegible: true,
  模糊: true, 不清楚: true, borroso: true, wazig: true, rozmazan: true, unscharf: true, flou: true, borrosa: true,
}

const MARK_TOKEN_KEYS = {
  正常: 'recognition.marks.normal',
  手写: 'recognition.marks.handwriting',
  模糊: 'recognition.marks.blurred',
  夜班: 'recognition.marks.nightShift',
  未出勤: 'recognition.marks.absent',
  已删除: 'recognition.marks.deleted',
  已签字确认: 'recognition.marks.signed',
  未签字确认: 'recognition.marks.unsigned',
  已签字: 'recognition.marks.signed',
  未签字: 'recognition.marks.unsigned',
}

const MARK_DETECT = {
  absent: ['未出勤', 'absent', 'ausente', 'abwesend', 'afwezig', 'nieobecny'],
  blurred: ['模糊', 'blur', 'borroso', 'unscharf', 'wazig', 'rozmazan'],
  handwriting: ['手写', 'handwritten', 'manuscrit', 'manuscrito', 'handschrift'],
  nightShift: ['夜班', 'night', 'noche', 'nuit', 'notte', 'nacht', 'noc'],
  deleted: ['已删除', 'deleted', 'eliminado', 'supprimé', 'gelöscht'],
  normal: ['正常', 'normal', 'normale', 'normaal']
}

function translateAnomalyReason(reason, t) {
  if (reason == null || reason === '') return ''
  const text = String(reason).trim()
  if (text.startsWith('missing.')) {
    const field = text.slice('missing.'.length)
    const key = `recognition.missing.${field}`
    const translated = t(key)
    return translated !== key ? translated : text
  }
  if (text === 'deleted.record') return t('recognition.deletedRecord')
  const legacyKey = LEGACY_ANOMALY_KEYS[text]
  if (legacyKey) return t(legacyKey)
  const dupMatch = text.match(/^重名疑似[：:]\s*(.+)$/)
  if (dupMatch) return t('result.duplicateSuspect', { names: dupMatch[1] })
  return text
}

function splitSmartMarkParts(mark) {
  if (mark == null || mark === '' || mark === '-') return []
  return [...new Set(String(mark).split(/[;；,，]/).map((p) => p.trim()).filter(Boolean))]
}

function isSignatureResultMark(value) {
  return !!SIGNATURE_RESULT_MARKS[String(value || '').trim()]
}

function isSignatureHeaderEcho(value) {
  if (value == null || !String(value).trim()) return false
  const lower = String(value).trim().toLowerCase()
  if (BLANK_SIGNATURE_TOKENS[lower] || lower === 'signature' || lower === '员工签名' || lower === 'firma') return true
  if (lower.includes('firma e conferma') || lower.includes('responsabile')) return true
  return false
}

function isSignatureStruckOut(rawSignature) {
  if (rawSignature == null || !String(rawSignature).trim()) return false
  const lower = String(rawSignature).trim().toLowerCase()
  return lower.includes('划线') || lower.includes('划掉') || String(rawSignature).trim() === '划线删除'
}

function shouldInferSignedWhenEmpty(record) {
  if (!record) return false
  const mark = String(record.Mark || record.mark || '').trim()
  const smartMark = String(record.SmartMark || record.smartMark || '').trim()
  const hasAbsent = (m) => splitSmartMarkParts(m).includes('未出勤')
  if (hasAbsent(mark) || hasAbsent(smartMark)) return false
  const arrivee = record.ARRIVEE ?? record.arrival ?? ''
  const depart = record.DEPAR ?? record.departure ?? ''
  const hasTime = (t) => {
    const v = String(t || '').trim()
    return v && v !== '???' && /^\d{1,2}:\d{2}$/.test(v)
  }
  return hasTime(arrivee) || hasTime(depart)
}

function sanitizeAiSignature(value) {
  if (value == null) return ''
  const trimmed = String(value).trim()
  if (!trimmed || isSignatureHeaderEcho(trimmed)) return ''
  return trimmed
}

function isBlankSignature(value) {
  if (value == null) return true
  const trimmed = String(value).trim()
  if (!trimmed) return true
  const lower = trimmed.toLowerCase()
  if (ILLEGIBLE_SIGNATURE_TOKENS[lower]) return false
  if (isSignatureResultMark(trimmed)) return trimmed === '未签字'
  if (BLANK_SIGNATURE_TOKENS[lower]) return true
  return isSignatureHeaderEcho(trimmed)
}

function isRecordDeletedForSignature(record) {
  if (!record) return false
  if (record.isDeleted === true || record.deleted === true) return true
  const mark = String(record.SmartMark || record.smartMark || record.Mark || record.mark || '').trim()
  return splitSmartMarkParts(mark).includes('已删除')
}

function computeSignatureMark(record) {
  if (!record) return '未签字'
  if (isRecordDeletedForSignature(record)) return '未签字'
  const raw = record.SIGNATURE_RAW ?? record.SIGNATURE ?? record.CHECKER ?? ''
  const rawText = String(raw).trim()
  if (isSignatureStruckOut(rawText)) return '未签字'
  const sanitized = sanitizeAiSignature(rawText)
  if (sanitized && !isBlankSignature(sanitized)) return '已签字'
  if (isSignatureResultMark(rawText) && record.SIGNATURE_RAW == null) {
    if (rawText === '已签字') return '已签字'
    if (shouldInferSignedWhenEmpty(record)) return '已签字'
    return '未签字'
  }
  if (shouldInferSignedWhenEmpty(record)) return '已签字'
  return '未签字'
}

function normalizeLegacySignature(signature, recordOrDeleted) {
  if (recordOrDeleted && typeof recordOrDeleted === 'object') {
    return computeSignatureMark(recordOrDeleted)
  }
  const rowDeleted = typeof recordOrDeleted === 'boolean' ? recordOrDeleted : false
  if (rowDeleted) return '未签字'
  if (isBlankSignature(signature)) return '未签字'
  const trimmed = String(signature || '').trim()
  if (trimmed === '未签字确认' || trimmed === '未签字') return '未签字'
  return '已签字'
}

function getDisplaySignature(signature, record) {
  if (record) return computeSignatureMark(record)
  return normalizeLegacySignature(signature, false)
}

function stripSignatureMarksFromSmartMark(mark) {
  const parts = splitSmartMarkParts(mark).filter((part) => !isSignatureResultMark(part))
  return parts.length ? parts.join(';') : ''
}

function translateSignatureMark(value, t) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return translateSmartMarkPart(text, t)
}

function getSignatureMarkTagClass(value) {
  const p = String(value || '').trim()
  if (p === '未签字' || p === '未签字确认') return 'tag-warning'
  if (p === '已签字' || p === '已签字确认') return 'tag-success'
  return 'tag-default'
}

function translateSmartMarkPart(part, t) {
  const p = String(part || '').trim()
  if (!p) return '-'
  const key = MARK_TOKEN_KEYS[p]
  if (key) return t(key)
  for (const [token, i18nKey] of Object.entries(MARK_TOKEN_KEYS)) {
    if (p.includes(token)) return p.replace(token, t(i18nKey))
  }
  return p
}

function translateSmartMark(mark, t) {
  if (mark == null || mark === '' || mark === '-') return '-'
  const parts = splitSmartMarkParts(mark)
  if (!parts.length) return '-'
  return [...new Set(parts.map((part) => translateSmartMarkPart(part, t)))].join('; ')
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

function getRawSmartMark(record) {
  const sourceMarks = [
    record && record.SmartMark,
    record && record.Mark,
    record && record.mark,
    record && record.smartMark
  ].map((v) => String(v || '').trim()).filter(Boolean)
  if (!sourceMarks.length) return ''
  return stripSignatureMarksFromSmartMark(
    [...new Set(sourceMarks.join(';').split(/[;；,，]/).map((v) => v.trim()).filter(Boolean))].join(';')
  )
}

function calculateRecordStats(records, options) {
  const getDisplayMark = options && options.getDisplayMark
  const resolveMark = getDisplayMark || getRawSmartMark
  const stats = { normal: 0, handwriting: 0, blurred: 0, night: 0, absent: 0, deleted: 0 }
  for (const record of records || []) {
    if (record && record.isDeleted) {
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

function anomalyReasonKind(reason) {
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

module.exports = {
  translateAnomalyReason,
  translateSmartMark,
  translateSmartMarkPart,
  splitSmartMarkParts,
  markContains,
  anomalyReasonKind,
  isSignatureResultMark,
  isBlankSignature,
  normalizeLegacySignature,
  computeSignatureMark,
  getDisplaySignature,
  markHasKind,
  getRawSmartMark,
  calculateRecordStats,
  stripSignatureMarksFromSmartMark,
  translateSignatureMark,
  getSignatureMarkTagClass
}
