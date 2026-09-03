const {
  runUploadAndWatch,
  runBatchUploadAndWatch,
  pollTaskUntilDone,
  uploadImageAsync,
  resolveUploadCountry
} = require('../../utils/recognitionUpload')
const { getCountryLabel } = require('../../utils/preferences')

const { t } = require('../../utils/i18n')
const { translateErrorMessage } = require('../../utils/translateError')
const { markTaskDataDirty } = require('../../utils/taskSummary')

Page({
  data: {
    statusText: '',
    engineText: '',
    countryText: '',
    rowCount: 0,
    elapsedText: '',
    currentStep: 1,
    hint: '',
    batchLabel: '',
    texts: {}
  },

  onLoad: function (options) {
    this._userLeft = false
    this._batchQueue = []
    this._batchTotal = 0
    this._batchIndex = 0
    this._sharedTaskId = ''

    this.setData({
      texts: {
        stepUpload: t('recognizing.stepUpload'),
        stepAnalyze: t('recognizing.stepAnalyze'),
        stepDone: t('recognizing.stepDone'),
        backgroundHint: t('recognizing.backgroundHint'),
        contextCountryPending: t('recognizing.contextCountryPending'),
        contextElapsedPending: t('recognizing.contextElapsedPending'),
        rowCountLabel: t('recognizing.rowCountLabel'),
        exitHome: t('recognizing.exitHome'),
        viewTasks: t('recognizing.viewTasks')
      },
      statusText: t('recognizing.initialStatus'),
      hint: t('recognizing.hint')
    })
    tt.setNavigationBarTitle({ title: t('recognizing.navTitle') })

    this._startTime = Date.now()
    this._elapsedTimer = setInterval(() => {
      const sec = Math.floor((Date.now() - this._startTime) / 1000)
      this.setData({ elapsedText: t('recognizing.elapsedSeconds', { seconds: sec }) })
    }, 1000)

    const taskId = options && options.taskId
    const imagePath = options && options.imagePath
    const isBatch = options && options.mode === 'batch'

    if (isBatch) {
      this._batchTotal = Number(options.batchTotal) || 0
      try {
        const app = getApp()
        this._batchQueue = (app.globalData.recognitionQueue || []).slice()
        app.globalData.recognitionQueue = []
      } catch (e) {
        this._batchQueue = []
      }
      if (!this._batchTotal && imagePath) {
        this._batchTotal = 1 + this._batchQueue.length
      }
      this._batchIndex = 1
      this.updateBatchLabel()
    }

    if (taskId) {
      this.watchTask(taskId)
      return
    }

    if (imagePath) {
      const path = decodeURIComponent(imagePath)
      if (isBatch) {
        this.runBatchRecognition(path)
      } else {
        this.runPathRecognition(path)
      }
      return
    }

    this.stopTimers()
    tt.showToast({ title: t('recognizing.missingImage'), icon: 'none' })
    setTimeout(() => tt.navigateBack(), 1500)
  },

  shouldAbort: function () {
    return !!this._userLeft
  },

  updateBatchLabel: function () {
    if (!this._batchTotal || this._batchTotal <= 1) {
      this.setData({ batchLabel: '' })
      return
    }
    this.setData({
      batchLabel: t('recognizing.batchProgress', {
        current: this._batchIndex,
        total: this._batchTotal
      })
    })
  },

  applyProgress: function (progress) {
    const engineLabel = progress.engineLabel || ''
    const countryText = progress.promptCountry
      ? getCountryLabel(progress.promptCountry)
      : ''
    let statusText = t('recognizing.statusDefault')

    if (progress.status === 'ready' && countryText) {
      statusText = t('recognizing.statusReady', { country: countryText })
    } else if (progress.status === 'uploading') {
      statusText = progress.uploadLabel || t('recognizing.statusUploading')
    } else if (progress.status === 'processing') {
      if (progress.rowCount > 0) {
        statusText = t('recognizing.statusProcessingRows', { count: progress.rowCount })
      } else if (progress.attempt <= 3) {
        statusText = t('recognizing.statusProcessingEarly')
      } else {
        statusText = t('recognizing.statusProcessingWait', { seconds: Math.max(1, progress.attempt * 2) })
      }
    } else if (progress.status === 'processed') {
      statusText = t('recognizing.statusProcessed')
    }

    let currentStep = 1
    if (progress.status === 'uploading' || progress.status === 'ready') {
      currentStep = 1
    } else if (progress.status === 'processing') {
      currentStep = 2
    } else if (progress.status === 'processed') {
      currentStep = 3
    }

    this.setData({
      statusText,
      engineText: engineLabel,
      countryText,
      rowCount: progress.rowCount || 0,
      currentStep
    })
  },

  openResultPage: function (taskId, options) {
    if (!taskId) {
      return
    }
    markTaskDataDirty()
    this.stopTimers()
    let url = `/pages/result/index?id=${taskId}`
    if (options && options.showQualityBanner) {
      url += `&qualityWarn=1&blurPercent=${options.blurPercent || 0}&unknownPercent=${options.unknownPercent || 0}`
    }
    tt.redirectTo({ url })
  },

  runPathRecognition: function (imagePath) {
    return runUploadAndWatch(imagePath, {
      onProgress: (progress) => this.applyProgress(progress),
      shouldAbort: () => this.shouldAbort()
    })
      .then((result) => this.handleRecognitionResult(result))
      .catch((err) => this.handleRecognitionError(err))
  },

  runBatchRecognition: function (firstPath) {
    const queue = this._batchQueue || []
    const total = 1 + queue.length
    this._batchTotal = total

    return runBatchUploadAndWatch(firstPath, queue, {
      onProgress: (progress) => this.applyProgress(progress),
      shouldAbort: () => this.shouldAbort(),
      onUploadProgress: ({ current, total: uploadTotal }) => {
        this._batchIndex = current
        this._batchTotal = uploadTotal
        this.updateBatchLabel()
        this.applyProgress({
          status: 'uploading',
          rowCount: this.data.rowCount,
          engine: '',
          promptCountry: resolveUploadCountry(),
          engineLabel: '',
          uploadLabel: t('recognizing.batchUploading', { current, total: uploadTotal }),
          attempt: 0
        })
      }
    })
      .then((result) => this.handleRecognitionResult(result))
      .catch((err) => this.handleRecognitionError(err, { isBatch: true }))
  },

  handleRecognitionResult: function (result) {
    if (!result) return
    if (result.aborted || this._userLeft) return
    if (result.taskId) {
      this._sharedTaskId = result.taskId
      this.openResultPage(result.taskId)
    }
  },

  watchTask: function (taskId) {
    pollTaskUntilDone(taskId, {
      onProgress: (progress) => this.applyProgress(progress),
      shouldAbort: () => this.shouldAbort()
    })
      .then((result) => {
        if (result && result.aborted) return
        if (this._userLeft) return
        this.openResultPage(taskId)
      })
      .catch((err) => this.handleRecognitionError(err))
  },

  stopTimers: function () {
    if (this._elapsedTimer) {
      clearInterval(this._elapsedTimer)
      this._elapsedTimer = null
    }
  },

  onUnload: function () {
    this._userLeft = true
    this.stopTimers()
  },

  leaveToHome: function () {
    this._userLeft = true
    this.stopTimers()
    tt.switchTab({ url: '/pages/index/index' })
  },

  leaveToTasks: function () {
    this._userLeft = true
    this.stopTimers()
    try {
      const app = getApp()
      app.globalData.tasksInitialTab = 'review'
    } catch (e) {
      // ignore
    }
    tt.switchTab({ url: '/pages/tasks/index' })
  },

  handleRecognitionError: function (err, meta) {
    if (this._userLeft) return
    this.stopTimers()
    const isBatch = meta && meta.isBatch
    const message = translateErrorMessage(err, t('recognizing.errorDefault'))
    console.warn('recognition failed', err)
    tt.showModal({
      title: t('recognizing.errorTitle'),
      content: message,
      showCancel: !!isBatch,
      cancelText: t('recognizing.viewTasks'),
      confirmText: t('recognizing.errorConfirm'),
      success: (res) => {
        if (res.cancel && isBatch) {
          this.leaveToTasks()
        } else if (this._sharedTaskId) {
          this.openResultPage(this._sharedTaskId)
        } else {
          tt.navigateBack()
        }
      }
    })
  }
})
