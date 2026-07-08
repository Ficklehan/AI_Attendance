/**
 * 重名检测纯逻辑（无 API / 框架依赖）— PC 与小程序共用
 */

const { resolveCountryCodeFromPays } = require('./taskWorkRegionCore.cjs')
const { normalizeDate } = require('./recognizedDateNormalizer.cjs')

function stripSerialSuffix(name) {
  return String(name || '').trim().replace(/\s\d{2}$/, '').trim()
}

function duplicatePaysKey(record) {
  const raw = String(record?.Pays || record?.pays || '').trim()
  if (!raw) return ''
  const code = resolveCountryCodeFromPays(raw)
  return (code && code !== 'default' ? code : raw).toUpperCase()
}

function duplicateDateKey(record) {
  return normalizeDate(String(record?.Date || record?.date || '').trim())
}

function duplicateGroupKey(record) {
  return [
    duplicatePaysKey(record),
    String(record?.Entrepot || record?.entrepot || '').trim().toUpperCase(),
    duplicateDateKey(record),
    String(record?.AGENCE_INTERIMAIRE || '').trim().toUpperCase(),
    String(record?._baseName || '').trim().toUpperCase(),
  ].join('|')
}

function isEligibleForDuplicate(record, isAbsentRow) {
  if (!record || record.isDeleted || record._duplicateConfirmedUnique) return false
  if (typeof isAbsentRow === 'function' && isAbsentRow(record)) return false
  return !!(String(record?.Date || '').trim() && String(record?._baseName || '').trim())
}

function buildDuplicateMember(record, sourceTaskId) {
  return {
    rowKey: record?._rowKey || `${sourceTaskId}-${record?.NO || ''}-${record?.NOM_PRENOM || ''}`,
    sourceTaskId,
    NO: record?.NO,
    displayName: record?.NOM_PRENOM,
    Pays: record?.Pays,
    Entrepot: record?.Entrepot,
    Date: record?.Date,
    AGENCE_INTERIMAIRE: record?.AGENCE_INTERIMAIRE,
    HORAIRES_DU_TRAVAIL: record?.HORAIRES_DU_TRAVAIL,
    ARRIVEE: record?.ARRIVEE,
    DEPAR: record?.DEPAR,
    PAUSE: record?.PAUSE,
    SIGNATURE: record?.SIGNATURE,
    Observations: record?.Observations,
  }
}

function mergeDuplicateMembers(remoteMembers, localMembers) {
  const remote = remoteMembers || []
  const local = localMembers || []
  const byKey = new Map()
  remote.forEach((member) => {
    if (member && member.rowKey) {
      byKey.set(member.rowKey, { ...member })
    }
  })
  local.forEach((member) => {
    if (!member || !member.rowKey) return
    byKey.set(member.rowKey, { ...(byKey.get(member.rowKey) || {}), ...member })
  })
  return [...byKey.values()]
}

function buildDuplicatePayload(records) {
  return (records || []).map((r) => ({
    _rowKey: r._rowKey,
    NO: r.NO,
    Pays: r.Pays,
    Entrepot: r.Entrepot,
    Date: r.Date,
    NOM_PRENOM: r.NOM_PRENOM,
    AGENCE_INTERIMAIRE: r.AGENCE_INTERIMAIRE,
    HORAIRES_DU_TRAVAIL: r.HORAIRES_DU_TRAVAIL,
    ARRIVEE: r.ARRIVEE,
    DEPAR: r.DEPAR,
    PAUSE: r.PAUSE,
    SIGNATURE: r.SIGNATURE,
    Observations: r.Observations,
    isDeleted: r.isDeleted,
    SmartMark: r.SmartMark,
  }))
}

function ensureRecordRowKeys(records, taskId) {
  ;(records || []).forEach((record, index) => {
    if (!record._rowKey) {
      record._rowKey = `${taskId}-r${index}`
    }
    if (!record._baseName) {
      record._baseName = stripSerialSuffix(record.NOM_PRENOM)
    }
  })
}

function normalizeRecordForDuplicate(record, taskId) {
  const tid = taskId || ''
  if (!record._rowKey) {
    record._rowKey = `${tid}-r${Math.random().toString(36).slice(2, 8)}`
  }
  if (!record._baseName) {
    record._baseName = stripSerialSuffix(record.NOM_PRENOM)
  }
  if (record._nameAutoNumbered && stripSerialSuffix(record.NOM_PRENOM) !== record._baseName) {
    record._baseName = stripSerialSuffix(record.NOM_PRENOM)
  }
}

