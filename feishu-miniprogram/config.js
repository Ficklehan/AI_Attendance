/**
 * 飞书小程序 API 配置（开关由 config.runtime.js 自动生成，源：production.yaml）
 * 运维：改 deploy/environments/production.yaml 后执行 ./start.sh apply
 */

const DEFAULT_RUNTIME = {
  RUNTIME_MODE: 'local',
  USE_PUBLIC_API: false,
  LOCAL_BASE_URL: 'http://localhost:8080/clockai/api',
}

let runtime = DEFAULT_RUNTIME
try {
  runtime = { ...DEFAULT_RUNTIME, ...require('./config.runtime.js') }
} catch (e) {
  console.warn('[config] config.runtime.js 不存在，请先执行: ./start.sh apply')
}

const LOCAL_BASE_URL = runtime.LOCAL_BASE_URL || DEFAULT_RUNTIME.LOCAL_BASE_URL
const USE_PUBLIC_API = !!runtime.USE_PUBLIC_API

const STORAGE_KEY = 'apiBaseUrlOverride'

let prodConfig = null

function loadProdConfig() {
  if (prodConfig !== null) {
    return prodConfig
  }
  if (USE_PUBLIC_API && runtime.PUBLIC_BASE_URL) {
    prodConfig = {
      PUBLIC_HOST: runtime.PUBLIC_HOST || '',
      PUBLIC_ORIGIN: runtime.PUBLIC_ORIGIN || '',
      PUBLIC_BASE_URL: runtime.PUBLIC_BASE_URL,
    }
    return prodConfig
  }
  try {
    prodConfig = require('./config.prod.js')
  } catch (e) {
    prodConfig = false
    console.warn(
      '[config] config.prod.js 不存在。请先执行: ./start.sh apply'
    )
  }
  return prodConfig
}

function getPublicBaseUrl() {
  const cfg = loadProdConfig()
  if (cfg && cfg.PUBLIC_BASE_URL) {
    return cfg.PUBLIC_BASE_URL
  }
  return ''
}

function getPublicHost() {
  const cfg = loadProdConfig()
  if (cfg && cfg.PUBLIC_HOST) {
    return cfg.PUBLIC_HOST
  }
  return ''
}

function readStorageOverride() {
  try {
    if (typeof tt !== 'undefined' && tt.getStorageSync) {
      const value = tt.getStorageSync(STORAGE_KEY)
      if (value && typeof value === 'string' && value.trim()) {
        return value.trim().replace(/\/+$/, '')
      }
    }
  } catch (e) {
    // ignore
  }
  return ''
}

function resolveBaseUrl() {
  const override = readStorageOverride()
  if (override) {
    return override
  }
  if (USE_PUBLIC_API) {
    const publicUrl = getPublicBaseUrl()
    if (publicUrl) {
      return publicUrl
    }
    // 真机无法访问 localhost；缺 config.prod.js 时勿静默回退
    console.error(
      '[config] USE_PUBLIC_API=true 但 config.prod.js 无效。请先执行: ./start.sh apply'
    )
    return ''
  }
  return LOCAL_BASE_URL
}

/** 本地开发时清除调试留下的公网 apiBaseUrlOverride（如 uat-guanpei） */
function clearProdApiOverrideIfLocalDev() {
  if (USE_PUBLIC_API) {
    return false
  }
  const override = readStorageOverride()
  if (!override) {
    return false
  }
  const lower = override.toLowerCase()
  if (lower.indexOf('localhost') >= 0 || lower.indexOf('127.0.0.1') >= 0) {
    return false
  }
  try {
    if (typeof tt !== 'undefined' && tt.removeStorageSync) {
      tt.removeStorageSync(STORAGE_KEY)
      console.log('[config] 已清除公网 API 覆盖，使用本地:', LOCAL_BASE_URL)
      return true
    }
  } catch (e) {
    // ignore
  }
  return false
}

module.exports = {
  LOCAL_BASE_URL,
  get PUBLIC_BASE_URL() {
    return getPublicBaseUrl()
  },
  USE_PUBLIC_API,
  get PUBLIC_HOST() {
    return getPublicHost()
  },
  STORAGE_KEY,
  loadProdConfig,
  resolveBaseUrl,
  clearProdApiOverrideIfLocalDev,
  get baseUrl() {
    return resolveBaseUrl()
  }
}
