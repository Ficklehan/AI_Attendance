import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  translateAnomalyReason,
  buildRecordMarkTags,
  markContains,
  anomalyReasonKind,
  refreshNightShiftInSmartMark,
  getRawSmartMark,
} from '@/utils/recognitionLabels'
import { getMissingRequiredFieldKeys, REQUIRED_FIELD_I18N_KEYS } from '@/utils/requiredRecordFields'
import { isArrivalDepartureSameTime } from '@/utils/recordFieldFormatRules'
import { FIELD_LABEL_KEYS } from '@/constants/calibratableFields'
import * as shiftVarianceCoreMod from '@shared/shiftVarianceCore.cjs'
import * as exceptionTypeCoreMod from '@shared/exceptionTypeCore.cjs'
import { importSharedCjs } from '@/utils/importSharedCjs'

const shiftVarianceCore = importSharedCjs(shiftVarianceCoreMod)
const exceptionTypeCore = importSharedCjs(exceptionTypeCoreMod)

const computeShiftVarianceMinutes = typeof shiftVarianceCore.computeShiftVarianceMinutes === 'function'
  ? shiftVarianceCore.computeShiftVarianceMinutes
  : () => ({ earlyArrivalMin: 0, lateArrivalMin: 0, earlyLeaveMin: 0, overtimeMin: 0 })
const formatShiftVariancePhrases = typeof shiftVarianceCore.formatShiftVariancePhrases === 'function'
  ? shiftVarianceCore.formatShiftVariancePhrases
  : () => []
const splitDurationMinutes = typeof shiftVarianceCore.splitDurationMinutes === 'function'
  ? shiftVarianceCore.splitDurationMinutes
  : (totalMinutes) => {
    const total = Math.max(0, Math.floor(Number(totalMinutes) || 0))
    return { total, hours: Math.floor(total / 60), minutes: total % 60 }
  }

const SNAPSHOT_FIELD_KEYS = Array.isArray(exceptionTypeCore.SNAPSHOT_FIELD_KEYS)
  && exceptionTypeCore.SNAPSHOT_FIELD_KEYS.length
  ? exceptionTypeCore.SNAPSHOT_FIELD_KEYS
  : [
    'Pays',
    'Entrepot',
    'Date',
    'NOM_PRENOM',
    'AGENCE_INTERIMAIRE',
    'HORAIRES_DU_TRAVAIL',
    'ARRIVEE',
    'DEPAR',
    'PAUSE',
  ]

const EXCEPTION_TYPE = exceptionTypeCore.EXCEPTION_TYPE || {
  ATTENDANCE_OK: 'attendance_ok',
  PAPER_OK_OCR_WRONG: 'paper_ok_ocr_wrong',
  PAPER_WRONG_TIME: 'paper_wrong_time',
}

const normalizeExceptionType = typeof exceptionTypeCore.normalizeExceptionType === 'function'
  ? exceptionTypeCore.normalizeExceptionType
  : (value) => {
    const v = String(value || '').trim()
    return ['attendance_ok', 'paper_ok_ocr_wrong', 'paper_wrong_time'].includes(v) ? v : ''
  }

const isExceptionTypeExempt = typeof exceptionTypeCore.isExceptionTypeExempt === 'function'
  ? exceptionTypeCore.isExceptionTypeExempt
  : (record, isAbsentRowFn) => {
    if (!record || record.isDeleted) return true
    return typeof isAbsentRowFn === 'function' && isAbsentRowFn(record)
  }

function localGetBaselineFieldDiffs(record, fieldKeys) {
  if (!record || !record._aiBaseline || typeof record._aiBaseline !== 'object') return []
  const keys = Array.isArray(fieldKeys) && fieldKeys.length ? fieldKeys : SNAPSHOT_FIELD_KEYS
  const diffs = []
  keys.forEach((key) => {
    const from = String(record._aiBaseline[key] ?? '').trim()
    const to = String(record[key] ?? '').trim()
    if (from === to) return
    diffs.push({ field: key, from, to })
  })
  return diffs
}

