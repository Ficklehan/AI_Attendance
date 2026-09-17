/** 与后端 ReminderLocaleSupport / default-templates.json 对齐 */

export const SUPPORTED_REMINDER_LOCALES = [
  'zh-CN',
  'en-US',
  'fr-FR',
  'nl-NL',
  'es-ES',
  'it-IT',
  'de-DE',
  'cs-CZ',
  'pl-PL',
]

export const COUNTRY_LOCALE_MAP = {
  default: 'zh-CN',
  CN: 'zh-CN',
  FR: 'fr-FR',
  NL: 'nl-NL',
  IT: 'it-IT',
  ES: 'es-ES',
  DE: 'de-DE',
  US: 'en-US',
  PL: 'pl-PL',
  CZ: 'cs-CZ',
}

export function resolveLocaleForCountry(countryCode) {
  const code = !countryCode || countryCode === 'default'
    ? 'default'
    : String(countryCode).trim().toUpperCase()
  return COUNTRY_LOCALE_MAP[code] || COUNTRY_LOCALE_MAP.default
}

export function localesForScopeCountries(scopeCountries = []) {
  if (!scopeCountries || scopeCountries.length === 0) {
    return [...SUPPORTED_REMINDER_LOCALES]
  }
  const locales = new Set([COUNTRY_LOCALE_MAP.default])
  for (const country of scopeCountries) {
    if (!country || country === 'default') continue
    locales.add(resolveLocaleForCountry(country))
  }
  return SUPPORTED_REMINDER_LOCALES.filter((loc) => locales.has(loc))
}

export function emptyTemplateMap(locales = SUPPORTED_REMINDER_LOCALES) {
  return Object.fromEntries((locales || []).map((loc) => [loc, '']))
}

export function mergeTemplateMaps(base = {}, fallback = {}) {
  const merged = { ...emptyTemplateMap(), ...fallback, ...base }
  Object.keys(merged).forEach((key) => {
    if (merged[key] == null) merged[key] = ''
  })
  return merged
}

export function localeI18nKey(locale) {
  const map = {
    'zh-CN': 'language.zhCN',
    'en-US': 'language.enUS',
    'fr-FR': 'language.frFR',
    'nl-NL': 'language.nlNL',
    'es-ES': 'language.esES',
    'it-IT': 'language.itIT',
    'de-DE': 'language.deDE',
    'cs-CZ': 'language.csCZ',
    'pl-PL': 'language.plPL',
  }
  return map[locale] || locale
}
