const { isApiSuccess, getApiData, getApiMessage } = require('./response')
const { t } = require('./i18n')
const { translateApiError } = require('./translateError')
const { getCountry } = require('./preferences')
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
  const sync = updateCurrentCountry(code, app)
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

const TEMPLATE_SURNAMES = [
  'DUPONT', 'MARTIN', 'BERNARD', 'PETIT', 'ROBERT', 'DURAND',
  'MOREAU', 'SIMON', 'LAURENT', 'LEFEBVRE'
]

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
  if (!Array.isArray(records) || records.length < 4) {
    return false
  }

  let exampleHits = 0
  let templateHits = 0
  let sequentialNo = 0
  const agencies = {}
  const pauses = new Set()
  let shiftHits = 0

  for (let i = 0; i < records.length; i++) {
    const r = records[i] || {}
    const name = String(r.NOM_PRENOM || r.nom_prenom || '').trim()
    const no = String(r.NO || r.no || '').trim()
    const agency = String(r.AGENCE_INTERIMAIRE || r.agence || '').trim()
    const shift = String(r.HORAIRES_DU_TRAVAIL || r.shift || '').toUpperCase()
    const pause = r.PAUSE != null ? String(r.PAUSE) : ''

    if (EXAMPLE_NAMES.has(name)) exampleHits++
    const upper = name.toUpperCase()
    if (TEMPLATE_SURNAMES.some((s) => upper.startsWith(s + ' ') || upper === s)) {
      templateHits++
    }
    if (no === String(i + 1) || no === String(i + 1).padStart(2, '0')) sequentialNo++
    if (agency) agencies[agency] = (agencies[agency] || 0) + 1
    if (pause) pauses.add(pause)
    if (shift === 'MATIN' || shift === 'SOIR' || shift === 'NUIT') shiftHits++
  }

  if (exampleHits >= 3) return true

  let score = 0
  if (sequentialNo >= records.length * 0.75) score++
  const maxAgency = Math.max(0, ...Object.values(agencies))
  if (maxAgency >= records.length * 0.85) score++
  if (pauses.size === 1 && records.length >= 5) score++
  if (templateHits >= 5) score++
  if (shiftHits >= records.length * 0.9 && records.length >= 6) score++
  return score >= 3
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

function fetchTask(taskId) {
  const app = getAppSafe()
  const baseUrl = (app && app.globalData.baseUrl) || ''
  const token = (app && app.globalData.token) || ''

  return new Promise((resolve, reject) => {
    tt.request({
      url: `${baseUrl}/tasks/${taskId}`,
      method: 'GET',
      timeout: 20000,
      header: {
        Authorization: token ? `Bearer ${token}` : ''
      },
      success: (res) => {
        if (res.statusCode === 200 && isApiSuccess(res.data)) {
          resolve(getApiData(res.data) || {})
        } else {
          reject(new Error(getApiMessage(res.data, t('upload.fetchTaskFail'))))
        }
      },
      fail: (err) => {
        reject(new Error(translateApiError({ message: (err && err.errMsg) }, t('errors.networkError'))))
      }
    })
  })
}

function parseRecordCount(task) {
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
  if (!task.anomalySummary) return ''
  try {
    const o = typeof task.anomalySummary === 'string'
      ? JSON.parse(task.anomalySummary)
      : task.anomalySummary
    return o.error || ''
  } catch {
    return ''
  }
}

function pollTaskUntilDone(taskId, options) {
  const intervalMs = (options && options.intervalMs) || 1500
  const maxAttempts = (options && options.maxAttempts) || 240
  let attempts = 0

  return new Promise((resolve, reject) => {
    const tick = () => {
      if (options && options.shouldAbort && options.shouldAbort()) {
        resolve({ aborted: true, taskId })
        return
      }
      attempts++
      fetchTask(taskId)
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
            traceLog.logServerTrace(task)
            traceLog.logTaskParse(task)

            if (engine === 'simulated' || engine.indexOf('simulated') >= 0) {
              reject(new Error(t('upload.simulatedRecognition')))
              return
            }
            if (rowCount === 0) {
              reject(new Error(t('upload.emptyResult')))
              return
            }
            if (looksLikeBadRecognitionData(task)) {
              reject(new Error(t('upload.fabricatedResult')))
              return
            }
            resolve({ task, rowCount, engine })
            return
          }
          if (status === 'failed') {
            traceLog.logServerTrace(task)
            reject(new Error(translateApiError({ message: parseTaskError(task) }, t('upload.recognizeFail'))))
            return
          }
          if (attempts >= maxAttempts) {
            reject(new Error(t('upload.recognizeTimeout')))
            return
          }
          setTimeout(tick, intervalMs)
        })
        .catch((err) => {
          traceLog.log('poll_error', { attempt: attempts, message: err && err.message })
          if (options && options.shouldAbort && options.shouldAbort()) {
            resolve({ aborted: true, taskId })
            return
          }
          if (attempts >= maxAttempts) {
            reject(err)
          } else {
            setTimeout(tick, intervalMs)
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
  const app = getAppSafe()
  const baseUrl = (app && app.globalData.baseUrl) || ''
  const token = (app && app.globalData.token) || ''
  const uploadCountry = country || resolveUploadCountry()
  const onProgress = options && options.onProgress
  const shouldAbort = options && options.shouldAbort

  return new Promise((resolve, reject) => {
    tt.request({
      url: `${baseUrl}/local/tasks/${encodeURIComponent(taskId)}/recognize`,
      method: 'POST',
      timeout: 60000,
      header: {
        Authorization: token ? `Bearer ${token}` : '',
        'X-Country': uploadCountry,
        'X-Client': 'feishu-miniprogram'
      },
      data: { country: uploadCountry },
      success: (res) => {
        if (res.statusCode === 200 && isApiSuccess(res.data)) {
          resolve(getApiData(res.data) || {})
          return
        }
        reject(new Error(getApiMessage(res.data, t('upload.startRecognizeFail'))))
      },
      fail: (err) => {
        reject(new Error(translateApiError({ message: (err && err.errMsg) }, t('upload.startRecognizeFail'))))
      }
    })
  }).then(() => {
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
  resolveUploadCountry
}
