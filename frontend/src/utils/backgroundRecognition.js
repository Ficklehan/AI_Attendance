import { getTaskDetail, getTaskProgress } from '@/api/task'
import { uploadImageAsync, startTaskRecognition } from '@/api/upload'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { translateErrorMessage } from '@/utils/translateError'
import i18n from '@/locales'

export const BG_TASK_STORAGE_KEY = 'attendance.bgRecognition.taskId'
const POLL_DEADLINE_MS = 16 * 60 * 1000

function buildUploadFormData(file, { taskId, deferRecognition } = {}) {
  const formData = new FormData()
  formData.append('image', file)
  formData.append('country', getCachedWorkingCountry())
  if (taskId) formData.append('taskId', taskId)
  if (deferRecognition) formData.append('deferRecognition', 'true')
  return formData
}

function pollDelayMs(attempt, processing = false) {
  const baseMs = processing ? 800 : 2000
  return Math.min(Math.round(baseMs * Math.pow(1.15, Math.max(0, attempt - 1))), processing ? 3000 : 8000)
}

function parseProgressErrorPayload(progress) {
  if (!progress) return { message: '', messageKey: '', messageArgs: {} }
  if (progress.progressError) {
    const key = progress.progressError
    return {
      message: key,
      messageKey: key.startsWith('errors.') ? key : '',
      messageArgs: progress.progressErrorArgs || {},
    }
  }
  if (!progress.anomalySummary) return { message: '', messageKey: '', messageArgs: {} }
  try {
    const summary = typeof progress.anomalySummary === 'string'
      ? JSON.parse(progress.anomalySummary)
      : progress.anomalySummary
    const error = summary?.error || ''
    return {
      message: error,
      messageKey: error.startsWith('errors.') ? error : '',
      messageArgs: summary?.errorArgs || {},
    }
  } catch {
    return { message: '', messageKey: '', messageArgs: {} }
  }
}

function parseProgressError(task) {
  return parseProgressErrorPayload(task).message
}

function isOpaqueProgressError(message) {
  const raw = String(message || '').trim()
  if (!raw) return true
  if (raw.startsWith('errors.')) return false
  if (/^\d+$/.test(raw)) return true
  return raw.length <= 2
}

function buildProgressError(progress) {
  const payload = parseProgressErrorPayload(progress)
  let raw = payload.message || ''
  let messageKey = payload.messageKey
  if (!messageKey && raw.startsWith('errors.')) {
    messageKey = raw
  }
  if (!messageKey && isOpaqueProgressError(raw)) {
    raw = i18n.global.t('home.recognitionError')
  }
  const text = translateErrorMessage({
    message: raw,
    messageKey: messageKey || undefined,
    messageArgs: payload.messageArgs,
  })
  return { text, messageKey, messageArgs: payload.messageArgs }
}

function formatProgressError(progress) {
  return buildProgressError(progress).text
}

function attachProgressError(err, progress, taskId) {
  const built = buildProgressError(progress)
  err.message = built.text
  err.messageKey = built.messageKey
  err.messageArgs = built.messageArgs
  err.taskId = taskId
  return err
}

export function parseRecordsFromTask(task) {
  const payload = task?.rawData
  if (!payload) return []
  const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload
  return Array.isArray(parsed) ? parsed : []
}

export function parseImageQualityWarning(task) {
  if (!task?.anomalySummary) return null
  try {
    const summary = typeof task.anomalySummary === 'string'
      ? JSON.parse(task.anomalySummary)
      : task.anomalySummary
    if (summary?.imageQualityWarning) {
      return {
        blurPercent: summary.blurPercent ?? 0,
        unknownPercent: summary.unknownPercent ?? 0,
      }
    }
  } catch {
    // ignore malformed summary
  }
  return null
}

export function persistBgTaskId(taskId) {
  if (!taskId) return
  try {
    sessionStorage.setItem(BG_TASK_STORAGE_KEY, taskId)
  } catch {
    // ignore quota / private mode
  }
}

export function clearBgTaskId() {
  try {
    sessionStorage.removeItem(BG_TASK_STORAGE_KEY)
  } catch {
    // ignore
  }
}

