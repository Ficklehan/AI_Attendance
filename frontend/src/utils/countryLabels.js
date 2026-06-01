import i18n from '@/locales'
import { resolveCountryFlag } from './countryCatalog'

export function countryI18nKey(code) {
  const normalized = !code || code === 'default' ? 'default' : code
  return `country.${normalized}`
}

/** 读取当前 locale，使 Pinia getter / computed 随语言切换更新 */
function touchLocale() {
  return i18n.global.locale.value
}

export function translateCountryName(code, fallbackName) {
  touchLocale()
  const key = countryI18nKey(code)
  const text = i18n.global.t(key)
  if (text && text !== key) return text
  return fallbackName || code || ''
}

export function formatCountryLabel(code, flag, fallbackName) {
  const resolvedFlag = resolveCountryFlag(code, flag)
  const name = translateCountryName(code, fallbackName)
  return `${resolvedFlag} ${name}`.trim()
}

export function buildCountrySelectOption(item) {
  if (!item) return { value: 'default', label: translateCountryName('default', '全局默认') }
  const flag = resolveCountryFlag(item.code, item.flag)
  const name = translateCountryName(item.code, item.name)
  return {
    value: item.code,
    label: `${flag} ${name}`.trim(),
  }
}
