import { getTaskDetail, getTaskProgress } from '@/api/task'
import { uploadImageAsync, startTaskRecognition } from '@/api/upload'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
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

function pollDelayMs(attempt, baseMs = 2000) {
  return Math.min(Math.round(baseMs * Math.pow(1.2, Math.max(0, attempt - 1))), 8000)
}

function parseProgressError(task) {
  if (task?.progressError) return task.progressError
  if (!task?.anomalySummary) return ''
  try {
    const summary = typeof task.anomalySummary === 'string'
      ? JSON.parse(task.anomalySummary)
      : task.anomalySummary
    return summary?.error || ''
  } catch {
    return ''
  }
}

export function parseRecordsFromTask(task) {
  const payload = task?.rawData
  if (!payload) return []
  const parsed = typeof payload === 'string' ? JSON.parse(payload) : payload
  return Array.isArray(parsed) ? parsed : []
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

/**
 * 轮询识别进度；网络错误时不放弃。shouldAbort 为 true 时仅停止前端轮询，服务端继续处理。
 */
export async function pollRecognitionUntilDone(taskId, options = {}) {
  const { onProgress, shouldAbort } = options
  let attempts = 0
  let lastRowCount = 0
  const deadline = Date.now() + POLL_DEADLINE_MS

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
        setTimeout(resolve, pollDelayMs(attempts))
      })
      continue
    }

    const status = progress.status
    const rowCount = progress.progressRowCount || 0
    lastRowCount = rowCount

    onProgress?.({
      taskId,
      status,
      rowCount,
      phase: status === 'processed' ? 'processed' : 'processing',
      attempt: attempts,
    })

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
      }
    }

    if (status === 'failed') {
      clearBgTaskId()
      const message = parseProgressError(progress) || i18n.global.t('home.recognitionError')
      throw new Error(message)
    }

    await new Promise((resolve) => {
      setTimeout(resolve, pollDelayMs(attempts))
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
