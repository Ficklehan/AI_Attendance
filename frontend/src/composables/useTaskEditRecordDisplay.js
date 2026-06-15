import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  translateAnomalyReason,
  buildRecordMarkTags,
  markContains,
  anomalyReasonKind,
  refreshNightShiftInSmartMark,
  getRawSmartMark,
  calculateRecordStats,
} from '@/utils/recognitionLabels'
import { hasRequiredMissing } from '@/utils/requiredRecordFields'

function buildRowCacheKey(record, duplicatePeers) {
  if (!record?._rowKey) return ''
  const anomalies = Array.isArray(record.anomalies) ? record.anomalies.join('|') : ''
  return [
    record._rowKey,
    record.SmartMark,
    record.Mark,
    record.NOM_PRENOM,
    record.NO,
    record.PAUSE,
    record.ARRIVEE,
    record.DEPAR,
    record.HORAIRES_DU_TRAVAIL,
    record.SIGNATURE,
    record.isDeleted ? '1' : '0',
    record._duplicateConfirmedUnique ? '1' : '0',
    anomalies,
    duplicatePeers || '',
  ].join('::')
}

/**
 * TaskEdit 记录行：标记展示、异常原因、行样式（带行级缓存）
 */
export function useTaskEditRecordDisplay(records, getDuplicateMeta, { isAbsentRow, hasManualCalibration }) {
  const { t } = useI18n()
  const rowCache = new Map()

  const getDuplicatePeersKey = (record) => {
    const meta = getDuplicateMeta(record)
    return meta?.peers?.join('、') || ''
  }

  const readCache = (record) => {
    const key = buildRowCacheKey(record, getDuplicatePeersKey(record))
    if (!key) return null
    return rowCache.get(key) || null
  }

  const writeCache = (record, payload) => {
    const key = buildRowCacheKey(record, getDuplicatePeersKey(record))
    if (!key) return payload
    rowCache.set(key, payload)
    if (rowCache.size > 800) {
      const firstKey = rowCache.keys().next().value
      rowCache.delete(firstKey)
    }
    return payload
  }

  const clearRowCache = () => {
    rowCache.clear()
  }

  const statItems = computed(() => [
    { key: 'normal', variant: 'normal', value: stats.value.normal, label: t('home.statsNormal') },
    { key: 'handwriting', variant: 'handwriting', value: stats.value.handwriting, label: t('home.statsHandwriting') },
    { key: 'blurred', variant: 'blurred', value: stats.value.blurred, label: t('home.statsBlurred') },
    { key: 'night', variant: 'night', value: stats.value.night, label: t('home.statsNight') },
    { key: 'absent', variant: 'absent', value: stats.value.absent, label: t('home.statsAbsent') },
    { key: 'deleted', variant: 'deleted', value: stats.value.deleted, label: t('home.statsDeleted') },
  ])

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

  const computeDisplaySmartMark = (record) => {
    let raw = refreshNightShiftInSmartMark(getRawSmartMark(record), record)
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

  const stats = computed(() => calculateRecordStats(records.value, { getDisplayMark: getDisplaySmartMark }))

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

  const computeRecordAnomalyReasons = (record) => {
    if (!record || record.isDeleted) return []
    const mark = getDisplaySmartMark(record)
    const reasons = getEffectiveAnomalies(record).map((r) => translateAnomalyReason(r, t))
    if (markContains(mark, 'blurred')) reasons.push(t('taskEdit.blurredContent'))
    if (markContains(mark, 'handwriting')) reasons.push(t('taskEdit.handwrittenContent'))
    if (markContains(mark, 'absent')) reasons.push(t('taskEdit.absentReason'))
    if (hasRequiredMissing(record)) reasons.push(t('taskEdit.requiredFieldMissingShort'))
    const duplicateMeta = getDuplicateMeta(record)
    if (duplicateMeta?.peers?.length) {
      reasons.push(t('taskEdit.duplicateSuspect', { names: duplicateMeta.peers.join('、') }))
    }
    return [...new Set(reasons)]
  }

  const getRecordAnomalyReasons = (record) => {
    const cached = readCache(record)
    if (cached?.anomalyReasons) return cached.anomalyReasons
    const anomalyReasons = computeRecordAnomalyReasons(record)
    writeCache(record, { ...(readCache(record) || {}), anomalyReasons })
    return anomalyReasons
  }

  const getRowClassName = (record) => {
    const cached = readCache(record)
    if (cached?.rowClassName != null) return cached.rowClassName
    if (!record) return ''
    let rowClassName = ''
    if (record?.isDeleted) rowClassName = 'deleted-row'
    else {
      const mark = getDisplaySmartMark(record)
      if (markContains(mark, 'absent')) rowClassName = 'absent-row'
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
    if (kind === 'absent' || kind === 'missing') return 'red'
    if (kind === 'blurred' || kind === 'duplicate') return 'orange'
    if (kind === 'handwriting') return 'blue'
    if (kind === 'deleted') return 'default'
    return 'default'
  }

  const getAnomalyTagClass = (reason) => {
    if (reason.includes(t('home.statsAbsent'))) return 'tag-red'
    if (reason.includes(t('home.statsBlurred'))) return 'tag-amber'
    if (reason.includes(t('home.statsHandwriting'))) return 'tag-blue'
    return 'tag-default'
  }

  /** 轻量判断：不组装完整原因列表，供收起态计数 */
  const hasRecordAnomaly = (record) => {
    if (!record || record.isDeleted) return false
    if (getEffectiveAnomalies(record).length > 0) return true
    const mark = getDisplaySmartMark(record)
    if (markContains(mark, 'blurred')) return true
    if (markContains(mark, 'handwriting')) return true
    if (markContains(mark, 'absent')) return true
    if (hasRequiredMissing(record)) return true
    const duplicateMeta = getDuplicateMeta(record)
    if (duplicateMeta?.peers?.length) return true
    return false
  }

  const countAnomalyRecords = (list = records.value) => {
    let count = 0
    for (const record of list) {
      if (hasRecordAnomaly(record)) count++
    }
    return count
  }

  /** 展开态按需构建明细，最多 limit 条后提前结束扫描 */
  const buildAnomalyAlertsSlice = (list = records.value, limit = 20) => {
    const alerts = []
    for (const record of list) {
      if (record.isDeleted) continue
      const reasons = getRecordAnomalyReasons(record)
      if (reasons.length === 0) continue
      alerts.push({
        name: `${record.NO || '?'} - ${record.NOM_PRENOM || '?'}`,
        reasons: [...new Set(reasons)],
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
    getRowClassName,
    getMarkColor,
    getRowTypeLabel,
    getRowTypeDotClass,
    getAnomalyTagColor,
    getAnomalyTagClass,
    hasRecordAnomaly,
    countAnomalyRecords,
    buildAnomalyAlertsSlice,
    clearRowCache,
  }
}
