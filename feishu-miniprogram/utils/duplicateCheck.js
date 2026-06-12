const { taskApi } = require('./api')
const { isApiSuccess, getApiData } = require('./response')
const { isAbsentRow } = require('./recordDisplay')
const core = require('../shared-js/duplicateCheckCore')

const {
  stripSerialSuffix,
  duplicateGroupKey,
  isEligibleForDuplicate: coreIsEligible,
  buildDuplicatePayload,
  ensureRecordRowKeys,
} = core

function isEligibleForDuplicate(record) {
  return coreIsEligible(record, isAbsentRow)
}

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

/**
 * 与 PC TaskEdit 一致：远程重名 + 本任务内同组重名自动编号。
 */
function applyDuplicateDecorations(records, remoteMetaMap, taskId) {
  const remoteMetaSnapshot = { ...(remoteMetaMap || {}) }
  const groups = new Map()

  ;(records || []).forEach((record) => {
    if (!record._rowKey) {
      record._rowKey = `${taskId}-r${Math.random().toString(36).slice(2, 6)}`
    }
    if (!record._baseName) {
      record._baseName = stripSerialSuffix(record.NOM_PRENOM)
    }
    if (record._nameAutoNumbered && stripSerialSuffix(record.NOM_PRENOM) !== record._baseName) {
      record._baseName = stripSerialSuffix(record.NOM_PRENOM)
    }
    record._duplicatePeers = []
    record._hasDuplicate = false
    record._duplicateMembers = []

    if (!isEligibleForDuplicate(record)) {
      if (record._nameAutoNumbered || record._duplicateConfirmedUnique) {
        record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
      }
      record._nameAutoNumbered = false
      return
    }

    const remoteHit = remoteMetaSnapshot[record._rowKey]
    if (!remoteHit) {
      record._nameAutoNumbered = false
      return
    }

    const key = duplicateGroupKey(record)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(record)
  })

  const mergedMeta = { ...remoteMetaSnapshot }

  groups.forEach((members) => {
    members.forEach((record, idx) => {
      const serial = String(idx + 1).padStart(2, '0')
      const targetName = `${record._baseName} ${serial}`.trim()
      if (!record._duplicateConfirmedUnique
        && (record._nameAutoNumbered || record.NOM_PRENOM === record._baseName || !record.NOM_PRENOM)) {
        record.NOM_PRENOM = targetName
        record._nameAutoNumbered = true
      }

      const localPeers = members
        .filter((m) => m._rowKey !== record._rowKey)
        .map((m) => `${m.NO || '?'}-${m.NOM_PRENOM || m._baseName || '?'}`)
      const remotePeers = Array.isArray(remoteMetaSnapshot[record._rowKey]?.peers)
        ? remoteMetaSnapshot[record._rowKey].peers
        : []
      const peers = [...new Set([...localPeers, ...remotePeers])]

      record._duplicatePeers = peers
      record._hasDuplicate = peers.length > 0 || (remoteMetaSnapshot[record._rowKey]?.members || []).length > 1
      record._duplicateMembers = remoteMetaSnapshot[record._rowKey]?.members
        || members.map((m) => ({
          rowKey: m._rowKey,
          sourceTaskId: taskId,
          NO: m.NO,
          displayName: m.NOM_PRENOM,
          Pays: m.Pays,
          Entrepot: m.Entrepot,
          Date: m.Date,
          AGENCE_INTERIMAIRE: m.AGENCE_INTERIMAIRE
        }))

      mergedMeta[record._rowKey] = {
        ...(mergedMeta[record._rowKey] || {}),
        peers,
        members: record._duplicateMembers
      }
    })
  })

  const duplicateRowKeys = Object.keys(mergedMeta).filter((key) => {
    const meta = mergedMeta[key]
    return meta && ((meta.peers && meta.peers.length) || (meta.members && meta.members.length > 1))
  })

  return {
    duplicateMetaMap: mergedMeta,
    duplicateRowKeys,
    duplicateCount: duplicateRowKeys.length
  }
}

function fetchAndApplyDuplicates(taskId, records, scope) {
  ensureRecordRowKeys(records, taskId)
  const payload = buildDuplicatePayload(records)
  if (!payload.length) {
    return Promise.resolve({
      duplicateMetaMap: {},
      duplicateRowKeys: [],
      duplicateCount: 0
    })
  }

  return taskApi.checkDuplicateNames(taskId, payload, scope || 'confirmed_only')
    .then((res) => {
      if (!res || !isApiSuccess(res)) {
        return { duplicateMetaMap: {}, duplicateRowKeys: [], duplicateCount: 0 }
      }
      const data = getApiData(res) || {}
      const remoteMap = buildRemoteMetaFromApi(data.duplicates || [], payload, taskId)
      return applyDuplicateDecorations(records, remoteMap, taskId)
    })
    .catch((err) => {
      console.warn('duplicate check failed', err)
      return { duplicateMetaMap: {}, duplicateRowKeys: [], duplicateCount: 0 }
    })
}

function confirmRecordNotDuplicate(record) {
  if (!record) return
  record._duplicateConfirmedUnique = true
  record._nameAutoNumbered = false
  record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
  record._hasDuplicate = false
  record._duplicatePeers = []
}

module.exports = {
  stripSerialSuffix,
  ensureRecordRowKeys,
  fetchAndApplyDuplicates,
  applyDuplicateDecorations,
  confirmRecordNotDuplicate
}
