const TOKEN_KEY = 'attendance_token'
const USER_INFO_KEY = 'userInfo'
const LAST_ACTIVITY_KEY = 'attendance_last_activity'

/** 无操作超过此时长将自动退出（2 小时） */
export const IDLE_TIMEOUT_MS = 2 * 60 * 60 * 1000

function readSessionItem(key) {
  const fromSession = sessionStorage.getItem(key)
  if (fromSession) return fromSession
  const fromLocal = localStorage.getItem(key)
  if (fromLocal) {
    sessionStorage.setItem(key, fromLocal)
    localStorage.removeItem(key)
    return fromLocal
  }
  return null
}

function writeSessionItem(key, value) {
  sessionStorage.setItem(key, value)
  localStorage.removeItem(key)
}

function removeSessionItem(key) {
  sessionStorage.removeItem(key)
  localStorage.removeItem(key)
}

export function getToken() {
  return readSessionItem(TOKEN_KEY)
}

export function setToken(token) {
  writeSessionItem(TOKEN_KEY, token)
  touchActivity()
}

export function removeToken() {
  removeSessionItem(TOKEN_KEY)
  removeSessionItem(LAST_ACTIVITY_KEY)
}

export function getStoredUserInfo() {
  const raw = readSessionItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setStoredUserInfo(userInfo) {
  writeSessionItem(USER_INFO_KEY, JSON.stringify(userInfo))
}

export function removeStoredUserInfo() {
  removeSessionItem(USER_INFO_KEY)
}

export function touchActivity() {
  sessionStorage.setItem(LAST_ACTIVITY_KEY, String(Date.now()))
}

export function isIdleExpired() {
  const raw = sessionStorage.getItem(LAST_ACTIVITY_KEY)
  if (!raw) return false
  return Date.now() - Number(raw) > IDLE_TIMEOUT_MS
}

export function isTokenExpiredLocally(token = getToken()) {
  if (!token) return true
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    if (!payload.exp) return false
    return payload.exp * 1000 <= Date.now()
  } catch {
    return true
  }
}
