const TAG = '[识别跟踪]'

const buffer = []

function preview(value, maxLen) {
  if (value == null) return ''
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length <= maxLen ? text : text.slice(0, maxLen) + '...'
}

function log(phase, detail) {
  const entry = {
    at: new Date().toISOString(),
    phase,
    detail: detail || {}
  }
  buffer.push(entry)
  try {
    console.log(TAG, phase, detail)
  } catch (e) {
    // ignore
  }
}

function clear() {
  buffer.length = 0
}

function dump() {
  return buffer.slice()
}

function formatDump() {
  return buffer
    .map((e) => `${e.at} [${e.phase}] ${preview(e.detail, 400)}`)
    .join('\n')
}

/** 从任务 anomalySummary 解析服务端跟踪 */
function parseServerTrace(task) {
  if (!task || !task.anomalySummary) return null
  try {
    const o = typeof task.anomalySummary === 'string'
      ? JSON.parse(task.anomalySummary)
      : task.anomalySummary
    return o.recognitionTrace || null
  } catch {
    return null
  }
}

function logServerTrace(task) {
  const trace = parseServerTrace(task)
  if (!trace) {
    log('server_trace_missing', { taskId: task && task.taskId, status: task && task.status })
    return null
  }
  log('server_trace', {
    taskId: trace.taskId,
    client: trace.client,
    stepCount: (trace.steps && trace.steps.length) || 0
  })
  const steps = trace.steps || []
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i]
    log(`server:${s.phase}`, s.detail)
  }
  return trace
}

function logTaskParse(task) {
  const raw = task.rawData
  let records = []
  try {
    records = typeof raw === 'string' ? JSON.parse(raw) : (raw || [])
  } catch (e) {
    log('frontend_parse_error', { message: e.message, rawPreview: preview(raw, 300) })
    return records
  }
  log('frontend_parsed_records', {
    count: records.length,
    sample: records.slice(0, 5).map((r) => ({
      NO: r.NO,
      NOM_PRENOM: r.NOM_PRENOM,
      Date: r.Date
    }))
  })
  return records
}

module.exports = {
  TAG,
  log,
  clear,
  dump,
  formatDump,
  preview,
  parseServerTrace,
  logServerTrace,
  logTaskParse
}
