import { ref } from 'vue'
import { checkTaskDuplicateNames } from '@/api/task'
import {
  stripSerialSuffix,
  duplicateGroupKey,
  isEligibleForDuplicate,
  buildDuplicateMember,
  mergeDuplicateMembers,
  buildDuplicatePayload,
} from '@/utils/duplicateCheck'

/**
 * TaskEdit 重名检测：远程提示、本地编号、展开详情
 */
export function useTaskEditDuplicates(taskId, records) {
  const expandedDuplicateRowKeys = ref([])
  const duplicateMetaMap = ref({})
  const duplicateRefreshing = ref(false)
  const duplicateScope = ref('confirmed_only')

  const getDuplicateMeta = (record) => duplicateMetaMap.value[record?._rowKey]

  const refreshDuplicateDecorations = () => {
    if (duplicateRefreshing.value) return
    duplicateRefreshing.value = true
    try {
      const remoteMetaSnapshot = { ...duplicateMetaMap.value }
      const groups = new Map()
      records.value.forEach((record) => {
        if (!record._rowKey) {
          record._rowKey = `${taskId.value}-${Math.random().toString(36).slice(2, 8)}`
        }
        if (!record._baseName) {
          record._baseName = stripSerialSuffix(record.NOM_PRENOM)
        }
        if (record._nameAutoNumbered && stripSerialSuffix(record.NOM_PRENOM) !== record._baseName) {
          record._baseName = stripSerialSuffix(record.NOM_PRENOM)
        }
        if (!isEligibleForDuplicate(record)) {
          if (record._nameAutoNumbered || record._duplicateConfirmedUnique) {
            record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
          }
          record._nameAutoNumbered = false
          return
        }
        const key = duplicateGroupKey(record)
        const remoteHit = duplicateMetaMap.value[record._rowKey]
        if (!remoteHit) {
          record._nameAutoNumbered = false
          return
        }
        if (!groups.has(key)) groups.set(key, [])
        groups.get(key).push(record)
      })

      const meta = {}
      groups.forEach((members) => {
        members.forEach((record, idx) => {
          const serial = String(idx + 1).padStart(2, '0')
          const targetName = `${record._baseName} ${serial}`.trim()
          if (
            !record._duplicateConfirmedUnique
            && (record._nameAutoNumbered || record.NOM_PRENOM === record._baseName || !record.NOM_PRENOM)
          ) {
            record.NOM_PRENOM = targetName
            record._nameAutoNumbered = true
          }
          const localPeers = members
            .filter((m) => m._rowKey !== record._rowKey)
            .map((m) => `${m.NO || '?'}-${m.NOM_PRENOM || m._baseName || '?'}`)
          const remotePeers = Array.isArray(remoteMetaSnapshot?.[record._rowKey]?.peers)
            ? remoteMetaSnapshot[record._rowKey].peers
            : []
          const peers = [...new Set([...localPeers, ...remotePeers])]
          const remoteMembers = remoteMetaSnapshot[record._rowKey]?.members || []
          const localMembers = members.map((m) => buildDuplicateMember(m, taskId.value))
          meta[record._rowKey] = {
            peers,
            members: mergeDuplicateMembers(remoteMembers, localMembers),
          }
        })
      })
      const mergedMeta = {}
      Object.keys(duplicateMetaMap.value).forEach((k) => {
        mergedMeta[k] = { ...duplicateMetaMap.value[k] }
      })
      Object.keys(meta).forEach((k) => {
        mergedMeta[k] = { ...(mergedMeta[k] || {}), ...meta[k] }
      })
      duplicateMetaMap.value = mergedMeta
      expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter((key) => !!mergedMeta[key])
    } finally {
      duplicateRefreshing.value = false
    }
  }

  const fetchConfirmedDuplicateHints = async () => {
    try {
      const payload = buildDuplicatePayload(records.value)
      const res = await checkTaskDuplicateNames(taskId.value, payload, duplicateScope.value)
      const map = {}
      const duplicates = res?.data?.duplicates || []
      duplicates.forEach((d) => {
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
      duplicateMetaMap.value = map
      refreshDuplicateDecorations()
    } catch (error) {
      console.error('加载已确认任务重名提示失败:', error)
    }
  }

  const handleDuplicateScopeChange = async () => {
    await fetchConfirmedDuplicateHints()
  }

  const toggleDuplicateExpand = (record) => {
    const key = record?._rowKey
    if (!key || !duplicateMetaMap.value[key]) return
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
    record._nameAutoNumbered = false
    record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
    refreshDuplicateDecorations()
  }

  const markNameManuallyEdited = (record) => {
    if (!record) return
    record._baseName = stripSerialSuffix(record.NOM_PRENOM)
    if (record._duplicateConfirmedUnique) {
      record._nameAutoNumbered = false
    }
    refreshDuplicateDecorations()
  }

  return {
    expandedDuplicateRowKeys,
    duplicateRefreshing,
    duplicateScope,
    duplicateMetaMap,
    getDuplicateMeta,
    refreshDuplicateDecorations,
    fetchConfirmedDuplicateHints,
    handleDuplicateScopeChange,
    toggleDuplicateExpand,
    handleTableExpand,
    confirmNotDuplicate,
    markNameManuallyEdited,
  }
}
