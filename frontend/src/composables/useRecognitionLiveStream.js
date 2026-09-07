import { onBeforeUnmount } from 'vue'
import { startRecognitionLiveClient } from '@/utils/recognitionEventClient'

/**
 * 识别进行中把事件流刷到表格。rAF + 32ms 合并，避免每行一次重绘。
 */
export function useRecognitionLiveStream({ onRecords }) {
  let stopClient = null
  let activeTaskId = null
  let raf = 0
  let timer = 0
  let pending = null

  const flush = () => {
    raf = 0
    timer = 0
    if (!pending) {
      return
    }
    const state = pending
    pending = null
    onRecords(state)
  }

  const schedule = (state) => {
    pending = state
    if (timer || raf) {
      return
    }
    timer = window.setTimeout(() => {
      timer = 0
      raf = window.requestAnimationFrame(flush)
    }, 32)
  }

  const stop = () => {
    activeTaskId = null
    if (stopClient) {
      stopClient()
      stopClient = null
    }
    if (raf) {
      window.cancelAnimationFrame(raf)
      raf = 0
    }
    if (timer) {
      window.clearTimeout(timer)
      timer = 0
    }
    pending = null
  }

  const start = (taskId, options = {}) => {
    if (!taskId) {
      return
    }
    if (!options.force && activeTaskId === taskId && stopClient) {
      return
    }
    stop()
    activeTaskId = taskId
    stopClient = startRecognitionLiveClient({
      taskId,
      onState: schedule,
    })
  }

  onBeforeUnmount(stop)

  return { start, stop }
}
