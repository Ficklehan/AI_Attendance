import { API_BASE_PATH } from '@/constants/apiBase'
import { getToken } from '@/utils/auth'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { getRecognitionEvents, waitRecognitionEvents } from '@/api/task'
import {
  applyRecognitionEvents,
  emptyRecognitionState,
  isRecognitionTerminal,
} from '@/utils/recognitionEvents'

function streamHeaders() {
  const headers = { Accept: 'text/event-stream' }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const country = getCachedWorkingCountry()
  if (country) {
    headers['X-Country'] = country
  }
  const locale = localStorage.getItem('locale')
  if (locale) {
    headers['X-Locale'] = locale
  }
  return headers
}

function parseSseBlocks(buffer, onEvent) {
  const parts = buffer.split('\n\n')
  const rest = parts.pop()
  for (const block of parts) {
    let eventName = 'message'
    const dataLines = []
    const lines = block.replace(/\r/g, '').split('\n')
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }
    if (eventName === 'heartbeat' || dataLines.length === 0) {
      continue
    }
    const raw = dataLines.join('\n')
    if (!raw || raw === '{}') {
      continue
    }
    try {
      onEvent(JSON.parse(raw))
    } catch {
      // ignore malformed frames
    }
  }
  return rest
}

async function consumeSse(taskId, afterSeq, onEvent, signal) {
  const url = `${API_BASE_PATH}/tasks/${encodeURIComponent(taskId)}/events/stream?afterSeq=${encodeURIComponent(afterSeq)}`
  const response = await fetch(url, {
    method: 'GET',
    headers: streamHeaders(),
    signal,
  })
  if (!response.ok || !response.body) {
    throw new Error(`SSE ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = parseSseBlocks(buffer, onEvent)
  }
}

function pageEvents(res) {
  return res?.data?.events || res?.events || []
}

function pageStatus(res) {
  return res?.data?.status || res?.status || ''
}

function pageHasMore(res) {
  return !!(res?.data?.hasMore ?? res?.hasMore)
}

export function startRecognitionLiveClient({ taskId, onState }) {
  let state = emptyRecognitionState()
  let stopped = false
  const abort = new AbortController()

  const emit = (batch) => {
    if (!batch || batch.length === 0) {
      onState(state)
      return
    }
    state = applyRecognitionEvents(state, batch)
    onState(state)
  }

  const catchUp = async () => {
    let guard = 0
    while (!stopped && guard < 50) {
      guard += 1
      const res = await getRecognitionEvents(taskId, { afterSeq: state.lastSeq, limit: 100 })
      const events = pageEvents(res)
      emit(events)
      const status = pageStatus(res)
      if (status) {
        state = { ...state, status }
      }
      if (!pageHasMore(res) || events.length === 0) {
        return res
      }
    }
    return null
  }

  const waitLoop = async () => {
    while (!stopped) {
      try {
        const res = await waitRecognitionEvents(taskId, {
          afterSeq: state.lastSeq,
          timeoutMs: 25000,
        })
        if (stopped) {
          return
        }
        emit(pageEvents(res))
        const status = pageStatus(res)
        if (status) {
          state = { ...state, status }
        }
        if (isRecognitionTerminal(status) && !pageHasMore(res)) {
          return
        }
        if (!pageEvents(res).length && !isRecognitionTerminal(status)) {
          await new Promise((resolve) => setTimeout(resolve, 400))
        }
      } catch {
        if (stopped) {
          return
        }
        try {
          const res = await getRecognitionEvents(taskId, { afterSeq: state.lastSeq, limit: 100 })
          emit(pageEvents(res))
          if (isRecognitionTerminal(pageStatus(res)) && !pageHasMore(res)) {
            return
          }
        } catch {
          // keep retrying
        }
        await new Promise((resolve) => setTimeout(resolve, 1000))
      }
    }
  }

  const run = async () => {
    try {
      await catchUp()
      if (stopped || state.complete || isRecognitionTerminal(state.status)) {
        return
      }
      await consumeSse(taskId, state.lastSeq, (event) => {
        if (!stopped) {
          emit([event])
        }
      }, abort.signal)
    } catch {
      // SSE unavailable or dropped — fall through to wait
    }
    if (!stopped && !state.complete && !isRecognitionTerminal(state.status)) {
      await waitLoop()
    }
  }

  run()

  return () => {
    stopped = true
    abort.abort()
  }
}
