/** 与 PC 端 Home.vue / TaskEdit.vue 一致的记录展示逻辑 */

const { displayFieldValue, isPlaceholderValue } = require('./fieldPlaceholder')
const { t } = require('./i18n')
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
  translateSignatureMark
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

function cleanPersonName(name) {
  return stripDisplayNoise(name)
}

function cleanWorkerNo(no) {
  const raw = stripDisplayNoise(no)
  if (!raw) return ''
  const normalized = raw.replace(/[()（）]/g, '').trim()
  return normalized || raw
}

function buildTimeRange(arrive, depart) {
  if (!arrive && !depart) return '--:--'
  return `${arrive || '--:--'}-${depart || '--:--'}`
}

function formatPauseText(pause) {
  const minutes = normalizePauseMinutes(pause)
  return minutes === '' ? '-' : `${minutes} min`
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
  const sourceMarks = [
    record && record.SmartMark,
    record && record.Mark,
    record && record.mark,
    record && record.smartMark
  ].map((v) => String(v || '').trim()).filter(Boolean)
  const raw = stripSignatureMarksFromSmartMark(
    [...new Set(sourceMarks.join(';').split(/[;；,，]/).map((v) => v.trim()).filter(Boolean))].join(';')
  )
  const hasHandwritten = hasHandwrittenIdentity(record) || markContains(raw, 'handwriting')
  if (!hasHandwritten || markContains(raw, 'deleted') || markContains(raw, 'absent')) {
    return raw
  }
  if (!raw || raw === '-' || markContains(raw, 'normal')) return '手写'
  if (markContains(raw, 'handwriting')) return raw
  return `${raw};手写`
}

function isAbsentRow(record) {
  const mark = getEffectiveSmartMark(record)
  if (markContains(mark, 'absent')) return true
  const arrive = pickField(record, 'ARRIVEE', 'ArriveTime')
  const depart = pickField(record, 'DEPAR', 'DepartTime')
  return !arrive && !depart
}

const { hasRequiredMissing } = require('./requiredRecordFields')

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

  function cell(key, labelKey, raw, display, required) {
    let value = '-'
    if (display !== undefined && display !== null && String(display).trim() !== '' && !isEmptyFieldValue(display)) {
      value = String(display).trim()
    } else if (!isEmptyFieldValue(raw)) {
      value = String(raw).trim()
    }
    const missing = !skipRequired && !!required && isEmptyFieldValue(raw)
    return { key, label: t(labelKey), value, missing }
  }

  const primaryRow = [
    cell('PAGE_NUM', 'result.fieldPageNumber', ctx.PAGE_NUM, ctx.pageNumText, false),
    cell('NO', 'result.fieldWorkerNo', ctx.NO, ctx.displayNo, false),
    cell('Date', 'result.fieldDate', ctx.Date, ctx.dateText, true),
    cell('ARRIVEE', 'result.fieldArrival', ctx.ARRIVEE, ctx.ARRIVEE, true),
    cell('DEPAR', 'result.fieldDeparture', ctx.DEPAR, ctx.DEPAR, true),
    cell('PAUSE', 'result.fieldBreak', ctx.PAUSE, ctx.pauseText, true),
    cell('workHours', 'result.fieldWorkHours', ctx.workHours, ctx.workHoursText, false)
  ]

  const contextRow = [
    cell('Pays', 'result.fieldCountry', ctx.Pays, null, false),
    cell('Entrepot', 'result.fieldWarehouse', ctx.Entrepot, null, false),
    cell('HORAIRES_DU_TRAVAIL', 'result.fieldShift', ctx.HORAIRES_DU_TRAVAIL, null, false),
    cell('AGENCE_INTERIMAIRE', 'result.fieldAgency', ctx.AGENCE_INTERIMAIRE, null, false),
    cell('SIGNATURE', 'result.fieldSignature', ctx.SIGNATURE, ctx.signatureDisplayText, false),
    cell('Observations', 'result.fieldObservations', ctx.Observations, null, false)
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
  const anomalyReasons = collectAnomalyReasons(record)
  const anomalyReasonPreview = buildReasonBadges(anomalyReasons, 2)
  const anomalyReasonMore = Math.max(0, anomalyReasons.length - anomalyReasonPreview.length)
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
    anomalyReasons,
    anomalyReasonPreview,
    anomalyReasonMore,
    hasAnomaly: anomalyReasons.length > 0,
    hasDuplicate: !!(record._hasDuplicate || (record._duplicatePeers && record._duplicatePeers.length)),
    hasManualCalibration: hasManualCalibration(record),
    calibrationHistory: buildCalibrationHistoryUi(record),
    duplicateMembers: record._duplicateMembers || [],
    isAbsent: isAbsentRow({ ...record, SmartMark: effectiveSmartMark }),
    canEdit: !record.isDeleted && !isAbsentRow({ ...record, SmartMark: effectiveSmartMark }),
    _rowKey: record._rowKey,
    titleText: `${displayName}（${displayNo}）`,
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
      Entrepot,
      HORAIRES_DU_TRAVAIL,
      AGENCE_INTERIMAIRE,
      SIGNATURE,
      Observations,
      dateText,
      pageNumText: displayFieldValue(PAGE_NUM),
      pauseText,
      workHours,
      workHoursText
    }),
    subLine: [
      Pays ? `${t('result.fieldCountry')}:${Pays}` : '',
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

function buildAnomalyAlerts(records) {
  return records
    .map((record, index) => {
      if (record.isDeleted) return null
      const reasons = collectAnomalyReasons(record)
      if (!reasons.length) return null
      const NO = pickField(record, 'NO')
      const NOM_PRENOM = pickField(record, 'NOM_PRENOM', 'Name')
      return {
        index,
        title: `${NO || '?'} - ${NOM_PRENOM || '?'}`,
        reasons
      }
    })
    .filter(Boolean)
}

module.exports = {
  pickField,
  isAbsentRow,
  hasRequiredMissing,
  enrichRecord,
  buildDisplayRecords,
  calculateRecordStats: calculateRecordStatsForDisplay,
  buildAnomalyAlerts,
  getMarkTag,
  normalizePauseMinutes
}
