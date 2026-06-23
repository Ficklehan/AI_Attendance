/** 识别结果展示翻译：存储层可保留中文/模型原文，UI 按当前语言渲染 */

import * as markCoreMod from '@shared/recognitionMarkCore.cjs'
import markTokens from '../../../shared/locales/mark-tokens.json'
import { importSharedCjs } from './importSharedCjs'
import { getNightShiftRules, shouldMarkNightShift, resolveRecordCountry } from './nightShiftRules'

const markCore = importSharedCjs(markCoreMod)
const {
  splitSmartMarkParts,
  isSignatureResultMark,
  markContains,
  markHasKind,
  stripSignatureMarksFromSmartMark,
} = markCore

function stripNightShiftMarkParts(mark) {
  return splitSmartMarkParts(mark).filter((part) => !markContains(part, 'nightShift'))
}

/** 按当前到离/排班重算夜班标记（可增可删） */
export function refreshNightShiftInSmartMark(mark, record, taskCountry) {
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
  if (!parts.length) {
    parts = ['正常']
  }
  const country = resolveRecordCountry(record, taskCountry)
  if (shouldMarkNightShift(record, getNightShiftRules(country))) {
    parts = [...parts, '夜班']
  }
  return [...new Set(parts)].join(';')
}

/** 展示层按规则重算夜班标记 */
export function withInferredNightShiftMark(mark, record, taskCountry) {
  return refreshNightShiftInSmartMark(mark, record, taskCountry)
}

const MARK_TOKEN_KEYS = markTokens

export {
  splitSmartMarkParts,
  isSignatureResultMark,
  markContains,
  markHasKind,
  stripSignatureMarksFromSmartMark,
}

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

const BLANK_SIGNATURE_TOKENS = new Set([
  '', 'n/a', 'na', 'none', 'null',
])

const ILLEGIBLE_SIGNATURE_TOKENS = new Set([
  '???', '??', 'unknown', 'illegible',
  '模糊', '不清楚', 'borroso', 'wazig', 'rozmazan', 'unscharf', 'flou', 'borrosa',
])

const SIGNATURE_COLUMN_HEADERS = new Set([
  '员工签名', 'signature', 'signatura', 'firma', '员工签', '签名',
  'employee signature', 'handtekening', 'unterschrift',
])

const SIGNATURE_COLUMN_HEADER_KEYWORDS = [
  '员工签名', 'signature', 'signatura', 'firma', '员工签', '签名',
  'employee signature', 'handtekening', 'unterschrift',
]

export function isSignatureColumnHeaderText(headerText) {
  if (headerText == null || !String(headerText).trim()) return false
  const lower = String(headerText).trim().toLowerCase()
  if (lower.includes('firma e conferma') || lower.includes('responsabile')) return false
  return SIGNATURE_COLUMN_HEADER_KEYWORDS.some((kw) => lower.includes(kw))
}

export function isSignatureColumnHeader(value) {
  if (value == null) return false
  const trimmed = String(value).trim()
  if (!trimmed) return false
  return SIGNATURE_COLUMN_HEADERS.has(trimmed.toLowerCase())
}

export function isSignatureHeaderEcho(value) {
  if (value == null || !String(value).trim()) return false
  if (isSignatureColumnHeader(value)) return true
  const lower = String(value).trim().toLowerCase()
  if (lower.includes('firma e conferma') || lower.includes('responsabile')) return true
  return isSignatureColumnHeaderText(value) && lower.length <= 60
}

export function isSignatureStruckOut(rawSignature) {
  if (rawSignature == null || !String(rawSignature).trim()) return false
  const lower = String(rawSignature).trim().toLowerCase()
  return lower.includes('划线')
    || lower.includes('划掉')
    || String(rawSignature).trim() === '划线删除'
    || lower.includes('barré')
    || lower.includes('barrato')
    || lower.includes('crossed')
    || lower.includes('strikethrough')
    || lower.includes('cancellato')
}