const getBaselineFieldDiffs = typeof exceptionTypeCore.getBaselineFieldDiffs === 'function'
  ? exceptionTypeCore.getBaselineFieldDiffs
  : localGetBaselineFieldDiffs

const ANOMALY_CATEGORY_ORDER = ['required', 'unreadable', 'format', 'duplicate', 'other']

function isMarkRedundantAnomalyReason(reason, t) {
  const raw = String(reason || '').trim()
  if (!raw) return true
  if (raw.startsWith('missing.')) return true
  if (raw === 'deleted.record') return true
  if (['内容模糊', '手写内容', '未出勤'].includes(raw)) return true
  const translated = translateAnomalyReason(reason, t)
  const kind = anomalyReasonKind(translated)
  return kind === 'blurred' || kind === 'handwriting' || kind === 'absent' || kind === 'night'
}

function buildRowCacheKey(record, duplicatePeers, localeTag = '') {
  if (!record?._rowKey) return ''
  const anomalies = Array.isArray(record.anomalies) ? record.anomalies.join('|') : ''
  const baseline = record._aiBaseline && typeof record._aiBaseline === 'object'
    ? Object.keys(record._aiBaseline).map((k) => `${k}=${record._aiBaseline[k]}`).join('|')
    : ''
  return [
    localeTag || '',
    record._rowKey,
    record.SmartMark,
    record.Mark,
    record.ExceptionType,
    record.Pays,
    record.NOM_PRENOM,
    record.NO,
    record.PAUSE,
    record.ARRIVEE,
    record.DEPAR,
    record.HORAIRES_DU_TRAVAIL,
    record.Date,
    record.Entrepot,
    record.AGENCE_INTERIMAIRE,
    isArrivalDepartureSameTime(record) ? '1' : '0',
    record.SIGNATURE,
    record.isDeleted ? '1' : '0',
    record._duplicateConfirmedUnique ? '1' : '0',
    record._manuallyAdded ? '1' : '0',
    Array.isArray(record._unreadableFields) ? record._unreadableFields.join('|') : '',
    anomalies,
    baseline,
    duplicatePeers || '',
  ].join('::')
}

/**
 * TaskEdit 记录行：标记展示、异常原因、行样式（带行级缓存）
 */
