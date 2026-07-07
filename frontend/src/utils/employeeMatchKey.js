/** 员工发号比对键（与 shared/js/employeeMatchCore.cjs 对齐） */

export function stripSerialSuffix(name) {
  return String(name || '').trim().replace(/\s\d{2}$/, '').trim()
}

export function normalizeRegionCode(regionCode) {
  const trimmed = String(regionCode || '').trim()
  if (!trimmed || trimmed.toLowerCase() === 'default') {
    return 'DEFAULT'
  }
  return trimmed.toUpperCase()
}

export function normalizeAgencyKey(record) {
  return String(record?.AGENCE_INTERIMAIRE || record?.AGENCE || record?.Agency || '')
    .trim()
    .toUpperCase()
}

export function resolveMatchName(record) {
  const name = String(record?.NOM_PRENOM || record?.NOM || record?.NAME || '').trim()
  if (!name) return ''
  if (record?._duplicateConfirmedUnique) {
    return stripSerialSuffix(name)
  }
  return name
}

export function buildEmployeeMatchKey(record, regionCode) {
  return [
    normalizeRegionCode(regionCode),
    normalizeAgencyKey(record),
    resolveMatchName(record),
  ].join('|')
}
