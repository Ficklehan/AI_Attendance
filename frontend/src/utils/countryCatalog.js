/** 与后端 CountryCatalog / Config.vue 保持一致 */
export const DEFAULT_COUNTRY_FLAG = '🇺🇳'

export const PAYS_COUNTRY_LABELS = {
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

export const COUNTRY_FLAG_FALLBACK = {
  default: DEFAULT_COUNTRY_FLAG,
  CN: '🇨🇳',
  FR: '🇫🇷',
  DE: '🇩🇪',
  US: '🇺🇸',
  PL: '🇵🇱',
  NL: '🇳🇱',
  IT: '🇮🇹',
  ES: '🇪🇸',
  CZ: '🇨🇿',
}

export function resolveCountryFlag(code, flagFromServer) {
  if (!code || code === 'default') return DEFAULT_COUNTRY_FLAG
  return flagFromServer || COUNTRY_FLAG_FALLBACK[code] || '🏳️'
}

const SUPPORTED_CODES = new Set(['default', ...Object.keys(COUNTRY_FLAG_FALLBACK)])

export function normalizeCountryCode(country) {
  if (!country || !String(country).trim()) return 'default'
  const trimmed = String(country).trim()
  return trimmed.toLowerCase() === 'default' ? 'default' : trimmed.toUpperCase()
}

/** 从 Pays 列值（国名或代码）解析国家代码 */
export function resolveCountryCodeFromPays(pays) {
  if (!pays || !String(pays).trim()) return null
  const trimmed = String(pays).trim()
  const upper = trimmed.toUpperCase()
  if (SUPPORTED_CODES.has(upper)) return upper
  for (const [code, label] of Object.entries(PAYS_COUNTRY_LABELS)) {
    if (label.toLowerCase() === trimmed.toLowerCase()) return code
  }
  return null
}

export function resolveRecordCountry(record, taskCountry) {
  const pays = record?.Pays ?? record?.Country ?? record?.PAYS ?? ''
  const fromPays = resolveCountryCodeFromPays(pays)
  if (fromPays && fromPays !== 'default') return fromPays
  if (taskCountry) return normalizeCountryCode(taskCountry)
  return 'default'
}
