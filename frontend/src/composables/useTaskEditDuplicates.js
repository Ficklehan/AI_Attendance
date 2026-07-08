import { ref, onScopeDispose } from 'vue'
import { checkTaskDuplicateNames } from '@/api/task'
import {
  stripSerialSuffix,
  isEligibleForDuplicate,
  buildDuplicateMember,
  mergeDuplicateMembers,
  buildDuplicatePayload,
  applyDuplicateDecorations,
} from '@/utils/duplicateCheck'

/**
 * TaskEdit 重名检测：远程提示、本任务内同组重名、自动编号、展开详情
 */
export function useTaskEditDuplicates(taskId, records) {
  const expandedDuplicateRowKeys = ref([])
  const duplicateMetaMap = ref({})
  const duplicateRefreshing = ref(false)
  const duplicateScope = ref('confirmed_only')
  let duplicateFetchTimer = null

  const getDuplicateMeta = (record) => {
    const meta = duplicateMetaMap.value[record?._rowKey]
    if (!meta) return null
    const peers = Array.isArray(meta.peers) ? meta.peers : []
    const members = Array.isArray(meta.members) ? meta.members : []
    if (peers.length > 0 || members.length > 1) return meta
    return null
  }

  const refreshDuplicateDecorations = () => {
    if (duplicateRefreshing.value) return
    duplicateRefreshing.value = true
    try {
      const mergedMeta = applyDuplicateDecorations(
        records.value,
        duplicateMetaMap.value,
        taskId.value,
      )
      duplicateMetaMap.value = mergedMeta
      // applyDuplicateDecorations 会就地改写 record.NOM_PRENOM（重名自动编号/还原）。
      // a-table 依据 data-source 数组引用做行渲染缓存，仅改属性不换数组引用时姓名列不会刷新，
      // 因此这里替换数组引用（保留原行对象），确保编号后的流水号能显示到姓名后。
      records.value = records.value.slice()
      expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter((key) => !!getDuplicateMeta({ _rowKey: key }))
    } finally {
      duplicateRefreshing.value = false
    }
  }

  const buildRemoteMetaFromApi = (duplicates) => {
    const map = {}
    ;(duplicates || []).forEach((d) => {
      if (!d?.rowKey) return
      const current = records.value.find((p) => p._rowKey === d.rowKey)
      const peers = (d.matches || []).map((m) => `${m.NO || '?'}-${m.NOM_PRENOM || '?'}`)
      map[d.rowKey] = {
        peers,
        members: mergeDuplicateMembers(
          (d.matches || []).map((m) => ({
            rowKey: `${m.sourceTaskId}-${m.NO}-${m.NOM_PRENOM}`,
            sourceTaskId: m.sourceTaskId,
            NO: m.NO,
            displayName: m.NOM_PRENOM,
            Pays: m.Pays,
            Entrepot: m.Entrepot,
            Date: m.Date,
            AGENCE_INTERIMAIRE: m.AGENCE_INTERIMAIRE,
            HORAIRES_DU_TRAVAIL: m.HORAIRES_DU_TRAVAIL,
            ARRIVEE: m.ARRIVEE,
            DEPAR: m.DEPAR,
            PAUSE: m.PAUSE,
            SIGNATURE: m.SIGNATURE,
            Observations: m.Observations,
          })),
          current ? [buildDuplicateMember(current, taskId.value)] : [],
        ),
      }
    })
    return map
  }

  const fetchConfirmedDuplicateHints = async () => {
    try {
      const payload = buildDuplicatePayload(records.value)
      if (!payload.length) {
        duplicateMetaMap.value = {}
        refreshDuplicateDecorations()
        return
      }
      const res = await checkTaskDuplicateNames(taskId.value, payload, duplicateScope.value)
      duplicateMetaMap.value = buildRemoteMetaFromApi(res?.data?.duplicates || [])
      refreshDuplicateDecorations()
    } catch (error) {
      console.error('加载已确认任务重名提示失败:', error)
      refreshDuplicateDecorations()
    }
  }

  const scheduleDuplicateRecheck = (delayMs = 300) => {
    if (duplicateFetchTimer) window.clearTimeout(duplicateFetchTimer)
    duplicateFetchTimer = window.setTimeout(() => {
      duplicateFetchTimer = null
      fetchConfirmedDuplicateHints()
    }, delayMs)
  }

  onScopeDispose(() => {
    if (duplicateFetchTimer) window.clearTimeout(duplicateFetchTimer)
  })

  const handleDuplicateScopeChange = async () => {
    await fetchConfirmedDuplicateHints()
  }

  const toggleDuplicateExpand = (record) => {
    const key = record?._rowKey
    if (!key || !getDuplicateMeta(record)) return
    if (expandedDuplicateRowKeys.value.includes(key)) {
      expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter((k) => k !== key)
    } else {
      expandedDuplicateRowKeys.value = [...expandedDuplicateRowKeys.value, key]
    }
  }

  const handleTableExpand = (expanded, record) => {
    const key = record?._rowKey
    if (!key) return
    if (expanded) {
      if (!expandedDuplicateRowKeys.value.includes(key)) {
        expandedDuplicateRowKeys.value = [...expandedDuplicateRowKeys.value, key]
      }
    } else {
      expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter((k) => k !== key)
    }
  }

  const confirmNotDuplicate = (record) => {
    record._duplicateConfirmedUnique = true
    // 「重名确认」：确认为重名后取消姓名后的流水号，还原基础名。
    record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
    record._nameAutoNumbered = false
    refreshDuplicateDecorations()
    scheduleDuplicateRecheck()
  }

  const markNameManuallyEdited = (record) => {
    if (!record) return
    record._baseName = stripSerialSuffix(record.NOM_PRENOM)
    if (record._duplicateConfirmedUnique) {
      record._nameAutoNumbered = false
    }
    scheduleDuplicateRecheck()
  }

  return {
    expandedDuplicateRowKeys,
    duplicateRefreshing,
    duplicateScope,
    duplicateMetaMap,
    getDuplicateMeta,
    refreshDuplicateDecorations,
    fetchConfirmedDuplicateHints,
    scheduleDuplicateRecheck,
    handleDuplicateScopeChange,
    toggleDuplicateExpand,
    handleTableExpand,
    confirmNotDuplicate,
    markNameManuallyEdited,
  }
}
