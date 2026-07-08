const { isApiSuccess, getApiData, getApiMessage } = require('./response')
const { t } = require('./i18n')
const { translateApiError } = require('./translateError')
const { getCountry } = require('./preferences')
const { apiCall } = require('./request')
const { updateCurrentCountry } = require('./configApi')
const { formatRecognitionEngine } = require('./engineLabel')
const { prepareImageForUpload } = require('./imagePrep')
const traceLog = require('./traceLog')

const EXAMPLE_NAMES = new Set(['张三', '李四', '王五', 'John Smith', 'Jane Doe', 'Bob Wilson'])

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function resolveUploadCountry() {
  const code = getCountry()
  return code && String(code).trim() ? String(code).trim() : 'default'
}

function syncCountryBeforeRecognition(app) {
  const code = resolveUploadCountry()
  traceLog.log('sync_country_start', { country: code })
  if (!code || !app || !app.globalData.token) {
    return Promise.resolve(code)
  }
  const sync = updateCurrentCountry(code, app, { personal: true })
    .then(() => {
      traceLog.log('sync_country_ok', { country: code })
      return code
    })
    .catch((err) => {
      traceLog.log('sync_country_fail', { country: code, message: err && err.message })
      return code
    })
  const timeout = new Promise((resolve) => {
    setTimeout(() => {
      traceLog.log('sync_country_timeout', { country: code })
      resolve(code)
    }, 3000)
  })
  return Promise.race([sync, timeout])
}

const EXAMPLE_KEYS = new Set([
  '1|张三', '2|李四', '3|王五',
  '1|John Smith', '2|Jane Doe', '3|Bob Wilson'
])

function isUnknownField(value) {
  if (value == null) return true
  const t = String(value).trim()
  if (!t) return true
  const lower = t.toLowerCase()
  return t === '???' || t === '??' || lower === 'unknown' || lower === 'illegible'
}

function hasFilledTime(record) {
  const r = record || {}
  return !isUnknownField(r.ARRIVEE || r.arrivee) || !isUnknownField(r.DEPAR || r.depart || r.DEPAR)
}

function isPromptExampleRecord(record) {
  const r = record || {}
  const no = String(r.NO || r.no || '').trim()
  const name = String(r.NOM_PRENOM || r.nom_prenom || '').trim()
  const agency = String(r.AGENCE_INTERIMAIRE || r.agence || '').trim()
  if (EXAMPLE_KEYS.has(`${no}|${name}`)) return true
  if (EXAMPLE_NAMES.has(name)) return true
  if (name === '???' && no === '4' && agency.indexOf('中介D') >= 0) return true
  if ((name.indexOf('张三') >= 0 || name.indexOf('李四') >= 0 || name.indexOf('王五') >= 0)
    && (agency.indexOf('中介A') >= 0 || agency.indexOf('中介B') >= 0 || agency.indexOf('中介C') >= 0)) {
    return true
  }
  return false
}

function looksUnreadableWithGuessedTimes(records) {
  if (!Array.isArray(records) || records.length < 3) return false
  const n = records.length
  let unknownIdentity = 0
  let unknownNo = 0
  let unknownName = 0
  let filledTimes = 0
  for (let i = 0; i < n; i++) {
    const r = records[i] || {}
    const uNo = isUnknownField(r.NO || r.no)
    const uName = isUnknownField(r.NOM_PRENOM || r.nom_prenom)
    if (uNo) unknownNo++
    if (uName) unknownName++
    if (uNo && uName) unknownIdentity++
    if (hasFilledTime(r)) filledTimes++
  }
  if (unknownIdentity >= n * 0.6 && filledTimes >= n * 0.5) return true
  return unknownNo >= n * 0.7 && unknownName >= n * 0.5 && filledTimes >= n * 0.6
}

function looksLikePromptExamples(task) {
  return looksLikeBadRecognitionData(task)
}

function looksLikeBadRecognitionData(task) {
  const raw = task.rawData
  if (!raw) return false
  let records
  try {
    records = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return false
  }
  if (!Array.isArray(records) || records.length < 3) {
    return false
  }

  let exampleHits = 0
  for (let i = 0; i < records.length; i++) {
    if (isPromptExampleRecord(records[i])) exampleHits++
  }
  if (exampleHits >= 3) return true
  return looksUnreadableWithGuessedTimes(records)
}

