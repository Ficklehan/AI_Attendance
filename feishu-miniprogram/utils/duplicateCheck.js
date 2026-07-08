const { taskApi } = require('./api')
const { isApiSuccess, getApiData } = require('./response')
const { isAbsentRow } = require('./recordDisplay')
const core = require('../shared-js/duplicateCheckCore')

const {
  stripSerialSuffix,
  buildDuplicatePayload,
  ensureRecordRowKeys,
  applyDuplicateDecorations,
} = core

function buildRemoteMetaFromApi(duplicates, payload, taskId) {
  const map = {}
  ;(duplicates || []).forEach((d) => {
    if (!d || !d.rowKey) return
    const current = payload.find((p) => p._rowKey === d.rowKey) || {}
    const peers = (d.matches || []).map((m) => `${m.NO || '?'}-${m.NOM_PRENOM || '?'}`)
    map[d.rowKey] = {
      peers,
      baseName: d.baseName || stripSerialSuffix(current.NOM_PRENOM),
      members: [
        {
          rowKey: d.rowKey,
          sourceTaskId: taskId,
          NO: current.NO,
          displayName: current.NOM_PRENOM,
          Pays: current.Pays,
          Entrepot: current.Entrepot,
          Date: current.Date,
          AGENCE_INTERIMAIRE: current.AGENCE_INTERIMAIRE,
          HORAIRES_DU_TRAVAIL: current.HORAIRES_DU_TRAVAIL,
          ARRIVEE: current.ARRIVEE,
          DEPAR: current.DEPAR,
          PAUSE: current.PAUSE,
          SIGNATURE: current.SIGNATURE,
          Observations: current.Observations
        },
        ...(d.matches || []).map((m) => ({
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
          Observations: m.Observations
        }))
      ]
    }
  })
  return map
}

function isMeaningfulDuplicateMeta(meta) {
  if (!meta) return false
  const peers = Array.isArray(meta.peers) ? meta.peers : []
  const members = Array.isArray(meta.members) ? meta.members : []
  return peers.length > 0 || members.length > 1
}

/**
 * 把重名 meta 映射写回每行 record，供 buildDisplayRecords 渲染「重名」标签与明细。
 * shared 核心只返回 meta 映射（PC 端按 rowKey 读取），小程序显示层读取 record 字段，
 * 因此这里做一次桥接写回。
 */
function applyDuplicateMetaToRecords(records, duplicateMetaMap) {
  const map = duplicateMetaMap || {}
  ;(records || []).forEach((record) => {
    if (!record) return
    const meta = map[record._rowKey]
    if (isMeaningfulDuplicateMeta(meta)) {
      record._hasDuplicate = true
      record._duplicatePeers = Array.isArray(meta.peers) ? meta.peers : []
      record._duplicateMembers = Array.isArray(meta.members) ? meta.members : []
    } else if (!record._duplicateConfirmedUnique) {
      record._hasDuplicate = false
      record._duplicatePeers = []
      record._duplicateMembers = []
    }
  })
}

function fetchAndApplyDuplicates(taskId, records, scope) {
  ensureRecordRowKeys(records, taskId)
  const payload = buildDuplicatePayload(records)
  if (!payload.length) {
    const duplicateMetaMap = applyDuplicateDecorations(records, {}, taskId, isAbsentRow)
    applyDuplicateMetaToRecords(records, duplicateMetaMap)
    return Promise.resolve({
      duplicateMetaMap,
      duplicateRowKeys: [],
      duplicateCount: 0
    })
  }

  return taskApi.checkDuplicateNames(taskId, payload, scope || 'confirmed_only')
    .then((res) => {
      if (!res || !isApiSuccess(res)) {
        const duplicateMetaMap = applyDuplicateDecorations(records, {}, taskId, isAbsentRow)
        return buildDuplicateResult(records, duplicateMetaMap)
      }
      const data = getApiData(res) || {}
      const remoteMap = buildRemoteMetaFromApi(data.duplicates || [], payload, taskId)
      const duplicateMetaMap = applyDuplicateDecorations(records, remoteMap, taskId, isAbsentRow)
      return buildDuplicateResult(records, duplicateMetaMap)
    })
    .catch((err) => {
      console.warn('duplicate check failed', err)
      const duplicateMetaMap = applyDuplicateDecorations(records, {}, taskId, isAbsentRow)
      return buildDuplicateResult(records, duplicateMetaMap)
    })
}

function buildDuplicateResult(records, duplicateMetaMap) {
  applyDuplicateMetaToRecords(records, duplicateMetaMap)
  const duplicateRowKeys = Object.keys(duplicateMetaMap || {}).filter((key) =>
    isMeaningfulDuplicateMeta(duplicateMetaMap[key])
  )
  return {
    duplicateMetaMap,
    duplicateRowKeys,
    duplicateCount: duplicateRowKeys.length
  }
}

function confirmRecordNotDuplicate(record) {
  if (!record) return
  record._duplicateConfirmedUnique = true
  // 「重名确认」：确认为重名后取消姓名后的流水号，还原基础名。
  record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
  record._nameAutoNumbered = false
  record._hasDuplicate = false
  record._duplicatePeers = []
}

module.exports = {
  stripSerialSuffix,
  ensureRecordRowKeys,
  fetchAndApplyDuplicates,
  confirmRecordNotDuplicate
}
