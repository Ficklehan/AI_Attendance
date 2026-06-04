/**
 * 自适应轮询：指数退避，页面不可见时放慢间隔。
 * @param {() => boolean | Promise<boolean>} shouldContinue 返回 false 时停止
 * @param {() => void | Promise<void>} tick 每次轮询执行
 * @param {{ intervalMs?: number, maxIntervalMs?: number }} options
 */
export function startAdaptivePoll(shouldContinue, tick, options = {}) {
  const baseMs = options.intervalMs ?? 3000
  const maxMs = options.maxIntervalMs ?? 10000
  const hiddenMultiplier = options.hiddenMultiplier ?? 2
  let attempt = 0
  let timer = null
  let stopped = false

  const nextDelay = () => {
    const scaled = Math.min(baseMs * Math.pow(1.2, Math.max(0, attempt - 1)), maxMs)
    if (typeof document !== 'undefined' && document.hidden) {
      return Math.min(Math.round(scaled * hiddenMultiplier), maxMs * hiddenMultiplier)
    }
    return Math.round(scaled)
  }

  const run = async () => {
    if (stopped) return
    attempt += 1
    try {
      const cont = await shouldContinue()
      if (!cont || stopped) {
        stop()
        return
      }
      await tick()
    } catch (_) {
      /* 单次失败不终止，由 shouldContinue 决定 */
    }
    if (!stopped) {
      timer = window.setTimeout(run, nextDelay())
    }
  }

  const stop = () => {
    stopped = true
    if (timer != null) {
      clearTimeout(timer)
      timer = null
    }
  }

  run()
  return stop
}
