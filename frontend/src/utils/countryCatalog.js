/** 与后端 CountryCatalog / Config.vue 保持一致 */
export const DEFAULT_COUNTRY_FLAG = '🇺🇳'

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
