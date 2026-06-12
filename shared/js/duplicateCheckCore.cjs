/**
 * 重名检测纯逻辑（无 API / 框架依赖）— PC 与小程序共用
 */

function stripSerialSuffix(name) {
  return String(name || '').trim().replace(/\s\d{2}$/, '').trim()
}

function duplicateGroupKey(record) {
  return [
    String(record?.Pays || record?.pays || '').trim().toUpperCase(),
    String(record?.Entrepot || record?.entrepot || '').trim().toUpperCase(),
    String(record?.Date || record?.date || '').trim(),
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

module.exports = {
  stripSerialSuffix,
  duplicateGroupKey,
  isEligibleForDuplicate,
  buildDuplicateMember,
  mergeDuplicateMembers,
  buildDuplicatePayload,
  ensureRecordRowKeys,
}
