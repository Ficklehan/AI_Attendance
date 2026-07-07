/** 与 PC 端 Home.vue / TaskEdit.vue 一致的记录展示逻辑 */

const { displayFieldValue, isPlaceholderValue } = require('./fieldPlaceholder')
const { t, tOr } = require('./i18n')
const { formatPaysFieldDisplay } = require('./paysCountryPicker')
const { hasManualCalibration, buildCalibrationHistoryUi } = require('./calibrationHistory')
const {
  translateAnomalyReason,
  translateSmartMark,
  translateSmartMarkPart,
  splitSmartMarkParts,
  markContains,
  anomalyReasonKind,
  getDisplaySignature,
  computeSignatureMark,
  normalizeLegacySignature,
  calculateRecordStats,
  stripSignatureMarksFromSmartMark,
  translateSignatureMark,
  refreshNightShiftInSmartMark,
  getRawSmartMark
} = require('./recognitionLabels')

function pickField(record, ...keys) {
  if (!record) return ''
  for (let i = 0; i < keys.length; i++) {
    const v = record[keys[i]]
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      return String(v).trim()
    }
  }
  return ''
}

function stripDisplayNoise(text) {
  if (text === undefined || text === null) return ''
  return String(text)
    .replace(/[\u200d\u26A0\uFE0E\uFE0F\u25B2\u25B3\u25B4\u25B5\u2757]+/g, '')
    .replace(/[\u2300-\u27BF]/g, '')
    .replace(/[⚠☢☣❗‼⛔🚫🚨🔺🔻]/g, '')
    .replace(/[|｜]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

const { normalizeWorkerNo } = require('../shared-js/workerNoNormalize')
const { normalizePersonName } = require('../shared-js/recognizedTextNormalizer')

function cleanWorkerNo(no) {
  return normalizeWorkerNo(stripDisplayNoise(no))
}

function cleanPersonName(name) {
  return normalizePersonName(stripDisplayNoise(name))
}

function buildTimeRange(arrive, depart) {
  const placeholder = tOr('result.timeRangePlaceholder', null, '--:--')
  if (!arrive && !depart) return placeholder
  return `${arrive || placeholder}-${depart || placeholder}`
}

function formatPauseText(pause) {
  const minutes = normalizePauseMinutes(pause)
  if (minutes === '') return '-'
  const unit = tOr('result.pauseMinutesUnit', null, 'min')
  return `${minutes} ${unit}`
}

function normalizePauseMinutes(pause) {
  if (pause === '' || pause === undefined || pause === null) return ''
  const normalized = String(pause)
    .trim()
    .toLowerCase()
    .replace(',', '.')
    .replace(/\s+/g, '')
    .replace(/minutes?|mins?|mn/g, 'min')
  if (isPlaceholderValue(normalized)) return ''

  const hourMatch = normalized.match(/^(\d+(?:\.\d+)?)h(\d+(?:\.\d+)?)?(?:min|m)?$/)
  if (hourMatch) {
    const hours = Number(hourMatch[1])
    const minutes = hourMatch[2] ? Number(hourMatch[2]) : 0
    return Number.isNaN(hours) || Number.isNaN(minutes) ? pause : Math.round(hours * 60 + minutes)
  }
  const colonMatch = normalized.match(/^(\d{1,2}):(\d{1,2})$/)
  if (colonMatch) return Number(colonMatch[1]) * 60 + Number(colonMatch[2])
  const minuteMatch = normalized.match(/^(\d+(?:\.\d+)?)(?:min|m)?$/)
  if (minuteMatch) {
    const minutes = Number(minuteMatch[1])
    return Number.isNaN(minutes) ? pause : Math.round(minutes)
  }
  return pause
}

function hasHandwrittenText(text) {
  const normalized = String(text || '').toLowerCase()
  return normalized.includes('手写')
    || normalized.includes('handwritten')
    || normalized.includes('manuscrit')
    || normalized.includes('manuscrite')
    || normalized.includes('ecrit main')
    || normalized.includes('écrit main')
    || normalized.includes('ecrit a la main')
    || normalized.includes('écrit à la main')
}

function hasHandwrittenIdentity(record) {
  const anomalies = Array.isArray(record && record.anomalies) ? record.anomalies.join(' ') : ''
  return hasHandwrittenText(pickField(record, 'NO'))
    || hasHandwrittenText(pickField(record, 'NOM_PRENOM', 'Name'))
    || hasHandwrittenText(pickField(record, 'Mark', 'mark', 'smartMark'))
    || hasHandwrittenText(anomalies)
}

function getEffectiveSmartMark(record) {
  const raw = refreshNightShiftInSmartMark(getRawSmartMark(record), record)
  const hasHandwritten = hasHandwrittenIdentity(record) || markContains(raw, 'handwriting')
  if (!hasHandwritten || markContains(raw, 'deleted') || markContains(raw, 'absent')) {
    return raw
  }
  if (!raw || raw === '-' || markContains(raw, 'normal')) return '手写'
  if (markContains(raw, 'handwriting')) return raw
  return `${raw};手写`
}

function isAbsentRow(record) {
  if (!record || record._restored || record.isDeleted) return false
  if (record._manuallyAdded) return false
  const mark = getEffectiveSmartMark(record)
  if (mark.indexOf('未出勤') !== -1 || markContains(mark, 'absent')) return true
  const arrive = pickField(record, 'ARRIVEE', 'ArriveTime')
  const depart = pickField(record, 'DEPAR', 'DepartTime')
  return !arrive && !depart
}

const { FIELD_LABEL_KEYS } = require('./calibratableFields')
const { hasRequiredMissing, getMissingRequiredFieldKeys, isConfiguredRequiredField, appendRequiredMark } = require('./requiredRecordFields')
const { getInvalidFormatFieldKeys, isArrivalDepartureSameTime } = require('./recordFieldFormatRules')
const { getFormatHintKeys } = require('./fieldFormatHints')

const ANOMALY_CATEGORY_ORDER = ['required', 'unreadable', 'duplicate', 'other']

const ANOMALY_CATEGORY_I18N = {
  required: 'result.anomalyCategoryRequired',
  unreadable: 'result.anomalyCategoryUnreadable',
  duplicate: 'result.anomalyCategoryDuplicate',
  other: 'result.anomalyCategoryOther',
}

const ANOMALY_CATEGORY_FALLBACK = {
  required: '必填缺失',
  unreadable: '看不清',
  duplicate: '重名',
  other: '其他异常',
}

function getAnomalyCategoryLabel(category) {
  const key = ANOMALY_CATEGORY_I18N[category]
  if (key) {
    const translated = t(key)
    if (translated && translated !== key) return translated
  }
  return ANOMALY_CATEGORY_FALLBACK[category] || category
}

function getCategoryLevelClass(category) {
  if (category === 'required') return 'reason-danger'
  if (category === 'unreadable' || category === 'duplicate') return 'reason-warn'
  return 'reason-info'
}

function isMarkRedundantAnomalyReason(reason) {
  const raw = String(reason || '').trim()
  if (!raw) return true
  if (raw.startsWith('missing.')) return true
  if (raw === 'deleted.record') return true
  if (['内容模糊', '手写内容', '未出勤'].includes(raw)) return true
  const translated = translateAnomalyReason(reason, t)
  const kind = anomalyReasonKind(translated)
  return kind === 'blurred' || kind === 'handwriting' || kind === 'absent' || kind === 'night'
}

function getEffectiveAnomalies(record) {
  const anomalies = Array.isArray(record?.anomalies) ? record.anomalies : []
  return anomalies.filter(
    (reason) => reason && !String(reason).includes(t('result.statsNight') || '夜班') && !String(reason).includes('夜班'),
  )
}

function collectAnomalyGroups(record) {
  if (!record || record.isDeleted) return []

  const bucket = new Map()
  const addItem = (category, text) => {
    const value = String(text || '').trim()
    if (!value) return
    if (!bucket.has(category)) bucket.set(category, new Set())
    bucket.get(category).add(value)
  }

  getEffectiveAnomalies(record).forEach((reason) => {
    if (isMarkRedundantAnomalyReason(reason)) return
    addItem('other', translateAnomalyReason(String(reason), t))
  })

  if (Array.isArray(record._unreadableFields)) {
    record._unreadableFields.forEach((fieldKey) => {
      const labelKey = FIELD_LABEL_KEYS[fieldKey]
      addItem('unreadable', labelKey ? t(labelKey) : fieldKey)
    })
  }

  getMissingRequiredFieldKeys(record).forEach((fieldKey) => {
    const labelKey = FIELD_LABEL_KEYS[fieldKey]
    addItem('required', labelKey ? t(labelKey) : fieldKey)
  })

  if (isArrivalDepartureSameTime(record)) {
    addItem('other', tOr('fieldFormat.sameTimeShort', null, '到达与离开时间相同'))
  }

  if (record._duplicatePeers && record._duplicatePeers.length) {
    addItem('duplicate', record._duplicatePeers.join('、'))
  }

  return ANOMALY_CATEGORY_ORDER
    .filter((category) => bucket.has(category))
    .map((category) => {
      const items = [...bucket.get(category)]
      const label = getAnomalyCategoryLabel(category)
      const sep = t('result.confirmValidationFieldSep')
      return {
        category,
        label,
        items,
        summary: `${label}：${items.join(sep)}`,
      }
    })
}

function buildGroupBadges(groups, maxCount) {
  const limit = typeof maxCount === 'number' ? maxCount : groups.length
  return (groups || []).slice(0, limit).map((group) => ({
    category: group.category,
    summary: group.summary,
    levelClass: getCategoryLevelClass(group.category),
  }))
}

function getMarkTag(mark) {
  if (!mark) return 'tag-default'
  const parts = String(mark).split(/[;；,，]/).map((p) => p.trim()).filter(Boolean)
  for (const part of parts) {
    if (part === '未签字' || part === '未签字确认') return 'tag-warning'
    if (part === '已签字' || part === '已签字确认') return 'tag-success'
  }
  if (markContains(mark, 'deleted')) return 'tag-default'
  if (markContains(mark, 'absent')) return 'tag-error'
  if (markContains(mark, 'blurred')) return 'tag-warning'
  if (markContains(mark, 'handwriting')) return 'tag-info'
  if (markContains(mark, 'nightShift')) return 'tag-purple'
  if (markContains(mark, 'normal')) return 'tag-success'
  return 'tag-default'
}

function getMarkTagForPart(part) {
  const p = String(part || '').trim()
  if (p === '未签字' || p === '未签字确认') return 'tag-warning'
  if (p === '已签字' || p === '已签字确认') return 'tag-success'
  return getMarkTag(part)
}

function buildRecordMarkLabels(record) {
  if (record.isDeleted) {
    return [{ key: 'deleted', label: t('recognition.marks.deleted'), tagClass: 'tag-default' }]
  }
  if (isAbsentRow(record)) {
    return [{ key: 'absent', label: t('recognition.marks.absent'), tagClass: 'tag-error' }]
  }
  const mark = getEffectiveSmartMark(record)
  let parts = splitSmartMarkParts(mark)
  if (!parts.length) {
    parts = ['正常']
  }
  const labels = parts.map((part, index) => ({
    key: `mark-${part}-${index}`,
    label: translateSmartMarkPart(part, t),
    tagClass: getMarkTagForPart(part),
    rowKey: record._rowKey
  }))
  if (hasManualCalibration(record)) {
    labels.push({
      key: 'manual-calibration',
      label: t('calibration.manualTag'),
      tagClass: 'tag-calibration',
      rowKey: record._rowKey,
      toggleHistory: true
    })
  }
  return labels
}

function getRowTypeLabel(record) {
  if (record.isDeleted) return t('recognition.marks.deleted')
  const mark = getEffectiveSmartMark(record)
  if (markContains(mark, 'absent')) return t('recognition.marks.absent')
  if (markContains(mark, 'blurred')) return t('recognition.marks.blurred')
  if (markContains(mark, 'handwriting')) return t('recognition.marks.handwriting')
  if (hasRequiredMissing(record)) return t('result.requiredFieldMissingShort')
  return t('recognition.marks.normal')
}

function getRowTypeTag(record) {
  if (record.isDeleted) return 'tag-default'
  const mark = getEffectiveSmartMark(record)
  if (markContains(mark, 'absent')) return 'tag-error'
  if (markContains(mark, 'blurred')) return 'tag-warning'
  if (markContains(mark, 'handwriting')) return 'tag-info'
  if (hasRequiredMissing(record)) return 'tag-orange'
  return 'tag-success'
}

function getRowDotClass(record) {
  if (record.isDeleted) return 'dot-deleted'
  const mark = getEffectiveSmartMark(record)
  if (markContains(mark, 'absent')) return 'dot-absent'
  if (markContains(mark, 'blurred')) return 'dot-blurred'
  if (hasRequiredMissing(record)) return 'dot-incomplete'
  return 'dot-normal'
}

function getRowClass(record) {
  if (record.isDeleted) return 'deleted'
  if (isAbsentRow(record)) return 'absent'
  if (markContains(getEffectiveSmartMark(record), 'blurred')) return 'blurred'
  if (hasRequiredMissing(record)) return 'incomplete'
  return ''
}

function getSmartMarkDisplay(record) {
  const mark = getEffectiveSmartMark(record)
  if (markContains(mark, 'absent')) {
    const shift = pickField(record, 'HORAIRES_DU_TRAVAIL')
    const absentLabel = t('recognition.marks.absent')
    return shift ? `${absentLabel}-${shift}` : absentLabel
  }
  return translateSmartMark(mark || t('recognition.marks.normal'), t)
}

function parseTimeToMinutes(timeStr) {
  if (!timeStr || timeStr === '???') return null
  const clean = String(timeStr).trim().replace(',', '.').replace(/h/gi, ':')
  const parts = clean.split(':')
  if (parts.length === 2) {
    const h = parseInt(parts[0], 10)
    const m = parseInt(parts[1], 10)
    if (!Number.isNaN(h) && !Number.isNaN(m)) return h * 60 + m
  }
  return null
}

function computeWorkHours(record) {
  if (isAbsentRow(record) || record.isDeleted) return '-'
  const arrive = parseTimeToMinutes(pickField(record, 'ARRIVEE', 'ArriveTime'))
  const depart = parseTimeToMinutes(pickField(record, 'DEPAR', 'DepartTime'))
  if (arrive === null || depart === null) return '-'
  let total = depart - arrive
  if (total < 0) total += 24 * 60
  const pauseStr = pickField(record, 'PAUSE')
  const normalizedPause = normalizePauseMinutes(pauseStr)
  const pause = normalizedPause === '' ? 0 : Number(normalizedPause)
  const work = total - (Number.isNaN(pause) ? 0 : pause)
  if (work < 0) return '-'
  return (work / 60).toFixed(2)
}

function collectAnomalyReasons(record) {
  const reasons = []
  const anomalies = record.anomalies || []
  if (Array.isArray(anomalies)) {
    anomalies.forEach((a) => {
      if (a && !String(a).includes('夜班') && !String(a).includes('night')) {
        reasons.push(translateAnomalyReason(String(a), t))
      }
    })
  }
  const mark = getEffectiveSmartMark(record)
  if (markContains(mark, 'blurred')) reasons.push(t('result.blurredContent'))
  if (markContains(mark, 'handwriting')) reasons.push(t('result.handwrittenContent'))
  if (markContains(mark, 'absent')) reasons.push(t('result.absentReason'))
  if (hasRequiredMissing(record)) reasons.push(t('result.requiredFieldMissingShort'))
  if (record._duplicatePeers && record._duplicatePeers.length) {
    reasons.push(t('result.duplicateSuspect', { names: record._duplicatePeers.join('、') }))
  }
  return [...new Set(reasons)]
}

function anomalyReasonLevelClass(reason) {
  if (!reason) return 'reason-info'
  const kind = anomalyReasonKind(reason)
  if (kind === 'missing' || kind === 'absent') return 'reason-danger'
  if (kind === 'blurred' || kind === 'duplicate') return 'reason-warn'
  if (kind === 'handwriting') return 'reason-info'
  return 'reason-info'
}

function anomalyReasonSeverity(reason) {
  const cls = anomalyReasonLevelClass(reason)
  if (cls === 'reason-danger') return 4
  if (cls === 'reason-warn') return 3
  if (reason && reason.includes('重名')) return 3
  if (cls === 'reason-info') return 2
  return 1
}

function buildReasonBadges(reasons, maxCount) {
  const ordered = [...(reasons || [])].sort((a, b) => anomalyReasonSeverity(b) - anomalyReasonSeverity(a))
  return ordered.slice(0, maxCount).map((reason) => ({
    text: reason,
    levelClass: anomalyReasonLevelClass(reason)
  }))
}

function isEmptyFieldValue(value) {
  return isPlaceholderValue(value)
}

function buildRecordFieldRows(record, ctx) {
  const skipRequired = !!(record.isDeleted || isAbsentRow(record))
  const missingKeys = skipRequired ? [] : getMissingRequiredFieldKeys(record)
  const formatInvalidKeys = skipRequired ? [] : getInvalidFormatFieldKeys(record)

  function cell(key, labelKey, raw, display) {
    let value = '-'
    if (display !== undefined && display !== null && String(display).trim() !== '' && !isEmptyFieldValue(display)) {
      value = String(display).trim()
    } else if (!isEmptyFieldValue(raw)) {
      value = String(raw).trim()
    }
    const required = isConfiguredRequiredField(key)
    const label = required ? appendRequiredMark(t(labelKey)) : t(labelKey)
    const missing = missingKeys.indexOf(key) !== -1
    const formatInvalid = formatInvalidKeys.indexOf(key) !== -1
    let formatHint = ''
    if (formatInvalid) {
      const hintKeys = getFormatHintKeys(key, { record, isSameArrivalDeparture: isArrivalDepartureSameTime })
      if (hintKeys) {
        const short = t(hintKeys.short)
        formatHint = short !== hintKeys.short ? short : t(hintKeys.tooltip)
      }
    }
    return { key, label, value, missing, formatInvalid, required, formatHint }
  }

  const primaryRow = [
    cell('PAGE_NUM', 'result.fieldPageNumber', ctx.PAGE_NUM, ctx.pageNumText),
    cell('NO', 'result.fieldWorkerNo', ctx.NO, ctx.displayNo),
    cell('Date', 'result.fieldDate', ctx.Date, ctx.dateText),
    cell('ARRIVEE', 'result.fieldArrival', ctx.ARRIVEE, ctx.ARRIVEE),
    cell('DEPAR', 'result.fieldDeparture', ctx.DEPAR, ctx.DEPAR),
    cell('PAUSE', 'result.fieldBreak', ctx.PAUSE, ctx.pauseText),
    cell('workHours', 'result.fieldWorkHours', ctx.workHours, ctx.workHoursText)
  ]

  const contextRow = [
    cell('Pays', 'result.fieldCountry', ctx.Pays, ctx.paysDisplayText),
    cell('Entrepot', 'result.fieldWarehouse', ctx.Entrepot, null),
    cell('HORAIRES_DU_TRAVAIL', 'result.fieldShift', ctx.HORAIRES_DU_TRAVAIL, null),
    cell('AGENCE_INTERIMAIRE', 'result.fieldAgency', ctx.AGENCE_INTERIMAIRE, null),
    cell('SIGNATURE', 'result.fieldSignature', ctx.SIGNATURE, ctx.signatureDisplayText),
    cell('Observations', 'result.fieldObservations', ctx.Observations, null)
  ]

  return { primaryRow, contextRow }
}

function enrichRecord(record, index) {
  const NO = pickField(record, 'NO')
  const NOM_PRENOM = pickField(record, 'NOM_PRENOM', 'Name')
  const Date = pickField(record, 'Date', 'WorkDate')
  const ARRIVEE = pickField(record, 'ARRIVEE', 'ArriveTime')
  const DEPAR = pickField(record, 'DEPAR', 'DepartTime')
  const PAUSE = pickField(record, 'PAUSE')
  const Pays = pickField(record, 'Pays')
  const paysDisplayText = formatPaysFieldDisplay(Pays)
  const Entrepot = pickField(record, 'Entrepot', 'Entrepôt')
  const HORAIRES_DU_TRAVAIL = pickField(record, 'HORAIRES_DU_TRAVAIL')
  const AGENCE_INTERIMAIRE = pickField(record, 'AGENCE_INTERIMAIRE')
  const rawSignature = pickField(record, 'SIGNATURE', 'CHECKER')
  const Observations = pickField(record, 'Observations')
  const PAGE_NUM = pickField(record, 'PAGE_NUM', 'PageNum', 'pageNum', '页码')
  const SmartMark = record.SmartMark || ''
  const SIGNATURE = computeSignatureMark({ ...record, SmartMark, SIGNATURE_RAW: record.SIGNATURE_RAW, SIGNATURE: rawSignature })
  const effectiveSmartMark = getEffectiveSmartMark({ ...record, SmartMark })
  const workHours = computeWorkHours(record)
  const anomalyGroups = collectAnomalyGroups(record)
  const anomalyGroupPreview = buildGroupBadges(anomalyGroups)
  const anomalySummaryText = anomalyGroups.map((group) => group.summary).join('；')
  const anomalySummaryLong = anomalySummaryText.length > 42
  const anomalyReasonPreview = anomalyGroupPreview
  const anomalyReasonMore = 0
  const displayName = displayFieldValue(cleanPersonName(NOM_PRENOM))
  const displayNo = displayFieldValue(cleanWorkerNo(NO))
  const dateText = displayFieldValue(Date)
  const timeRangeText = buildTimeRange(
    isPlaceholderValue(ARRIVEE) ? '' : ARRIVEE,
    isPlaceholderValue(DEPAR) ? '' : DEPAR
  )
  const normalizedPause = normalizePauseMinutes(PAUSE)
  const pauseText = formatPauseText(normalizedPause)
  const workHoursText = workHours || '-'
  const smartMarkDisplay = getSmartMarkDisplay({ ...record, SmartMark: effectiveSmartMark, HORAIRES_DU_TRAVAIL })
  const signatureDisplayText = translateSignatureMark(getDisplaySignature(SIGNATURE, { ...record, SmartMark }), t)

  const display = {
    ...record,
    index,
    NO,
    NOM_PRENOM,
    Date,
    ARRIVEE,
    DEPAR,
    PAUSE: normalizedPause,
    Pays,
    Entrepot,
    HORAIRES_DU_TRAVAIL,
    AGENCE_INTERIMAIRE,
    SIGNATURE,
    signatureDisplayText,
    Observations,
    PAGE_NUM,
    SmartMark: effectiveSmartMark,
    workHours,
    smartMarkDisplay,
    rowTypeLabel: getRowTypeLabel({ ...record, SmartMark: effectiveSmartMark, isDeleted: record.isDeleted }),
    rowTypeTag: getRowTypeTag({ ...record, SmartMark: effectiveSmartMark, isDeleted: record.isDeleted }),
    markLabels: buildRecordMarkLabels({ ...record, SmartMark: effectiveSmartMark }),
    markTag: getMarkTag(effectiveSmartMark),
    rowClass: getRowClass({ ...record, SmartMark: effectiveSmartMark, isDeleted: record.isDeleted }),
    rowDotClass: getRowDotClass({ ...record, SmartMark: effectiveSmartMark, isDeleted: record.isDeleted }),
    anomalyGroups,
    anomalyGroupPreview,
    anomalySummaryText,
    anomalySummaryLong,
    anomalyReasons: anomalyGroups.map((group) => group.summary),
    anomalyReasonPreview,
    anomalyReasonMore,
    hasAnomaly: anomalyGroups.length > 0,
    hasDuplicate: !!(record._hasDuplicate || (record._duplicatePeers && record._duplicatePeers.length)),
    hasManualCalibration: hasManualCalibration(record),
    calibrationHistory: buildCalibrationHistoryUi(record),
    duplicateMembers: record._duplicateMembers || [],
    isAbsent: isAbsentRow({ ...record, SmartMark: effectiveSmartMark }),
    canEdit: !record.isDeleted && !isAbsentRow({ ...record, SmartMark: effectiveSmartMark }),
    _rowKey: record._rowKey,
    titleText: `${displayName} (${displayNo})`,
    dateText,
    timeRangeText,
    pauseText,
    workHoursText,
    timeLine: `${dateText}  ${timeRangeText}`,
    metricsLine: `${t('result.fieldBreak')}:${pauseText}  ${t('result.fieldWorkHours')}:${workHoursText}`,
    pageNumText: displayFieldValue(PAGE_NUM),
    fieldRows: buildRecordFieldRows(record, {
      PAGE_NUM,
      NO,
      displayNo,
      Date,
      ARRIVEE,
      DEPAR,
      PAUSE,
      Pays,
      paysDisplayText,
      Entrepot,
      HORAIRES_DU_TRAVAIL,
      AGENCE_INTERIMAIRE,
      SIGNATURE,
      Observations,
      dateText,
      pageNumText: displayFieldValue(PAGE_NUM),
      pauseText,
      workHours,
      workHoursText,
      signatureDisplayText
    }),
    subLine: [
      Pays ? `${t('result.fieldCountry')}:${paysDisplayText}` : '',
      Entrepot ? `${t('result.fieldWarehouse')}:${Entrepot}` : '',
      HORAIRES_DU_TRAVAIL ? `${t('result.fieldShift')}:${HORAIRES_DU_TRAVAIL}` : '',
      AGENCE_INTERIMAIRE ? `${t('result.fieldAgency')}:${AGENCE_INTERIMAIRE}` : '',
      SIGNATURE ? `${t('result.fieldSignature')}:${signatureDisplayText}` : '',
      Observations ? `${t('result.fieldObservations')}:${Observations}` : ''
    ].filter(Boolean).join('  ')
  }
  return display
}

/** @param {number} maxCount 最多展示条数（用于分页加载） */
function buildDisplayRecords(records, maxCount) {
  const limit = Math.min(
    typeof maxCount === 'number' ? maxCount : records.length,
    records.length
  )
  return records.slice(0, limit).map((r, i) => enrichRecord(r, i))
}

function calculateRecordStatsForDisplay(records) {
  const stats = calculateRecordStats(records)
  return { total: records.length, ...stats, handwritten: stats.handwriting }
}

function buildAnomalyAlerts(records, limit) {
  const max = typeof limit === 'number' ? limit : 20
  const alerts = []
  for (let index = 0; index < (records || []).length; index += 1) {
    const record = records[index]
    if (record.isDeleted) continue
    const groups = collectAnomalyGroups(record)
    if (!groups.length) continue
    const NO = pickField(record, 'NO')
    const NOM_PRENOM = pickField(record, 'NOM_PRENOM', 'Name')
    const displayName = displayFieldValue(cleanPersonName(NOM_PRENOM)) || '?'
    const displayNo = displayFieldValue(cleanWorkerNo(NO)) || '?'
    alerts.push({
      index,
      title: `${displayName}（${displayNo}）`,
      groups: buildGroupBadges(groups),
    })
    if (alerts.length >= max) break
  }
  return alerts
}

module.exports = {
  pickField,
  isAbsentRow,
  hasRequiredMissing,
  collectAnomalyGroups,
  buildGroupBadges,
  enrichRecord,
  buildDisplayRecords,
  calculateRecordStats: calculateRecordStatsForDisplay,
  buildAnomalyAlerts,
  getMarkTag,
  normalizePauseMinutes
}
