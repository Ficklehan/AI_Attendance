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

const LEGACY_ALIASES = {
  CHINA: 'CN',
  CHINE: 'CN',
  FRANCE: 'FR',
  FRANCIA: 'FR',
  GERMANY: 'DE',
  DEUTSCHLAND: 'DE',
  USA: 'US',
  'UNITED STATES': 'US',
  UNITEDSTATES: 'US',
  AMERICA: 'US',
  POLAND: 'PL',
  POLSKA: 'PL',
  NETHERLANDS: 'NL',
  NEDERLAND: 'NL',
  HOLLAND: 'NL',
  NL: 'NL',
  ITALY: 'IT',
  ITALIA: 'IT',
  ITALIE: 'IT',
  ITA: 'IT',
  SPAIN: 'ES',
  ESPANA: 'ES',
  ESPAGNE: 'ES',
  CZECH: 'CZ',
  CZECHIA: 'CZ',
  'CZECH REPUBLIC': 'CZ',
  CZECHREPUBLIC: 'CZ',
}

/** 从 Pays 列值（国名、代码或历史别名）解析国家代码 */
export function resolveCountryCodeFromPays(pays) {
  if (!pays || !String(pays).trim()) return null
  const trimmed = String(pays).trim()
  const upper = trimmed.toUpperCase()
  if (SUPPORTED_CODES.has(upper)) return upper
  if (LEGACY_ALIASES[upper]) return LEGACY_ALIASES[upper]
  for (const [code, label] of Object.entries(PAYS_COUNTRY_LABELS)) {
    if (label.toLowerCase() === trimmed.toLowerCase()) return code
  }
  for (const [code, name] of Object.entries(LOCAL_COUNTRY_NAMES)) {
    if (name === trimmed) return code
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