function uploadImageAsync(filePath, country, onProgress, options) {
  const app = getAppSafe()
  const baseUrl = (app && app.globalData.baseUrl) || ''
  const token = (app && app.globalData.token) || ''
  const uploadCountry = country || resolveUploadCountry()
  const taskId = options && options.taskId ? String(options.taskId) : ''

  traceLog.log('image_prep_start', { filePath, uploadCountry, baseUrl })

  return prepareImageForUpload(filePath)
    .then(({ path, size }) => {
      traceLog.log('image_prep_done', { path, size })
      return new Promise((resolve, reject) => {
        if (onProgress) {
          onProgress({
            status: 'uploading',
            rowCount: 0,
            engine: 'mimo',
            promptCountry: uploadCountry,
            engineLabel: t('recognizing.statusUploading'),
            attempt: 0
          })
        }

        traceLog.log('upload_start', {
          url: `${baseUrl}/local/upload-async`,
          country: uploadCountry,
          fileSize: size
        })

        tt.uploadFile({
          url: `${baseUrl}/local/upload-async`,
          filePath: path,
          name: 'image',
          fileName: 'photo.jpg',
          timeout: 120000,
          formData: (() => {
            const data = { country: uploadCountry }
            if (taskId) {
              data.taskId = taskId
            }
            if (options && options.deferRecognition) {
              data.deferRecognition = 'true'
            }
            return data
          })(),
          header: {
            Authorization: token ? `Bearer ${token}` : '',
            'X-Country': uploadCountry,
            'X-Client': 'feishu-miniprogram'
          },
          success: (res) => {
            traceLog.log('upload_http', {
              statusCode: res.statusCode,
              bodyPreview: traceLog.preview(res.data, 500)
            })
            try {
              const data = JSON.parse(res.data)
              if (!isApiSuccess(data)) {
                traceLog.log('upload_api_error', { message: getApiMessage(data, t('upload.fail')) })
                reject(new Error(getApiMessage(data, t('upload.fail'))))
                return
              }
              const payload = getApiData(data) || {}
              if (!payload.promptCountry) {
                payload.promptCountry = uploadCountry
              }
              if (payload.appendOnly) {
                payload.uploadOnly = true
              }
              traceLog.log('upload_response', payload)
              resolve(payload)
            } catch (e) {
              traceLog.log('upload_parse_error', { message: e.message })
              reject(new Error(t('upload.parseResponseFail')))
            }
          },
          fail: (err) => {
            traceLog.log('upload_fail', { errMsg: err && err.errMsg })
            reject(new Error(translateApiError({ message: (err && err.errMsg) || (err && err.message) }, t('upload.fail'))))
          }
        })
      })
    })
}

function fetchTaskProgress(taskId) {
  return apiCall({ url: `/tasks/${taskId}/progress`, timeout: 15000 })
    .then((res) => {
      if (isApiSuccess(res.data)) {
        return getApiData(res.data) || {}
      }
      throw new Error(getApiMessage(res.data, t('upload.fetchTaskFail')))
    })
    .catch((err) => {
      throw new Error(translateApiError(
        { message: (err && err.errMsg) || (err && err.message) },
        t('errors.networkError')
      ))
    })
}

function fetchTask(taskId) {
  return apiCall({ url: `/tasks/${taskId}`, timeout: 20000 })
    .then((res) => {
      if (isApiSuccess(res.data)) {
        return getApiData(res.data) || {}
      }
      throw new Error(getApiMessage(res.data, t('upload.fetchTaskFail')))
    })
    .catch((err) => {
      throw new Error(translateApiError(
        { message: (err && err.errMsg) || (err && err.message) },
        t('errors.networkError')
      ))
    })
}

