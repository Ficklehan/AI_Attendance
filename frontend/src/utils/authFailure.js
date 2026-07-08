import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { resetSessionValidation } from '@/utils/sessionState'

const AUTH_ERROR_CODES = new Set([401, 1001, 1004, 1005])

const AUTH_ERROR_KEYS = new Set([
  'errors.userNotFound',
  'errors.userDisabled',
  'errors.tokenInvalid',
  'errors.tokenExpired',
  'errors.loginRequired',
])

export function isAuthFailure(payload) {
  if (!payload) return false
  const code = Number(payload.code ?? payload.apiCode)
  if (AUTH_ERROR_CODES.has(code)) return true
  return AUTH_ERROR_KEYS.has(payload.messageKey)
}

export function forceAuthLogout() {
  const authStore = useAuthStore()
  authStore.logout()
  resetSessionValidation()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}
