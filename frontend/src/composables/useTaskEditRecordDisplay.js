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
import { getMissingRequiredFieldKeys, REQUIRED_FIELD_I18N_KEYS } from '@/utils/requiredRecordFields'
import { FIELD_LABEL_KEYS } from '@/constants/calibratableFields'

const ANOMALY_CATEGORY_ORDER = ['required', 'unreadable', 'duplicate', 'other']

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
    Array.isArray(record._unreadableFields) ? record._unreadableFields.join('|') : '',
    anomalies,
    duplicatePeers || '',
  ].join('::')
}

/**
 * TaskEdit 记录行：标记展示、异常原因、行样式（带行级缓存）
 */
export function useTaskEditRecordDisplay(records, getDuplicateMeta, { isAbsentRow, hasManualCalibration, taskCountry }) {
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

const ANOMALY_CATEGORY_I18N = {
  required: 'taskEdit.anomalyCategoryRequired',
  unreadable: 'taskEdit.anomalyCategoryUnreadable',
  duplicate: 'taskEdit.anomalyCategoryDuplicate',
  other: 'taskEdit.anomalyCategoryOther',
}

const ANOMALY_CATEGORY_FALLBACK = {
  required: '必填缺失',
  unreadable: '看不清',
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

  const computeRecordAnomalyReasons = (record) => getRecordAnomalyGroups(record).map((group) => group.summary)

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
    if (kind === 'missing' || /^必填/.test(reason)) return 'red'
    if (kind === 'duplicate' || /^重名/.test(reason)) return 'orange'
    if (/看不清/.test(reason) || kind === 'blurred') return 'gold'
    if (kind === 'deleted') return 'default'
    return 'default'
  }

  const getAnomalyCategoryColor = (category) => {
    if (category === 'required') return 'red'
    if (category === 'unreadable') return 'gold'
    if (category === 'duplicate') return 'orange'
    return 'default'
  }

  const getAnomalyTagClass = (reason) => {
    if (reason.includes(t('home.statsAbsent'))) return 'tag-red'
    if (reason.includes(t('home.statsBlurred'))) return 'tag-amber'
    if (reason.includes(t('home.statsHandwriting'))) return 'tag-blue'
    return 'tag-default'
  }

  const hasRecordAnomaly = (record) => computeRecordAnomalyGroups(record).length > 0

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
      const groups = getRecordAnomalyGroups(record)
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
    getRecordAnomalyGroups,
    getRowClassName,
    getMarkColor,
    getRowTypeLabel,
    getRowTypeDotClass,
    getAnomalyTagColor,
    getAnomalyCategoryColor,
    getAnomalyTagClass,
    hasRecordAnomaly,
    countAnomalyRecords,
    buildAnomalyAlertsSlice,
    clearRowCache,
  }
}