export function useTaskEditRecordDisplay(records, getDuplicateMeta, { isAbsentRow, hasManualCalibration, taskCountry }) {
  const { t, locale } = useI18n()
  /** rowKey -> { fp, payload }；按行失效，避免一次按键清空全表缓存 */
  const rowCache = new Map()

  const getDuplicatePeersKey = (record) => {
    const meta = getDuplicateMeta(record)
    return meta?.peers?.join('、') || ''
  }

  const readCache = (record) => {
    const rowKey = record?._rowKey
    if (!rowKey) return null
    const fp = buildRowCacheKey(record, getDuplicatePeersKey(record), locale.value)
    if (!fp) return null
    const entry = rowCache.get(rowKey)
    if (!entry || entry.fp !== fp) return null
    return entry.payload || null
  }

  const writeCache = (record, payload) => {
    const rowKey = record?._rowKey
    if (!rowKey) return payload
    const fp = buildRowCacheKey(record, getDuplicatePeersKey(record), locale.value)
    if (!fp) return payload
    rowCache.set(rowKey, { fp, payload })
    if (rowCache.size > 800) {
      const firstKey = rowCache.keys().next().value
      rowCache.delete(firstKey)
    }
    return payload
  }

  const clearRowCache = () => {
    rowCache.clear()
  }

  const invalidateRowCache = (record) => {
    const rowKey = record?._rowKey
    if (rowKey) rowCache.delete(rowKey)
  }

  /** 任务确认页汇总：按异常类型 / 班次偏差口径，不再用 SmartMark 手写·模糊·夜班 */
  const stats = computed(() => {
    void locale.value
    const next = {
      pendingException: 0,
      attendanceOk: 0,
      ocrWrong: 0,
      paperWrong: 0,
      shiftVariance: 0,
      deleted: 0,
    }
    for (const record of records.value || []) {
      if (record?.isDeleted) {
        next.deleted++
        continue
      }
      if (typeof isAbsentRow === 'function' && isAbsentRow(record)) {
        continue
      }
      if (getRecordShiftVarianceSentence(record)) {
        next.shiftVariance++
      }
      if (isExceptionTypeExempt(record, isAbsentRow)) {
        continue
      }
      const type = normalizeExceptionType(record?.ExceptionType)
      if (!type) {
        next.pendingException++
      } else if (type === EXCEPTION_TYPE.ATTENDANCE_OK) {
        next.attendanceOk++
      } else if (type === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG) {
        next.ocrWrong++
      } else if (type === EXCEPTION_TYPE.PAPER_WRONG_TIME) {
        next.paperWrong++
      }
    }
    return next
  })

  const statItems = computed(() => {
    void locale.value
    return [
    {
      key: 'pendingException',
      variant: 'blurred',
      value: stats.value.pendingException,
      label: t('taskEdit.statsPendingException'),
    },
    {
      key: 'attendanceOk',
      variant: 'normal',
      value: stats.value.attendanceOk,
      label: t('taskEdit.statsAttendanceOk'),
    },
    {
      key: 'ocrWrong',
      variant: 'handwriting',
      value: stats.value.ocrWrong,
      label: t('taskEdit.statsOcrWrong'),
    },
    {
      key: 'paperWrong',
      variant: 'absent',
      value: stats.value.paperWrong,
      label: t('taskEdit.statsPaperWrong'),
    },
    {
      key: 'shiftVariance',
      variant: 'night',
      value: stats.value.shiftVariance,
      label: t('taskEdit.statsShiftVariance'),
    },
    {
      key: 'deleted',
      variant: 'deleted',
      value: stats.value.deleted,
      label: t('taskEdit.statsDeleted'),
    },
  ]
  })

  /** 与汇总卡片口径一致，供表格快速过滤 */
  const recordMatchesStatFilter = (record, filterKey) => {
    if (!filterKey) return true
    if (!record) return false
    if (filterKey === 'deleted') {
      return Boolean(record.isDeleted || record.deleted)
    }
    if (filterKey === 'attention' || filterKey === 'anomaly') {
      return needsAttentionRecord(record)
    }
    if (record.isDeleted || record.deleted) return false
    if (typeof isAbsentRow === 'function' && isAbsentRow(record)) return false
    if (filterKey === 'shiftVariance') {
      return Boolean(getRecordShiftVarianceSentence(record))
    }
    if (isExceptionTypeExempt(record, isAbsentRow)) return false
    const type = normalizeExceptionType(record.ExceptionType)
    if (filterKey === 'pendingException') return !type
    if (filterKey === 'attendanceOk') return type === EXCEPTION_TYPE.ATTENDANCE_OK
    if (filterKey === 'ocrWrong') return type === EXCEPTION_TYPE.PAPER_OK_OCR_WRONG
    if (filterKey === 'paperWrong') return type === EXCEPTION_TYPE.PAPER_WRONG_TIME
    return true
  }

  const hasHandwrittenText = (value) => {
    const text = String(value || '').toLowerCase()
    return text.includes('手写')
      || text.includes('handwritten')
      || text.includes('manuscrit')
      || text.includes('manuscrite')
      || text.includes('ecrit main')
      || text.includes('écrit main')
      || text.includes('ecrit a la main')
      || text.includes('écrit à la main')
  }

  const hasHandwrittenIdentity = (record) => {
    const anomalyText = Array.isArray(record?.anomalies) ? record.anomalies.join(' ') : ''
    return hasHandwrittenText(record?.NO)
      || hasHandwrittenText(record?.NOM_PRENOM)
      || hasHandwrittenText(record?.Mark)
      || hasHandwrittenText(record?.mark)
      || hasHandwrittenText(record?.smartMark)
      || hasHandwrittenText(anomalyText)
  }

  const resolveTaskCountry = () => {
    if (typeof taskCountry === 'function') return taskCountry()
    if (taskCountry && typeof taskCountry === 'object' && 'value' in taskCountry) return taskCountry.value
    return taskCountry
  }

  const computeDisplaySmartMark = (record) => {
    let raw = refreshNightShiftInSmartMark(getRawSmartMark(record), record, resolveTaskCountry())
    const hasHandwritten = hasHandwrittenIdentity(record) || raw.includes('手写')
    if (!hasHandwritten || raw.includes('已删除') || raw.includes('未出勤')) {
      return raw || '-'
    }
    if (!raw || raw === '-' || raw === '正常') return '手写'
    if (raw.includes('手写')) return raw
    return `${raw};手写`
  }

  const getDisplaySmartMark = (record) => {
    const cached = readCache(record)
    if (cached?.displayMark != null) return cached.displayMark
    const displayMark = computeDisplaySmartMark(record)
    writeCache(record, { ...(cached || {}), displayMark })
    return displayMark
  }

  const getRecordMarkTags = (record) => {
    const cached = readCache(record)
    if (cached?.markTags) return cached.markTags
    const markTags = buildRecordMarkTags(record, {
      getDisplayMark: getDisplaySmartMark,
      isAbsentRow,
      t,
      hasManualCalibration,
    })
    writeCache(record, { ...(readCache(record) || {}), markTags })
    return markTags
  }

  const SHIFT_PHRASE_I18N = {
    earlyArrival: 'taskEdit.shiftVarianceEarlyArrival',
    lateArrival: 'taskEdit.shiftVarianceLateArrival',
    earlyLeave: 'taskEdit.shiftVarianceEarlyLeave',
    overtime: 'taskEdit.shiftVarianceOvertime',
  }

  const formatShiftDurationLabel = (totalMinutes) => {
    const parts = splitDurationMinutes(totalMinutes) || { total: 0, hours: 0, minutes: 0 }
    const total = Number(parts.total) || 0
    const hours = Number(parts.hours) || 0
    const minutes = Number(parts.minutes) || 0
    if (total < 60) {
      const text = t('taskEdit.shiftVarianceDurationMinutes', { minutes: total })
      return text !== 'taskEdit.shiftVarianceDurationMinutes' ? text : `${total} min`
    }
    if (minutes === 0) {
      const text = t('taskEdit.shiftVarianceDurationHours', { hours })
      return text !== 'taskEdit.shiftVarianceDurationHours' ? text : `${hours} h`
    }
    const text = t('taskEdit.shiftVarianceDurationHoursMinutes', { hours, minutes })
    return text !== 'taskEdit.shiftVarianceDurationHoursMinutes'
      ? text
      : `${hours} h ${minutes} min`
  }

  const computeShiftVariancePhrases = (record) => {
    try {
      if (!record || record.isDeleted || isAbsentRow?.(record)) return []
      const variance = computeShiftVarianceMinutes(record)
      return formatShiftVariancePhrases(variance, (key, minutes) => {
        const i18nKey = SHIFT_PHRASE_I18N[key]
        const duration = formatShiftDurationLabel(minutes)
        if (!i18nKey) return `${key} ${duration}`
        const translated = t(i18nKey, { duration, minutes })
        return translated && translated !== i18nKey ? translated : `${key} ${duration}`
      }) || []
    } catch (error) {
      console.warn('[taskEdit] shift variance failed', error)
      return []
    }
  }

  const getRecordShiftVariancePhrases = (record) => {
    const cached = readCache(record)
    if (cached?.shiftPhrases) return cached.shiftPhrases
    const shiftPhrases = computeShiftVariancePhrases(record)
    writeCache(record, { ...(readCache(record) || {}), shiftPhrases })
    return shiftPhrases
  }

  /** 早到/迟到/早离开/晚离开合并为一句展示 */
  const getRecordShiftVarianceSentence = (record) => {
    const cached = readCache(record)
    if (cached?.shiftSentence != null) return cached.shiftSentence
    const phrases = getRecordShiftVariancePhrases(record)
    let shiftSentence = ''
    if (phrases.length) {
      const joinKey = 'taskEdit.shiftVarianceJoin'
      const prefixKey = 'taskEdit.shiftVariancePrefix'
      const join = t(joinKey)
      const prefix = t(prefixKey)
      const joinText = join && join !== joinKey ? join : '且'
      const prefixText = prefix && prefix !== prefixKey ? prefix : '员工'
      shiftSentence = `${prefixText}${phrases.join(joinText)}`
    }
    writeCache(record, { ...(readCache(record) || {}), shiftSentence })
    return shiftSentence
  }

  const formatDiffValue = (value) => {
    const text = String(value ?? '').trim()
    if (!text) {
      const empty = t('taskEdit.fieldChangeEmpty')
      return empty !== 'taskEdit.fieldChangeEmpty' ? empty : '（空）'
    }
    return text
  }

  const formatOneDiff = (diff) => {
    const labelKey = FIELD_LABEL_KEYS[diff.field] || REQUIRED_FIELD_I18N_KEYS[diff.field]
    const label = labelKey ? t(labelKey) : diff.field
    const line = t('taskEdit.fieldChangeFromTo', {
      field: label,
      from: formatDiffValue(diff.from),
      to: formatDiffValue(diff.to),
    })
    if (line && line !== 'taskEdit.fieldChangeFromTo') return line
    return `${label}: ${formatDiffValue(diff.from)} → ${formatDiffValue(diff.to)}`
  }

  const computeFieldChangeDiffs = (record) => {
    try {
      if (!record || record.isDeleted) return []
      const diffs = getBaselineFieldDiffs(record, SNAPSHOT_FIELD_KEYS)
      const list = Array.isArray(diffs) ? diffs : []
      if (!list.length) return localGetBaselineFieldDiffs(record, SNAPSHOT_FIELD_KEYS)
      return list
    } catch (error) {
      console.warn('[taskEdit] field change diffs failed', error)
      return localGetBaselineFieldDiffs(record, SNAPSHOT_FIELD_KEYS)
    }
  }

  const getRecordFieldChangeDiffs = (record) => {
    const cached = readCache(record)
    if (cached && Array.isArray(cached.fieldChangeDiffs)) return cached.fieldChangeDiffs
    const fieldChangeDiffs = computeFieldChangeDiffs(record)
    writeCache(record, { ...(readCache(record) || {}), fieldChangeDiffs })
    return fieldChangeDiffs
  }

  const getRecordFieldChangeLines = (record) => {
    const cached = readCache(record)
    if (cached && Array.isArray(cached.fieldChangeLines)) return cached.fieldChangeLines
    const fieldChangeLines = getRecordFieldChangeDiffs(record).map(formatOneDiff)
    writeCache(record, { ...(readCache(record) || {}), fieldChangeLines })
    return fieldChangeLines
  }

  /** 单元格下方小字：旧值 → 新值（不含字段名）；已删除/未出勤不展示 */
  const getFieldChangeHint = (record, field) => {
    if (!record || !field) return ''
    if (record.isDeleted || isAbsentRow?.(record)) return ''
    const diff = getRecordFieldChangeDiffs(record).find((item) => item.field === field)
    if (!diff) return ''
    const line = t('taskEdit.fieldChangeInline', {
      from: formatDiffValue(diff.from),
      to: formatDiffValue(diff.to),
    })
    if (line && line !== 'taskEdit.fieldChangeInline') return line
    return `${formatDiffValue(diff.from)} → ${formatDiffValue(diff.to)}`
  }

  const getSmartMarkDisplay = (record) => {
    const mark = getDisplaySmartMark(record)
    if (mark.includes('未出勤')) {
      const shift = record?.HORAIRES_DU_TRAVAIL || ''
      return shift ? `未出勤-${shift}` : '未出勤'
    }
    return mark
  }

  const getEffectiveAnomalies = (record) => {
    const anomalies = Array.isArray(record?.anomalies) ? record.anomalies : []
    return anomalies.filter(
      (reason) => reason && !String(reason).includes(t('home.statsNight')) && !String(reason).includes('夜班'),
    )
  }

const ANOMALY_CATEGORY_I18N = {
  required: 'taskEdit.anomalyCategoryRequired',
  unreadable: 'taskEdit.anomalyCategoryUnreadable',
  format: 'taskEdit.anomalyCategoryFormat',
  duplicate: 'taskEdit.anomalyCategoryDuplicate',
  other: 'taskEdit.anomalyCategoryOther',
}

const ANOMALY_CATEGORY_FALLBACK = {
  required: '必填缺失',
  unreadable: '看不清',
  format: '格式异常',
  duplicate: '重名',
  other: '其他异常',
}

  const getAnomalyCategoryLabel = (category) => {
    const key = ANOMALY_CATEGORY_I18N[category]
    if (key) {
      const translated = t(key)
      if (translated && translated !== key) return translated
    }
    return ANOMALY_CATEGORY_FALLBACK[category] || category
  }

  const computeRecordAnomalyGroups = (record) => {
    if (!record || record.isDeleted) return []

    const bucket = new Map()
    const addItem = (category, text) => {
      const value = String(text || '').trim()
      if (!value) return
      if (!bucket.has(category)) bucket.set(category, new Set())
      bucket.get(category).add(value)
    }

    getEffectiveAnomalies(record).forEach((reason) => {
      if (isMarkRedundantAnomalyReason(reason, t)) return
      addItem('other', translateAnomalyReason(reason, t))
    })

    if (Array.isArray(record._unreadableFields)) {
      record._unreadableFields.forEach((fieldKey) => {
        const labelKey = FIELD_LABEL_KEYS[fieldKey]
        addItem('unreadable', labelKey ? t(labelKey) : fieldKey)
      })
    }

    getMissingRequiredFieldKeys(record).forEach((fieldKey) => {
      const labelKey = REQUIRED_FIELD_I18N_KEYS[fieldKey] || FIELD_LABEL_KEYS[fieldKey]
      addItem('required', labelKey ? t(labelKey) : fieldKey)
    })

    if (isArrivalDepartureSameTime(record)) {
      const sameTimeLabel = t('fieldFormat.sameTimeShort')
      addItem('format', sameTimeLabel !== 'fieldFormat.sameTimeShort' ? sameTimeLabel : '到达与离开时间相同')
    }

    if (record._parseMalformed) {
      const malformedLabel = t('taskEdit.parseMalformedShort')
      addItem('format', malformedLabel !== 'taskEdit.parseMalformedShort' ? malformedLabel : '结构异常')
    }

    const duplicateMeta = getDuplicateMeta(record)
    if (duplicateMeta?.peers?.length) {
      addItem('duplicate', duplicateMeta.peers.join('、'))
    }

    return ANOMALY_CATEGORY_ORDER
      .filter((category) => bucket.has(category))
      .map((category) => {
        const items = [...bucket.get(category)]
        const label = getAnomalyCategoryLabel(category)
        const sep = t('taskEdit.confirmValidationFieldSep')
        return {
          category,
          label,
          items,
          summary: `${label}：${items.join(sep)}`,
        }
      })
  }

  const getRecordAnomalyGroups = (record) => {
    const cached = readCache(record)
    if (cached?.anomalyGroups) return cached.anomalyGroups
    const anomalyGroups = computeRecordAnomalyGroups(record)
    writeCache(record, { ...(readCache(record) || {}), anomalyGroups })
    return anomalyGroups
  }

  const resolveRecognitionNoteTone = ({ tag, category, kind } = {}) => {
    if (kind === 'shift') return 'shift'
    if (category === 'required') return 'danger'
    if (category === 'unreadable') return 'warning'
    if (category === 'format') return 'warning'
    if (category === 'duplicate') return 'accent'
    if (tag?.key === 'manual-calibration') return 'accent'
    if (tag?.key === 'manually-added') return 'primary'
    if (tag?.key === 'absent' || tag?.key === 'deleted') return 'danger'
    const color = String(tag?.color || '')
    if (color === 'purple') return 'night'
    if (color === 'error' || color === 'red') return 'danger'
    if (color === 'warning' || color === 'gold') return 'warning'
    if (color === 'orange') return 'accent'
    if (color === 'processing' || color === 'blue') return 'primary'
    if (color === 'success') return 'success'
    return 'default'
  }

  const computeRecognitionNoteItems = (record) => {
    const items = []
    getRecordMarkTags(record).forEach((tag) => {
      if (!tag?.label) return
      items.push({
        key: `mark-${tag.key}`,
        text: tag.label,
        tone: resolveRecognitionNoteTone({ tag }),
        showCalibrationHistory: Boolean(tag.showCalibrationHistory),
      })
    })
    const shiftSentence = getRecordShiftVarianceSentence(record)
    if (shiftSentence) {
      items.push({
        key: 'shift-sentence',
        text: shiftSentence,
        tone: resolveRecognitionNoteTone({ kind: 'shift' }),
      })
    }
    getRecordAnomalyGroups(record).forEach((group) => {
      if (!group?.summary) return
      items.push({
        key: `group-${group.category}`,
        text: group.summary,
        tone: resolveRecognitionNoteTone({ category: group.category }),
      })
    })
    return items
  }

  const getRecognitionNoteItems = (record) => {
    const cached = readCache(record)
    if (cached && Array.isArray(cached.recognitionNoteItems)) return cached.recognitionNoteItems
    const recognitionNoteItems = computeRecognitionNoteItems(record)
    writeCache(record, { ...(readCache(record) || {}), recognitionNoteItems })
    return recognitionNoteItems
  }

  const getRecordAnomalyReasons = (record) => {
    const cached = readCache(record)
    if (cached?.anomalyReasons) return cached.anomalyReasons
    const anomalyReasons = getRecognitionNoteItems(record).map((item) => item.text).filter(Boolean)
    writeCache(record, { ...(readCache(record) || {}), anomalyReasons })
    return anomalyReasons
  }

  const hasAnomalyColumnContent = (record) => getRecognitionNoteItems(record).length > 0

  const getRowClassName = (record) => {
    const cached = readCache(record)
    if (cached?.rowClassName != null) return cached.rowClassName
    if (!record) return ''
    let rowClassName = ''
    if (record?.isDeleted || record?.deleted) rowClassName = 'deleted-row'
    else if (typeof isAbsentRow === 'function' && isAbsentRow(record)) rowClassName = 'absent-row'
    else if (record?._parseMalformed) rowClassName = 'parse-malformed-row'
    else if (record?._manuallyAdded) rowClassName = 'manual-added-row'
    else {
      const mark = getDisplaySmartMark(record)
      if (markContains(mark, 'absent') || String(mark).includes('未出勤')) rowClassName = 'absent-row'
      else if (markContains(mark, 'blurred')) rowClassName = 'blurred-row'
    }
    writeCache(record, { ...(readCache(record) || {}), rowClassName })
    return rowClassName
  }

  const getMarkColor = (mark) => {
    if (!mark) return 'default'
    const parts = String(mark).split(/[;；,，]/).map((p) => p.trim()).filter(Boolean)
    for (const part of parts) {
      if (part === '未签字' || part === '未签字确认') return 'warning'
      if (part === '已签字' || part === '已签字确认') return 'success'
    }
    if (markContains(mark, 'absent')) return 'error'
    if (markContains(mark, 'blurred')) return 'warning'
    if (markContains(mark, 'handwriting')) return 'processing'
    if (markContains(mark, 'nightShift')) return 'purple'
    if (markContains(mark, 'normal')) return 'success'
    return 'default'
  }

  const getRowTypeLabel = (record) => {
    if (record?.isDeleted) return '已删除'
    const mark = getDisplaySmartMark(record)
    if (mark.includes('未出勤')) return '未出勤'
    if (mark.includes('模糊')) return '模糊'
    if (mark.includes('手写')) return '手写'
    return '正常'
  }

  const getRowTypeDotClass = (record) => {
    if (record?.isDeleted) return 'dot-deleted'
    const mark = getDisplaySmartMark(record)
    if (mark.includes('未出勤')) return 'dot-absent'
    if (mark.includes('模糊')) return 'dot-blurred'
    if (mark.includes('手写')) return 'dot-handwritten'
    return 'dot-normal'
  }

  const getAnomalyTagColor = (reason) => {
    const kind = anomalyReasonKind(reason)
    if (kind === 'missing' || /^必填/.test(reason)) return 'red'
    if (kind === 'duplicate' || /^重名/.test(reason)) return 'orange'
    if (/看不清/.test(reason) || kind === 'blurred') return 'gold'
    if (kind === 'deleted') return 'default'
    return 'default'
  }

  const getAnomalyCategoryColor = (category) => {
    if (category === 'required') return 'red'
    if (category === 'unreadable') return 'gold'
    if (category === 'format') return 'warning'
    if (category === 'duplicate') return 'orange'
    return 'default'
  }

  const getAnomalyTagClass = (reason) => {
    if (reason.includes(t('home.statsAbsent'))) return 'tag-red'
    if (reason.includes(t('home.statsBlurred'))) return 'tag-amber'
    if (reason.includes(t('home.statsHandwriting'))) return 'tag-blue'
    return 'tag-default'
  }

  const hasRecordAnomaly = (record) => {
    if (!record || record.isDeleted) return false
    if (getRecordAnomalyGroups(record).length > 0) return true
    if (getRecordShiftVarianceSentence(record)) return true
    return getRecordMarkTags(record).some((tag) => tag.key !== 'manually-added')
  }

  /** 需关注：识别异常 + 已删除 + 未出勤 */
  const needsAttentionRecord = (record) => {
    if (!record) return false
    if (record.isDeleted || record.deleted) return true
    if (typeof isAbsentRow === 'function' && isAbsentRow(record)) return true
    return hasRecordAnomaly(record)
  }

  const countAnomalyRecords = (list = records.value) => {
    let count = 0
    for (const record of list) {
      if (hasRecordAnomaly(record)) count++
    }
    return count
  }

  const countAttentionRecords = (list = records.value) => {
    let count = 0
    for (const record of list) {
      if (needsAttentionRecord(record)) count++
    }
    return count
  }

  /** 与 hasRecordAnomaly 口径一致：含班次偏差 / 识别标记，避免「有条数却展开为空」 */
  const buildAnomalyAlertsSlice = (list = records.value, limit = 20) => {
    const alerts = []
    for (const record of list) {
      if (!hasRecordAnomaly(record)) continue
      const groups = [...getRecordAnomalyGroups(record)]
      const shiftSentence = getRecordShiftVarianceSentence(record)
      if (shiftSentence) {
        const label = getAnomalyCategoryLabel('other')
        groups.push({
          category: 'other',
          label,
          items: [shiftSentence],
          summary: shiftSentence,
        })
      }
      if (groups.length === 0) {
        const markTags = getRecordMarkTags(record).filter((tag) => tag.key !== 'manually-added')
        if (markTags.length > 0) {
          const label = getAnomalyCategoryLabel('other')
          const items = markTags.map((tag) => tag.label || tag.key).filter(Boolean)
          if (items.length) {
            groups.push({
              category: 'other',
              label,
              items,
              summary: `${label}：${items.join(t('taskEdit.confirmValidationFieldSep'))}`,
            })
          }
        }
      }
      if (groups.length === 0) continue
      alerts.push({
        name: `${record.NO || '?'} - ${record.NOM_PRENOM || '?'}`,
        groups,
      })
      if (alerts.length >= limit) break
    }
    return alerts
  }

  return {
    stats,
    statItems,
    getRecordMarkTags,
    getDisplaySmartMark,
    getSmartMarkDisplay,
    getEffectiveAnomalies,
    getRecordAnomalyReasons,
    getRecognitionNoteItems,
    getRecordAnomalyGroups,
    getRecordShiftVariancePhrases,
    getRecordShiftVarianceSentence,
    getRecordFieldChangeLines,
    getFieldChangeHint,
    hasAnomalyColumnContent,
    getRowClassName,
    getMarkColor,
    getRowTypeLabel,
    getRowTypeDotClass,
    getAnomalyTagColor,
    getAnomalyCategoryColor,
    getAnomalyTagClass,
    hasRecordAnomaly,
    countAnomalyRecords,
    needsAttentionRecord,
    countAttentionRecords,
    buildAnomalyAlertsSlice,
    recordMatchesStatFilter,
    clearRowCache,
    invalidateRowCache,
  }
}