function parseRecordCount(task) {
  if (task == null) return 0
  if (typeof task.progressRowCount === 'number') {
    return task.progressRowCount
  }
  const raw = task.rawData
  if (!raw) return 0
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

function parseTaskError(task) {
  if (task && task.progressError) return task.progressError
  if (!task || !task.anomalySummary) return ''
  try {
    const o = typeof task.anomalySummary === 'string'
      ? JSON.parse(task.anomalySummary)
      : task.anomalySummary
    return o.error || o.messageKey || ''
  } catch {
    return ''
  }
}

function parseImageQualityWarning(task) {
  if (!task || !task.anomalySummary) return null
  try {
    const o = typeof task.anomalySummary === 'string'
      ? JSON.parse(task.anomalySummary)
      : task.anomalySummary
    if (o && o.imageQualityWarning) {
      return {
        blurPercent: o.blurPercent || 0,
        unknownPercent: o.unknownPercent || 0
      }
    }
  } catch (e) {
    return null
  }
  return null
}

function pollIntervalMs(attempt, baseMs) {
  const capped = Math.min(baseMs * Math.pow(1.25, Math.max(0, attempt - 1)), 8000)
  if (typeof document !== 'undefined' && document.hidden) {
    return Math.min(capped * 2, 12000)
  }
  return capped
}

function pollTaskUntilDone(taskId, options) {
  const baseIntervalMs = (options && options.intervalMs) || 1500
  const maxAttempts = (options && options.maxAttempts) || 240
  let attempts = 0
  let hiddenListener = null

  return new Promise((resolve, reject) => {
    const cleanup = () => {
      if (hiddenListener && typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', hiddenListener)
        hiddenListener = null
      }
    }

    if (typeof document !== 'undefined') {
      hiddenListener = () => {
        traceLog.log('poll_visibility', { hidden: document.hidden, attempt: attempts })
      }
      document.addEventListener('visibilitychange', hiddenListener)
    }

    const tick = () => {
      if (options && options.shouldAbort && options.shouldAbort()) {
        cleanup()
        resolve({ aborted: true, taskId })
        return
      }
      attempts++
      fetchTaskProgress(taskId)
        .then((task) => {
          const status = task.status
          const rowCount = parseRecordCount(task)
          const engine = task.aiRawOutput || ''

          traceLog.log('poll_task', {
            attempt: attempts,
            status,
            rowCount,
            engine,
            rawPreview: traceLog.preview(task.rawData, 200)
          })

          if (options && options.onProgress) {
            options.onProgress({
              status: status === 'processed' ? 'processed' : 'processing',
              rowCount,
              engine,
              promptCountry: options.promptCountry,
              engineLabel: formatRecognitionEngine(engine, options.promptCountry),
              attempt: attempts
            })
          }

          if (status === 'processed') {
            fetchTask(taskId)
              .then((fullTask) => {
                traceLog.logServerTrace(fullTask)
                traceLog.logTaskParse(fullTask)
                const fullEngine = fullTask.aiRawOutput || engine
                if (fullEngine === 'simulated' || String(fullEngine).indexOf('simulated') >= 0) {
                  cleanup()
                  reject(new Error(t('upload.simulatedRecognition')))
                  return
                }
                const fullCount = parseRecordCount(fullTask)
                if (fullCount === 0) {
                  cleanup()
                  reject(new Error(t('upload.emptyResult')))
                  return
                }
                if (looksLikeBadRecognitionData(fullTask)) {
                  cleanup()
                  reject(new Error(t('upload.fabricatedResult')))
                  return
                }
                cleanup()
                resolve({
                  task: fullTask,
                  rowCount: fullCount,
                  engine: fullEngine,
                  imageQualityWarning: parseImageQualityWarning(fullTask)
                })
              })
              .catch((err) => {
                cleanup()
                reject(err)
              })
            return
          }
          if (status === 'failed') {
            traceLog.logServerTrace(task)
            cleanup()
            reject(new Error(translateApiError({ message: parseTaskError(task) }, t('upload.recognizeFail'))))
            return
          }
          if (attempts >= maxAttempts) {
            cleanup()
            reject(new Error(t('upload.recognizeTimeout')))
            return
          }
          setTimeout(tick, pollIntervalMs(attempts, baseIntervalMs))
        })
        .catch((err) => {
          traceLog.log('poll_error', { attempt: attempts, message: err && err.message })
          if (options && options.shouldAbort && options.shouldAbort()) {
            cleanup()
            resolve({ aborted: true, taskId })
            return
          }
          if (attempts >= maxAttempts) {
            cleanup()
            reject(err)
          } else {
            setTimeout(tick, pollIntervalMs(attempts, baseIntervalMs))
          }
        })
    }
    tick()
  })
}

/**
 * 上传图片并由服务端异步识别；可选轮询直至完成。
 * shouldAbort 返回 true 时停止前端轮询（服务端仍继续处理）。
 */
function startTaskRecognition(taskId, country, options) {
  const uploadCountry = country || resolveUploadCountry()
  const onProgress = options && options.onProgress
  const shouldAbort = options && options.shouldAbort

  return apiCall({
    url: `/local/tasks/${encodeURIComponent(taskId)}/recognize`,
    method: 'POST',
    timeout: 60000,
    header: {
      'X-Country': uploadCountry,
      'X-Client': 'feishu-miniprogram'
    },
    data: { country: uploadCountry }
  })
    .then((res) => {
      if (!isApiSuccess(res.data)) {
        throw new Error(getApiMessage(res.data, t('upload.startRecognizeFail')))
      }
      return getApiData(res.data) || {}
    })
    .catch((err) => {
      throw new Error(translateApiError(
        { message: (err && err.errMsg) || (err && err.message) },
        t('upload.startRecognizeFail')
      ))
    })
    .then(() => {
    if (shouldAbort && shouldAbort()) {
      return { aborted: true, taskId, promptCountry: uploadCountry }
    }
    if (onProgress) {
      onProgress({
        status: 'processing',
        rowCount: 0,
        engine: 'mimo',
        promptCountry: uploadCountry,
        engineLabel: formatRecognitionEngine('mimo', uploadCountry),
        attempt: 0
      })
    }
    return pollTaskUntilDone(taskId, {
      onProgress,
      shouldAbort,
      promptCountry: uploadCountry
    }).then((result) => ({
      ...result,
      taskId,
      promptCountry: uploadCountry,
      engine: (result && result.engine) || 'mimo'
    }))
  })
}

/**
 * 多图批量：先上传全部原图到同一任务，再一次性识别合并结果。
 */
function runBatchUploadAndWatch(firstPath, restPaths, options) {
  const queue = (restPaths || []).slice()
  const onProgress = options && options.onProgress
  const shouldAbort = options && options.shouldAbort
  const onUploadProgress = options && options.onUploadProgress
  const app = getAppSafe()
  const uploadCountry = resolveUploadCountry()

  traceLog.clear()
  traceLog.log('batch_upload_start', { firstPath, queueSize: queue.length })

  if (onProgress) {
    onProgress({
      status: 'ready',
      rowCount: 0,
      promptCountry: uploadCountry,
      engineLabel: formatRecognitionEngine('mimo', uploadCountry),
      attempt: 0
    })
  }

  return syncCountryBeforeRecognition(app)
    .then((country) => uploadImageAsync(firstPath, country, onProgress, { deferRecognition: true })
      .then((payload) => {
        const taskId = payload.taskId
        if (!taskId) {
          return Promise.reject(new Error(t('upload.noTaskId')))
        }
        if (engineIsSimulated(payload)) {
          return Promise.reject(new Error(t('upload.simulatedServer')))
        }

        let chain = Promise.resolve({ taskId, country })
        const total = 1 + queue.length
        let uploaded = 1
        if (onUploadProgress) {
          onUploadProgress({ current: uploaded, total })
        }

        queue.forEach((path) => {
          chain = chain.then((state) => {
            if (shouldAbort && shouldAbort()) {
              return Promise.reject({ aborted: true, taskId: state.taskId })
            }
            return uploadImageAsync(path, state.country, onProgress, { taskId: state.taskId })
              .then(() => {
                uploaded += 1
                if (onUploadProgress) {
                  onUploadProgress({ current: uploaded, total })
                }
                return state
              })
          })
        })

        return chain
      }))
    .then((state) => {
      if (shouldAbort && shouldAbort()) {
        return { aborted: true, taskId: state.taskId, promptCountry: state.country }
      }
      traceLog.log('batch_upload_done', { taskId: state.taskId, imageCount: 1 + queue.length })
      return startTaskRecognition(state.taskId, state.country, {
        onProgress,
        shouldAbort
      })
    })
    .catch((err) => {
      if (err && err.aborted) {
        return { aborted: true, taskId: err.taskId }
      }
      throw err
    })
}

function engineIsSimulated(payload) {
  const engine = payload && payload.recognitionEngine
  return engine === 'simulated' || (engine && String(engine).indexOf('simulated') >= 0)
}

function runUploadAndWatch(filePath, options) {
  const onProgress = options && options.onProgress
  const shouldAbort = options && options.shouldAbort
  const app = getAppSafe()
  const uploadCountry = resolveUploadCountry()

  traceLog.clear()
  traceLog.log('upload_watch_start', { filePath, uploadCountry })

  if (onProgress) {
    onProgress({
      status: 'ready',
      rowCount: 0,
      promptCountry: uploadCountry,
      engineLabel: formatRecognitionEngine('mimo', uploadCountry),
      attempt: 0
    })
  }

  return syncCountryBeforeRecognition(app)
    .then((country) => uploadImageAsync(filePath, country, onProgress))
    .then((payload) => {
      const taskId = payload.taskId
      const engine = payload.recognitionEngine || 'mimo'
      const promptCountry = payload.promptCountry || uploadCountry

      if (engine === 'simulated' || String(engine).indexOf('simulated') >= 0) {
        return Promise.reject(new Error(t('upload.simulatedServer')))
      }
      if (!taskId) {
        return Promise.reject(new Error(t('upload.noTaskId')))
      }

      if (shouldAbort && shouldAbort()) {
        return { aborted: true, taskId, promptCountry, engine }
      }

      if (onProgress) {
        onProgress({
          status: 'processing',
          rowCount: 0,
          engine,
          promptCountry,
          engineLabel: formatRecognitionEngine(engine, promptCountry),
          attempt: 0
        })
      }

      if (options && options.uploadOnly) {
        return { aborted: false, uploadOnly: true, taskId, promptCountry, engine }
      }

      return pollTaskUntilDone(taskId, {
        onProgress,
        shouldAbort,
        promptCountry
      }).then((result) => ({
        ...result,
        taskId,
        promptCountry,
        engine: (result && result.engine) || engine
      }))
    })
}

function startRecognition(filePath, onProgress) {
  traceLog.clear()
  const app = getAppSafe()
  const uploadCountry = resolveUploadCountry()

  traceLog.log('recognition_start', { filePath, uploadCountry })

  if (onProgress) {
    onProgress({
      status: 'ready',
      rowCount: 0,
      promptCountry: uploadCountry,
      engineLabel: formatRecognitionEngine('mimo', uploadCountry),
      attempt: 0
    })
  }

  return syncCountryBeforeRecognition(app)
    .then((country) => uploadImageAsync(filePath, country, onProgress))
    .then((payload) => {
      const taskId = payload.taskId
      const engine = payload.recognitionEngine || 'mimo'
      const promptCountry = payload.promptCountry || uploadCountry
      const promptSection = payload.promptSection || ''

      traceLog.log('upload_accepted', { taskId, engine, promptCountry, promptSection })

      if (engine === 'simulated' || String(engine).indexOf('simulated') >= 0) {
        return Promise.reject(new Error(t('upload.simulatedServer')))
      }
      if (!taskId) {
        return Promise.reject(new Error(t('upload.noTaskId')))
      }

      if (onProgress) {
        onProgress({
          status: 'processing',
          rowCount: 0,
          engine,
          promptCountry,
          promptSection,
          engineLabel: formatRecognitionEngine(engine, promptCountry),
          attempt: 0
        })
      }

      return pollTaskUntilDone(taskId, { onProgress, promptCountry }).then((result) => {
        traceLog.log('recognition_success', {
          taskId,
          engine: result.engine,
          rowCount: result.rowCount
        })
        return {
          taskId,
          engine: result.engine || engine,
          promptCountry,
          promptSection,
          rowCount: result.rowCount,
          traceDump: traceLog.formatDump()
        }
      })
    })
    .catch((err) => {
      traceLog.log('recognition_fail', { message: err && err.message })
      err.traceDump = traceLog.formatDump()
      throw err
    })
}

module.exports = {
  startRecognition,
  uploadImageAsync,
  pollTaskUntilDone,
  runUploadAndWatch,
  runBatchUploadAndWatch,
  startTaskRecognition,
  resolveUploadCountry,
  parseImageQualityWarning
}
