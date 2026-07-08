import { IDLE_TIMEOUT_MS, isIdleExpired, touchActivity } from '@/utils/auth'

const CHECK_INTERVAL_MS = 60 * 1000

export function startSessionGuard(onExpire) {
  touchActivity()

  const events = ['mousedown', 'keydown', 'scroll', 'touchstart']
  const onActivity = () => touchActivity()
  events.forEach((event) => window.addEventListener(event, onActivity, { passive: true }))

  const timer = window.setInterval(() => {
    if (isIdleExpired()) {
      onExpire('idle')
    }
  }, CHECK_INTERVAL_MS)

  return () => {
    window.clearInterval(timer)
    events.forEach((event) => window.removeEventListener(event, onActivity))
  }
}

export { IDLE_TIMEOUT_MS }