/**
 * 统计某分组内「历史（远程）撞名」的去重条数。
 * 远程 meta.members 同时包含当前行成员（sourceTaskId === 当前任务）与历史匹配行，
 * 这里只计入 sourceTaskId 与当前任务不同的历史行，并跨本组去重。
 */
function countRemoteMatchesForGroup(members, remoteMetaSnapshot, currentTaskId) {
  const remoteKeys = new Set()
  ;(members || []).forEach((m) => {
    const remoteMeta = remoteMetaSnapshot[m._rowKey]
    const remoteMembers = remoteMeta && Array.isArray(remoteMeta.members) ? remoteMeta.members : []
    remoteMembers.forEach((rm) => {
      if (!rm) return
      const src = rm.sourceTaskId
      if (src && src !== currentTaskId) {
        remoteKeys.add(rm.rowKey || `${src}-${rm.NO || ''}-${rm.displayName || ''}`)
      }
    })
  })
  return remoteKeys.size
}

/**
 * 本任务内同组重名 + 远程已确认任务重名提示，并自动编号。
 */
function applyDuplicateDecorations(records, remoteMetaMap, taskId, isAbsentRow) {
  const remoteMetaSnapshot = { ...(remoteMetaMap || {}) }
  const groups = new Map()
  const tid = taskId || ''

  ;(records || []).forEach((record) => {
    normalizeRecordForDuplicate(record, tid)
    if (!isEligibleForDuplicate(record, isAbsentRow)) {
      // 「重名确认」等不再参与判重的行：去掉姓名后自动编号的流水号，还原基础名。
      if (record._nameAutoNumbered) {
        record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
      }
      record._nameAutoNumbered = false
      return
    }
    const key = duplicateGroupKey(record)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(record)
  })

  const meta = {}
  groups.forEach((members) => {
    const hasLocalDuplicate = members.length > 1
    const hasRemoteHit = members.some((m) => remoteMetaSnapshot[m._rowKey])
    if (!hasLocalDuplicate && !hasRemoteHit) {
      members.forEach((record) => {
        if (record._nameAutoNumbered) {
          record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
        }
        record._nameAutoNumbered = false
      })
      return
    }

    // 历史（远程已确认/处理中）撞名条数：本批次姓名接着历史序号往后编，避免与历史重号。
    const remoteMatchOffset = countRemoteMatchesForGroup(members, remoteMetaSnapshot, tid)
    // 本批次内自身≥2，或与历史撞名（有远程命中）均自动编号；
    // 不依赖 remoteMatchOffset（后端可能未回传 sourceTaskId 等，仅有 peers 亦应编号）。
    const shouldNumber = hasLocalDuplicate || hasRemoteHit

    members.forEach((record, idx) => {
      if (shouldNumber) {
        const serial = String(remoteMatchOffset + idx + 1).padStart(2, '0')
        const targetName = `${record._baseName} ${serial}`.trim()
        if (
          !record._duplicateConfirmedUnique
          && (record._nameAutoNumbered || record.NOM_PRENOM === record._baseName || !record.NOM_PRENOM)
        ) {
          record.NOM_PRENOM = targetName
          record._nameAutoNumbered = true
        }
      } else {
        record._nameAutoNumbered = false
      }

      const localPeers = hasLocalDuplicate
        ? members
          .filter((m) => m._rowKey !== record._rowKey)
          .map((m) => `${m.NO || '?'}-${m.NOM_PRENOM || m._baseName || '?'}`)
        : []
      const remotePeers = Array.isArray(remoteMetaSnapshot[record._rowKey]?.peers)
        ? remoteMetaSnapshot[record._rowKey].peers
        : []
      const peers = [...new Set([...localPeers, ...remotePeers])]
      const remoteMembers = remoteMetaSnapshot[record._rowKey]?.members || []
      const localMembers = members.map((m) => buildDuplicateMember(m, tid))

      if (peers.length > 0 || hasLocalDuplicate) {
        meta[record._rowKey] = {
          peers,
          members: mergeDuplicateMembers(remoteMembers, localMembers),
        }
      }
    })
  })

  const mergedMeta = { ...remoteMetaSnapshot }
  Object.keys(meta).forEach((k) => {
    mergedMeta[k] = { ...(mergedMeta[k] || {}), ...meta[k] }
  })
  return mergedMeta
}

module.exports = {
  stripSerialSuffix,
  duplicatePaysKey,
  duplicateDateKey,
  duplicateGroupKey,
  isEligibleForDuplicate,
  buildDuplicateMember,
  mergeDuplicateMembers,
  buildDuplicatePayload,
  ensureRecordRowKeys,
  normalizeRecordForDuplicate,
  applyDuplicateDecorations,
}
