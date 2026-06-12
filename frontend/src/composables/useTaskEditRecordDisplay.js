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

/**
 * TaskEdit 记录行：标记展示、异常原因、行样式
 */
export function useTaskEditRecordDisplay(records, getDuplicateMeta, { isAbsentRow, hasManualCalibration }) {
  const { t } = useI18n()

  const statItems = computed(() => [
    { key: 'normal', variant: 'normal', value: stats.value.normal, label: t('home.statsNormal') },
    { key: 'handwriting', variant: 'handwriting', value: stats.value.handwriting, label: t('home.statsHandwriting') },
    { key: 'blurred', variant: 'blurred', value: stats.value.blurred, label: t('home.statsBlurred') },
    { key: 'night', variant: 'night', value: stats.value.night, label: t('home.statsNight') },
    { key: 'absent', variant: 'absent', value: stats.value.absent, label: t('home.statsAbsent') },
    { key: 'deleted', variant: 'deleted', value: stats.value.deleted, label: t('home.statsDeleted') },
  ])

  const getRecordMarkTags = (record) =>
    buildRecordMarkTags(record, {
      getDisplayMark: getDisplaySmartMark,
      isAbsentRow,
      t,
      hasManualCalibration,
    })

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

  const getDisplaySmartMark = (record) => {
    let raw = refreshNightShiftInSmartMark(getRawSmartMark(record), record)
    const hasHandwritten = hasHandwrittenIdentity(record) || raw.includes('手写')
    if (!hasHandwritten || raw.includes('已删除') || raw.includes('未出勤')) {
      return raw || '-'
    }
    if (!raw || raw === '-' || raw === '正常') return '手写'
    if (raw.includes('手写')) return raw
    return `${raw};手写`
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

  const getRecordAnomalyReasons = (record) => {
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

  const getRowClassName = (record) => {
    if (!record) return ''
    if (record?.isDeleted) return 'deleted-row'
    const mark = getDisplaySmartMark(record)
    if (markContains(mark, 'absent')) return 'absent-row'
    if (markContains(mark, 'blurred')) return 'blurred-row'
    return ''
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

  const anomalyAlerts = computed(() =>
    records.value
      .map((record) => {
        if (record.isDeleted) return null
        const reasons = getRecordAnomalyReasons(record)
        if (reasons.length === 0) return null
        return {
          name: `${record.NO || '?'} - ${record.NOM_PRENOM || '?'}`,
          reasons: [...new Set(reasons)],
        }
      })
      .filter(Boolean),
  )

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
    anomalyAlerts,
  }
}
