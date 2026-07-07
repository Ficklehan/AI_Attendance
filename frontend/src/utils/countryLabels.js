import i18n from '@/locales'
import { COUNTRY_FLAG_FALLBACK, DEFAULT_COUNTRY_FLAG, PAYS_COUNTRY_LABELS, resolveCountryCodeFromPays, resolveCountryFlag } from './countryCatalog'

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

/** 将国家/工作地区代码转为带旗帜的展示名（用于任务详情等只读场景） */
export function resolveCountryDisplayLabel(code, options = []) {
  touchLocale()
  const raw = String(code || '').trim()
  const normalized = !raw || raw.toLowerCase() === 'default' ? 'default' : raw.toUpperCase()
  const found = (options || []).find((item) => item.code === normalized)
  if (found) return formatCountryLabel(found.code, found.flag, found.name)
  if (normalized === 'default') {
    return formatCountryLabel('default', DEFAULT_COUNTRY_FLAG, translateCountryName('default', '全局默认'))
  }
  return formatCountryLabel(normalized, COUNTRY_FLAG_FALLBACK[normalized], normalized)
}

/** 表格 Pays 列展示：代码 + 本地化国名 */
export function formatPaysFieldDisplay(paysValue, options = []) {
  touchLocale()
  const raw = String(paysValue || '').trim()
  if (!raw) return ''
  const code = resolveCountryCodeFromPays(raw)
  if (code && code !== 'default') {
    const label = resolveCountryDisplayLabel(code, options)
    return code === raw.toUpperCase() ? label : `${label} (${code})`
  }
  const upper = raw.toUpperCase()
  if (PAYS_COUNTRY_LABELS[upper]) {
    return resolveCountryDisplayLabel(upper, options)
  }
  return raw
}