export function shouldInferSignedWhenEmpty(record) {
  if (!record) return false
  const mark = String(record.Mark || record.mark || '').trim()
  const smartMark = String(record.SmartMark || record.smartMark || '').trim()
  const arrivee = record.ARRIVEE ?? record.arrival ?? ''
  const depart = record.DEPAR ?? record.departure ?? ''
  const hasAbsent = (m) => splitSmartMarkParts(m).includes('未出勤')
  if (hasAbsent(mark) || hasAbsent(smartMark)) return false
  const hasTime = (t) => {
    const v = String(t || '').trim()
    return v && v !== '???' && /^\d{1,2}:\d{2}$/.test(v)
  }
  return hasTime(arrivee) || hasTime(depart)
}

export function sanitizeAiSignature(value) {
  if (value == null) return ''
  const trimmed = String(value).trim()
  if (!trimmed || isSignatureHeaderEcho(trimmed)) return ''
  return trimmed
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

  const categoryMatch = text.match(/^(required|unreadable|duplicate|other)[:：]\s*(.*)$/i)
  if (categoryMatch) {
    const category = categoryMatch[1].toLowerCase()
    const detail = categoryMatch[2].trim()
    const labelKey = {
      required: 'taskEdit.anomalyCategoryRequired',
      unreadable: 'taskEdit.anomalyCategoryUnreadable',
      duplicate: 'taskEdit.anomalyCategoryDuplicate',
      other: 'taskEdit.anomalyCategoryOther',
    }[category]
    let label = category
    if (labelKey) {
      const translated = t(labelKey)
      label = translated !== labelKey ? translated : category
    }
    return detail ? `${label}：${detail}` : label
  }

  return text
}

export function translateAnomalyReasons(reasons, t) {
  return (reasons || []).map((r) => translateAnomalyReason(r, t)).filter(Boolean)
}

export function isBlankSignature(value) {
  if (value == null) return true
  const trimmed = String(value).trim()
  if (!trimmed) return true
  if (ILLEGIBLE_SIGNATURE_TOKENS.has(trimmed.toLowerCase())) return false
  const lower = trimmed.toLowerCase()
  if (isSignatureResultMark(trimmed)) return trimmed === '未签字'
  if (BLANK_SIGNATURE_TOKENS.has(lower)) return true
  return isSignatureHeaderEcho(trimmed)
}

export function isRecordDeletedForSignature(record) {
  if (!record) return false
  if (record.isDeleted === true || record.deleted === true) return true
  const mark = String(record.SmartMark || record.smartMark || record.Mark || record.mark || '').trim()
  return splitSmartMarkParts(mark).includes('已删除')
}

/** 根据 AI 原始输出与行上下文计算签字结果 */
export function computeSignatureMark(record) {
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

/** 旧数据：统一规范为「未签字 / 已签字」 */
export function normalizeLegacySignature(signature, recordOrDeleted = false) {
  if (recordOrDeleted && typeof recordOrDeleted === 'object') {
    return computeSignatureMark(recordOrDeleted)
  }
  const rowDeleted = typeof recordOrDeleted === 'boolean' ? recordOrDeleted : false
  if (rowDeleted) return '未签字'
  if (isBlankSignature(signature)) return '未签字'
  const trimmed = String(signature).trim()
  if (trimmed === '未签字确认' || trimmed === '未签字') return '未签字'
  return '已签字'
}

export function getDisplaySignature(signature, record = null) {
  if (record) return computeSignatureMark(record)
  return normalizeLegacySignature(signature, false)
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
  if (p === '未签字' || p === '未签字确认') return 'warning'
  if (p === '已签字' || p === '已签字确认') return 'success'
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
  if (record?._manuallyAdded) {
    tags.unshift({
      key: 'manually-added',
      label: t('recognition.marks.manuallyAdded'),
      color: 'blue',
    })
  }
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
