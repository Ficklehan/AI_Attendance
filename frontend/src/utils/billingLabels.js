import { resolveCountryDisplayLabel } from '@/utils/countryLabels'
import { normalizeCountryCode, resolveCountryCodeFromPays } from '@/utils/countryCatalog'

function trim(value) {
  return String(value || '').trim()
}

function isMostlyUppercase(value) {
  const letters = String(value || '').replace(/[^A-Za-z]/g, '')
  if (!letters) return false
  const upper = letters.replace(/[^A-Z]/g, '').length
  return upper * 100 / letters.length >= 80
}

/** 将 MANPOWER / job_talent 等键转为可读名称；短仓库代码如 AMS 保持原样 */
export function humanizeBillingKey(key) {
  const raw = trim(key)
  if (!raw) return ''
  if (raw.length <= 4 && raw === raw.toUpperCase()) return raw
  return raw
    .split(/[\s_-]+/)
    .filter(Boolean)
    .map((part) => {
      if (part.includes('&')) {
        return part
          .split('&')
          .map((token) => capitalizeToken(token))
          .join('&')
      }
      return capitalizeToken(part)
    })
    .join(' ')
}

function capitalizeToken(token) {
  if (!token) return ''
  if (token.length <= 4 && token === token.toUpperCase()) return token
  return token.charAt(0).toUpperCase() + token.slice(1).toLowerCase()
}

export function formatBillingCountry(countryLabel, countryKey, options = []) {
  const label = trim(countryLabel)
  const key = trim(countryKey)
  let code = normalizeCountryCode(key)
  if ((!code || code === 'default') && label) {
    code = resolveCountryCodeFromPays(label) || normalizeCountryCode(label)
  }
  if (code && code !== 'default') {
    return resolveCountryDisplayLabel(code, options)
  }
  if (label) return label
  return humanizeBillingKey(key)
}

export function formatBillingText(text, key) {
  const label = trim(text)
  const normalizedKey = trim(key)
  if (label) {
    if (normalizedKey && label.toUpperCase() === normalizedKey.toUpperCase()) {
      return humanizeBillingKey(normalizedKey)
    }
    if (isMostlyUppercase(label) && normalizedKey && label.toUpperCase() === normalizedKey.toUpperCase()) {
      return humanizeBillingKey(normalizedKey)
    }
    return label
  }
  return humanizeBillingKey(normalizedKey)
}

export function formatBillingBlock(block, countryOptions = []) {
  if (!block) return block
  return {
    ...block,
    agencyLabel: formatBillingText(block.agencyLabel, block.agencyKey),
    warehouseLabel: formatBillingText(block.warehouseLabel, block.warehouseKey),
    countryLabel: formatBillingCountry(block.countryLabel, block.countryKey, countryOptions),
  }
}

export function formatBillingDetail(detail, countryOptions = []) {
  if (!detail) return detail
  return {
    ...detail,
    agencyLabel: formatBillingText(detail.agencyLabel, detail.agencyKey),
    warehouseLabel: formatBillingText(detail.warehouseLabel, detail.warehouseKey),
    countryLabel: formatBillingCountry(detail.countryLabel, detail.countryKey, countryOptions),
  }
}
