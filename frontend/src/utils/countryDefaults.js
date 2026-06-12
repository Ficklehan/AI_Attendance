const PAYS_LABELS = {
  CN: 'China',
  FR: 'France',
  DE: 'Germany',
  US: 'United States',
  PL: 'Poland',
  NL: 'Netherlands',
  IT: 'Italy',
  ES: 'Spain',
  CZ: 'Czech Republic',
}

const LOCAL_NAMES = {
  CN: '中国',
  FR: '法国',
  DE: '德国',
  US: '美国',
  PL: '波兰',
  NL: '荷兰',
  IT: '意大利',
  ES: '西班牙',
  CZ: '捷克',
}

export function defaultPaysLabel(countryCode) {
  if (!countryCode || countryCode === 'default') return ''
  const code = String(countryCode).trim().toUpperCase()
  return PAYS_LABELS[code] || LOCAL_NAMES[code] || code
}

import { isPlaceholderValue, sanitizeFieldValue } from './fieldPlaceholder'

export function isMissingPays(value) {
  return isPlaceholderValue(value)
}

export function isUnrecognizedField(value) {
  return isPlaceholderValue(value)
}

export function sanitizeEntrepot(value) {
  return sanitizeFieldValue(value)
}

export function applyMissingPays(record, workingCountryCode) {
  if (!record) return record
  const withWarehouse = { ...record, Entrepot: sanitizeEntrepot(record.Entrepot) }
  if (!isMissingPays(withWarehouse.Pays)) return withWarehouse
  const defaultPays = defaultPaysLabel(workingCountryCode)
  if (!defaultPays) return withWarehouse
  return { ...withWarehouse, Pays: defaultPays }
}
