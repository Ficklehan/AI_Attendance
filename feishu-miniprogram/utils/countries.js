/** 与 PC 端 Config.vue / 后端 CountryCatalog 保持一致 */
const COUNTRIES = [
  { code: 'default', flag: '🇺🇳', name: '全局默认' },
  { code: 'CN', flag: '🇨🇳', name: '中国' },
  { code: 'FR', flag: '🇫🇷', name: '法国' },
  { code: 'DE', flag: '🇩🇪', name: '德国' },
  { code: 'US', flag: '🇺🇸', name: '美国' },
  { code: 'PL', flag: '🇵🇱', name: '波兰' },
  { code: 'NL', flag: '🇳🇱', name: '荷兰' },
  { code: 'IT', flag: '🇮🇹', name: '意大利' },
  { code: 'ES', flag: '🇪🇸', name: '西班牙' },
  { code: 'CZ', flag: '🇨🇿', name: '捷克' }
]

const CODE_ORDER = COUNTRIES.map((c) => c.code)

function findCountry(code) {
  if (!code) return null
  const normalized = code.trim().toUpperCase()
  return COUNTRIES.find((c) => c.code.toUpperCase() === normalized)
    || COUNTRIES.find((c) => c.code === code)
}

/** 将服务端列表与本地元数据合并，并保持 PC 顺序 */
function mergeCountryOptions(serverList) {
  const codesFromServer = (serverList || [])
    .map((item) => (typeof item === 'string' ? item : item.code))
    .filter(Boolean)

  const codes = codesFromServer.length
    ? CODE_ORDER.filter((code) => codesFromServer.some((c) => c.toUpperCase() === code.toUpperCase()))
    : CODE_ORDER

  return codes.map((code) => {
    const local = findCountry(code)
    const remote = (serverList || []).find((item) => {
      const c = typeof item === 'string' ? item : item.code
      return c && c.toUpperCase() === code.toUpperCase()
    })
    if (remote && typeof remote !== 'string') {
      return {
        code: remote.code || code,
        flag: remote.flag || (local && local.flag) || '',
        name: remote.name || (local && local.name) || code
      }
    }
    return {
      code,
      flag: (local && local.flag) || '',
      name: (local && local.name) || code
    }
  })
}

const PAYS_LABELS = {
  CN: 'China',
  FR: 'France',
  DE: 'Germany',
  US: 'United States',
  PL: 'Poland',
  NL: 'Netherlands',
  IT: 'Italy',
  ES: 'Spain',
  CZ: 'Czech Republic'
}

function defaultPaysLabel(code) {
  if (!code || code === 'default') return ''
  const normalized = String(code).trim().toUpperCase()
  return PAYS_LABELS[normalized] || (findCountry(normalized) && findCountry(normalized).name) || normalized
}

function isMissingPays(value) {
  if (value == null) return true
  const trimmed = String(value).trim()
  if (!trimmed || trimmed === '-' || trimmed === '—') return true
  if (trimmed === '???' || trimmed === '??') return true
  const lower = trimmed.toLowerCase()
  return lower === 'illegible' || lower === 'n/a' || lower === 'null'
}

function sanitizeEntrepot(value) {
  if (value == null) return ''
  const trimmed = String(value).trim()
  if (!trimmed || trimmed === '-' || trimmed === '—') return ''
  if (trimmed === '???' || trimmed === '??') return ''
  const lower = trimmed.toLowerCase()
  if (lower === 'illegible' || lower === 'n/a' || lower === 'na' || lower === 'null' || lower === 'none') return ''
  return trimmed
}

function applyWorkingCountryPays(record, workingCountryCode) {
  if (!record) return record
  const withWarehouse = { ...record, Entrepot: sanitizeEntrepot(record.Entrepot) }
  const defaultPays = defaultPaysLabel(workingCountryCode)
  if (!defaultPays) return withWarehouse
  return { ...withWarehouse, Pays: defaultPays }
}

function applyMissingPays(record, workingCountryCode) {
  if (!record) return record
  const withWarehouse = { ...record, Entrepot: sanitizeEntrepot(record.Entrepot) }
  if (!isMissingPays(record.Pays)) {
    return withWarehouse
  }
  const defaultPays = defaultPaysLabel(workingCountryCode)
  if (!defaultPays) return withWarehouse
  return { ...withWarehouse, Pays: defaultPays }
}

module.exports = {
  COUNTRIES,
  CODE_ORDER,
  findCountry,
  mergeCountryOptions,
  defaultPaysLabel,
  isMissingPays,
  sanitizeEntrepot,
  applyWorkingCountryPays,
  applyMissingPays
}