export function getPersistedBgTaskId() {
  try {
    return sessionStorage.getItem(BG_TASK_STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

async function uploadOneFile(file, options = {}) {
  const res = await uploadImageAsync(buildUploadFormData(file, options))
  return res.data || {}
}

async function kickRecognitionIfIdle(taskId, rowCount, attempts, state) {
  if (rowCount > 0 || attempts < 3) return
  const now = Date.now()
  if (state.lastKickAt && now - state.lastKickAt < 30000) return
  if (state.kickCount >= 3) return
  state.kickCount += 1
  state.lastKickAt = now
  try {
    await startTaskRecognition(taskId)
  } catch {
    // 可能已在队列中，忽略
  }
}

export async function resolveRecognitionIfReady(taskId) {
  const progressRes = await getTaskProgress(taskId)
  const progress = progressRes.data || {}
  if (progress.status === 'processed') {
    clearBgTaskId()
    const detailRes = await getTaskDetail(taskId)
    const task = detailRes.data || {}
    const records = parseRecordsFromTask(task)
    return {
      taskId,
      task,
      records,
      rowCount: records.length || progress.progressRowCount || 0,
      imageQualityWarning: parseImageQualityWarning(task),
    }
  }
  if (progress.status === 'failed') {
    const err = new Error('')
    attachProgressError(err, progress, taskId)
    throw err
  }
  return null
}

/**
 * 轮询识别进度；网络错误时不放弃。shouldAbort 为 true 时仅停止前端轮询，服务端继续处理。
 */
export async function pollRecognitionUntilDone(taskId, options = {}) {
  const { onProgress, shouldAbort } = options
  let attempts = 0
  let lastRowCount = 0
  const deadline = Date.now() + POLL_DEADLINE_MS
  const kickState = { kickCount: 0, lastKickAt: 0 }

  const ready = await resolveRecognitionIfReady(taskId)
  if (ready) {
    return ready
  }

  while (Date.now() < deadline) {
    if (shouldAbort?.()) {
      return { aborted: true, taskId }
    }

    attempts += 1
    let progress = {}
    try {
      const progressRes = await getTaskProgress(taskId)
      progress = progressRes.data || {}
    } catch {
      onProgress?.({
        taskId,
        status: 'processing',
        rowCount: lastRowCount,
        phase: 'processing',
        networkRetry: true,
        attempt: attempts,
      })
      await new Promise((resolve) => {
        setTimeout(resolve, pollDelayMs(attempts, true))
      })
      continue
    }

    const status = progress.status
    const rowCount = progress.progressRowCount || 0
    const rowCountIncreased = rowCount > lastRowCount
    lastRowCount = rowCount

    onProgress?.({
      taskId,
      status,
      rowCount,
      phase: status === 'processed' ? 'processed' : 'processing',
      attempt: attempts,
    })

    if (status === 'processing' && rowCountIncreased) {
      // rowCount 有变化时尽快拉取 partial raw_data，不等到下一轮 poll
      onProgress?.({
        taskId,
        status,
        rowCount,
        phase: 'refresh',
        attempt: attempts,
      })
    }

    if (status === 'processed') {
      clearBgTaskId()
      const detailRes = await getTaskDetail(taskId)
      const task = detailRes.data || {}
      const records = parseRecordsFromTask(task)
      return {
        taskId,
        task,
        records,
        rowCount: records.length || rowCount,
        imageQualityWarning: parseImageQualityWarning(task),
      }
    }

    if (status === 'failed') {
      const err = new Error('')
      attachProgressError(err, progress, taskId)
      throw err
    }

    if (status === 'processing') {
      await kickRecognitionIfIdle(taskId, rowCount, attempts, kickState)
    }

    await new Promise((resolve) => {
      setTimeout(resolve, pollDelayMs(attempts, status === 'processing'))
    })
  }

  throw new Error(i18n.global.t('home.recognitionTimeout'))
}

/**
 * PC 端后台识别：先上传（多图批量合并），再由服务端异步识别。
 */
export async function submitBackgroundRecognition(files, options = {}) {
  const { onProgress, shouldAbort } = options
  if (!files?.length) {
    throw new Error(i18n.global.t('home.selectAtLeastOne'))
  }

  const total = files.length
  let taskId = null

  if (total === 1) {
    const payload = await uploadOneFile(files[0], { deferRecognition: false })
    taskId = payload.taskId
    if (!taskId) throw new Error(i18n.global.t('errors.taskNotFound'))
    persistBgTaskId(taskId)
    onProgress?.({ taskId, rowCount: 0, phase: 'processing', uploaded: 1, total: 1 })
    return pollRecognitionUntilDone(taskId, { onProgress, shouldAbort })
  }

  const firstPayload = await uploadOneFile(files[0], { deferRecognition: true })
  taskId = firstPayload.taskId
  if (!taskId) throw new Error(i18n.global.t('errors.taskNotFound'))
  persistBgTaskId(taskId)

  onProgress?.({ taskId, rowCount: 0, phase: 'uploading', uploaded: 1, total })

  for (let i = 1; i < total; i += 1) {
    if (shouldAbort?.()) {
      return { aborted: true, taskId }
    }
    await uploadOneFile(files[i], { taskId })
    onProgress?.({ taskId, rowCount: 0, phase: 'uploading', uploaded: i + 1, total })
  }

  if (shouldAbort?.()) {
    return { aborted: true, taskId }
  }

  await startTaskRecognition(taskId)
  onProgress?.({ taskId, rowCount: 0, phase: 'processing', uploaded: total, total })
  return pollRecognitionUntilDone(taskId, { onProgress, shouldAbort })
}

/** 对已上传图片的任务重新发起识别（无需重新上传文件） */
export async function retryTaskRecognition(taskId, options = {}) {
  const { onProgress, shouldAbort } = options
  if (!taskId) throw new Error(i18n.global.t('errors.taskNotFound'))
  persistBgTaskId(taskId)
  await startTaskRecognition(taskId)
  onProgress?.({ taskId, rowCount: 0, phase: 'processing' })
  return pollRecognitionUntilDone(taskId, { onProgress, shouldAbort })
}
