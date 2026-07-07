/**
 * 员工发号比对键（工作地区 + 中介 + match_name）
 */

function stripSerialSuffix(name) {
  return String(name || '').trim().replace(/\s\d{2}$/, '').trim()
}

function normalizeRegionCode(regionCode) {
  const trimmed = String(regionCode || '').trim()
  if (!trimmed || trimmed.toLowerCase() === 'default') {
    return 'DEFAULT'
  }
  return trimmed.toUpperCase()
}

function normalizeAgencyKey(record) {
  return String(record?.AGENCE_INTERIMAIRE || record?.AGENCE || record?.Agency || '')
    .trim()
    .toUpperCase()
}

function resolveMatchName(record) {
  const name = String(record?.NOM_PRENOM || record?.NOM || record?.NAME || '').trim()
  if (!name) return ''
  if (record?._duplicateConfirmedUnique) {
    return stripSerialSuffix(name)
  }
  return name
}

function buildEmployeeMatchKey(record, regionCode) {
  return [
    normalizeRegionCode(regionCode),
    normalizeAgencyKey(record),
    resolveMatchName(record),
  ].join('|')
}

module.exports = {
  stripSerialSuffix,
  normalizeRegionCode,
  normalizeAgencyKey,
  resolveMatchName,
  buildEmployeeMatchKey,
}
