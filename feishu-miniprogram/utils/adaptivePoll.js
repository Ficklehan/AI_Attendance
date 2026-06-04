/**
 * 小程序轮询退避（无 document.visibility，靠调用方传入 isPaused）。
 */
function pollIntervalMs(attempt, baseMs, isPaused) {
  const capped = Math.min(baseMs * Math.pow(1.25, Math.max(0, attempt - 1)), 8000)
  if (isPaused) {
    return Math.min(Math.round(capped * 2), 12000)
  }
  return Math.round(capped)
}

function startAdaptivePoll(options) {
  const tickFn = options.tick
  const shouldContinue = options.shouldContinue || (() => true)
  const baseMs = options.intervalMs || 3000
  const isPaused = options.isPaused || (() => false)
  let attempt = 0
  let timer = null
  let stopped = false

  const run = () => {
    if (stopped || !shouldContinue()) {
      stopped = true
      if (timer) clearTimeout(timer)
      return
    }
    attempt += 1
    Promise.resolve(tickFn(attempt))
      .finally(() => {
        if (stopped || !shouldContinue()) {
          stopped = true
          return
        }
        timer = setTimeout(run, pollIntervalMs(attempt, baseMs, isPaused()))
      })
  }

  run()
  return () => {
    stopped = true
    if (timer) clearTimeout(timer)
  }
}

module.exports = {
  pollIntervalMs,
  startAdaptivePoll
}
