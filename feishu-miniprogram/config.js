/**
 * 飞书小程序 — 本地开发配置（公网地址由 config.prod.js 提供，脚本生成）
 *
 * 本地开发者工具：USE_PUBLIC_API = false，后端 localhost:8080
 * 手机 / 公网真机：USE_PUBLIC_API = true，先运行 node scripts/render-deploy-config.mjs
 *
 * 临时覆盖：tt.setStorageSync('apiBaseUrlOverride', 'https://xxx/attendance/api')
 */

const LOCAL_BASE_URL = 'http://localhost:8080/attendance/api'

/**
 * false = localhost（本机开发者工具 + 本地后端）
 * true  = config.prod.js（公网部署，手机飞书）
 */
const USE_PUBLIC_API = true

const STORAGE_KEY = 'apiBaseUrlOverride'

let prodConfig = null
function loadProdConfig() {
  if (prodConfig !== null) {
    return prodConfig
  }
  try {
    prodConfig = require('./config.prod.js')
  } catch (e) {
    prodConfig = false
    console.warn(
      '[config] config.prod.js 不存在。公网/真机请先执行: node scripts/render-deploy-config.mjs'
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
