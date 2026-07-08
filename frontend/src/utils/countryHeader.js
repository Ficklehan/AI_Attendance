import { getToken } from './auth'

const STORAGE_KEY = 'attendance_working_country'
const CONFIGURED_KEY = 'attendance_working_country_configured'

let memoryCache = null

/** 同步读取当前工作国家（供 axios / fetch 拦截器使用，避免循环依赖） */
export function getCachedWorkingCountry() {
  if (memoryCache) {
    return memoryCache
  }
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored) {
    memoryCache = stored
    return stored
  }
  return 'default'
}

export function setCachedWorkingCountry(country) {
  const code = country && String(country).trim() ? String(country).trim() : 'default'
  memoryCache = code
  localStorage.setItem(STORAGE_KEY, code)
}

export function isWorkingCountryConfigured() {
  return localStorage.getItem(CONFIGURED_KEY) === '1'
}

export function markWorkingCountryConfigured() {
  localStorage.setItem(CONFIGURED_KEY, '1')
}

export function clearWorkingCountryConfigured() {
  localStorage.removeItem(CONFIGURED_KEY)
}

export function buildAuthCountryHeaders() {
  const token = getToken()
  const country = getCachedWorkingCountry()
  const headers = { 'X-Country': country }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}
