const PAYS_COUNTRY_LABELS = {
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

const LOCAL_COUNTRY_NAMES = {
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

const SUPPORTED_CODES = new Set(['default', ...Object.keys(PAYS_COUNTRY_LABELS)])

function normalizeCountryCode(country) {
  if (!country || !String(country).trim()) return 'default'
  const trimmed = String(country).trim()
  return trimmed.toLowerCase() === 'default' ? 'default' : trimmed.toUpperCase()
}

function resolveCountryCodeFromPays(pays) {
  if (!pays || !String(pays).trim()) return null
  const trimmed = String(pays).trim()
  const upper = trimmed.toUpperCase()
  if (SUPPORTED_CODES.has(upper)) return upper
  const keys = Object.keys(PAYS_COUNTRY_LABELS)
  for (let i = 0; i < keys.length; i += 1) {
    const code = keys[i]
    if (PAYS_COUNTRY_LABELS[code].toLowerCase() === trimmed.toLowerCase()) return code
  }
  const localKeys = Object.keys(LOCAL_COUNTRY_NAMES)
  for (let i = 0; i < localKeys.length; i += 1) {
    const code = localKeys[i]
    if (LOCAL_COUNTRY_NAMES[code] === trimmed) return code
  }
  return null
}

function resolveRecordCountry(record, taskCountry) {
  const pays = (record && (record.Pays || record.Country || record.PAYS)) || ''
  const fromPays = resolveCountryCodeFromPays(pays)
  if (fromPays && fromPays !== 'default') return fromPays
  if (taskCountry) return normalizeCountryCode(taskCountry)
  return 'default'
}

module.exports = {
  normalizeCountryCode,
  resolveCountryCodeFromPays,
  resolveRecordCountry,
}
