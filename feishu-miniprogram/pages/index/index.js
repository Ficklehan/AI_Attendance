const App = getApp()
const { isApiSuccess, getApiData } = require('../../utils/response')
const { mapTaskList } = require('../../utils/task')
const {
  fetchTaskSummary,
  toStatusSummary,
  fetchFirstReviewTask,
  shouldReloadTaskData,
  markTaskDataDirty
} = require('../../utils/taskSummary')
const { taskApi } = require('../../utils/api')
const { t, getLocale, localeToLanguageKey } = require('../../utils/i18n')
const { runWithCountryGate } = require('../../utils/countryGate')
const {
  getCountry,
  getCountryLabel,
  isCountryConfigured,
  syncCountryFromServer,
  redirectToCountrySetupIfNeeded,
} = require('../../utils/preferences')
const {
  runUploadAndWatch,
  runBatchUploadAndWatch,
  pollTaskUntilDone,
} = require('../../utils/recognitionUpload')
const { translateErrorMessage } = require('../../utils/translateError')

const HOME_TEXT_FALLBACK = {
  navTitle: '识别',
  countryWarnBanner: '尚未配置国家规则，点击前往设置',
  contextCountryLabel: '当前规则',
  contextLanguageLabel: '语言',
  contextChange: '更改',
  heroTitle: '拍照识别考勤表',
  heroSub: '拍完自动解析，核对后提交',
  stepCapture: '拍摄',
  stepReview: '核对',
  stepSubmit: '提交',
  sectionQueue: '待识别队列',
  scanTitle: '拍照',
  scanSub: '打开相机拍摄',
  uploadAlbum: '从相册选择',
  uploadAlbumSub: 'JPG / PNG',
  clear: '清空',
  statusTitle: '任务概览',
  statusProcessing: '识别中',
  statusReview: '待核对',
  statusCompleted: '已完成',
  viewAll: '查看全部',
  recognizing: '识别中...',
  progressThenDetail: '识别中只显示进度，完成后进入任务详情核对',
  progressRows: '已识别 {count} 条',
  primaryCtaScan: '拍照识别',
  primaryCtaReview: '去核对 ({count})',
  primaryCtaQueue: '开始识别 ({count})',
  countryNotSet: '未配置国家',
  recentTasks: '近期任务',
  noTasks: '暂无任务，拍照后将显示在这里'
}

function homeText(key, params) {
  const fullKey = `home.${key}`
  const value = t(fullKey, params)
  if (value && value !== fullKey) return value
  const fallback = HOME_TEXT_FALLBACK[key]
  if (!fallback) return value
  if (!params) return fallback
  return String(fallback).replace(/\{(\w+)\}/g, (_, name) => (
    params[name] != null ? String(params[name]) : `{${name}}`
  ))
}

Page({
  data: {
    imageList: [],
    recentTasks: [],
    processingTasks: [],
    isRecognizing: false,
    recognizeStatus: '',
    rowCount: 0,
    progressRowsText: '',
    countryConfigured: false,
    todaySummary: '',
    primaryCtaLabel: '',
    dockHint: '',
    contextCountryLabel: '',
    contextLanguageLabel: '',
    workflowStage: 'capture',
    statusSummary: {
      processing: 0,
      review: 0,
      completed: 0
    },
    texts: { ...HOME_TEXT_FALLBACK }
  },

  onLoad: function () {
    this._taskDataVersion = getApp().globalData.taskDataVersion || 0
    this.refreshTexts()
    this.loadTasks()
  },

  onShow: function () {
    syncCountryFromServer().finally(() => {
      if (redirectToCountrySetupIfNeeded()) {
        return
      }
      this.refreshTexts()
      if (shouldReloadTaskData(this)) {
        this.loadTasks()
      }
    })
  },

  refreshTexts: function () {
    const country = getCountry()
    const locale = getLocale()
    const localeKey = localeToLanguageKey(locale)
    const todayCount = this._todayTaskCount || 0
    const reviewCount = (this.data.statusSummary && this.data.statusSummary.review) || 0
    const hasSubmitted = (this.data.recentTasks || []).some((task) => task.status === 'confirmed')
    const total = (this.data && this.data.imageList ? this.data.imageList.length : 0)
    const recognizing = !!(this.data && this.data.isRecognizing)
    let primaryCtaLabel = homeText('primaryCtaScan')
    if (recognizing) {
      primaryCtaLabel = homeText('recognizing')
    } else if (total > 0) {
      primaryCtaLabel = homeText('primaryCtaQueue', { count: total })
    } else if (reviewCount > 0) {
      primaryCtaLabel = homeText('primaryCtaReview', { count: reviewCount })
    }

    let workflowStage = 'capture'
    if (total > 0) workflowStage = 'capture'
    else if (reviewCount > 0) workflowStage = 'review'
    else if (hasSubmitted) workflowStage = 'submit'

    const configured = isCountryConfigured() && !!country
    this.setData({
      texts: {
        navTitle: homeText('navTitle'),
        countryWarnBanner: homeText('countryWarnBanner'),
        contextCountryLabel: homeText('contextCountryLabel'),
        contextLanguageLabel: homeText('contextLanguageLabel'),
        contextChange: homeText('contextChange'),
        heroTitle: homeText('heroTitle'),
        heroSub: homeText('heroSub'),
        stepCapture: homeText('stepCapture'),
        stepReview: homeText('stepReview'),
        stepSubmit: homeText('stepSubmit'),
        sectionQueue: homeText('sectionQueue'),
        sectionProcessing: homeText('sectionProcessing'),
        scanTitle: homeText('scanTitle'),
        scanSub: homeText('scanSub'),
        uploadAlbum: homeText('uploadAlbum'),
        uploadAlbumSub: homeText('uploadAlbumSub'),
        clear: homeText('clear'),
        processingHint: homeText('processingHint'),
        reviewHint: homeText('reviewHint'),
        recognizing: homeText('recognizing'),
        progressThenDetail: homeText('progressThenDetail'),
        recentTasks: homeText('recentTasks'),
        viewAll: homeText('viewAll'),
        noTasks: homeText('noTasks'),
        batchTitle: homeText('batchTitle'),
        batchContent: homeText('batchContent', { count: total }),
        batchConfirm: homeText('batchConfirm'),
        batchProgress: homeText('batchProgress'),
        batchDone: homeText('batchDone'),
        batchDoneWithFail: homeText('batchDoneWithFail'),
        queueUploading: homeText('queueUploading'),
        queueQueued: homeText('queueQueued'),
        queueFailed: homeText('queueFailed'),
        statusTitle: homeText('statusTitle'),
        statusProcessing: homeText('statusProcessing'),
        statusReview: homeText('statusReview'),
        statusCompleted: homeText('statusCompleted')
      },
      todaySummary: todayCount > 0
        ? t('home.todaySummaryCount', { count: todayCount })
        : t('home.todaySummary'),
      countryConfigured: configured,
      primaryCtaLabel,
      dockHint: total > 0 ? t('home.dockQueueHint', { count: total }) : '',
      contextCountryLabel: configured ? getCountryLabel(country) : homeText('countryNotSet'),
      contextLanguageLabel: t(`language.${localeKey}`) || HOME_TEXT_FALLBACK.contextLanguageLabel,
      workflowStage
    })
    tt.setNavigationBarTitle({ title: homeText('navTitle') })
  },

  onPrimaryCta: function () {
    if (this.data.isRecognizing) return
    const queueCount = (this.data.imageList && this.data.imageList.length) || 0
    if (queueCount > 0) {
      this.startRecognition()
      return
    }
    const reviewCount = (this.data.statusSummary && this.data.statusSummary.review) || 0
    if (reviewCount > 0) {
      fetchFirstReviewTask().then((reviewTask) => {
        if (reviewTask && reviewTask.id) {
          tt.navigateTo({ url: `/pages/result/index?id=${reviewTask.id}` })
          return
        }
        this.goToReviewTasks()
      })
      return
    }
    this.goToCamera()
  },

  goToReviewTasks: function () {
    this.goToTasks({ currentTarget: { dataset: { tab: 'review' } } })
  },

  goToSettings: function () {
    tt.navigateTo({ url: '/pages/settings/index' })
  },

  goToCamera: function () {
    if (this.data.isRecognizing) return
    runWithCountryGate(() => {
      tt.navigateTo({ url: '/pages/camera/index' })
    })
  },

  chooseImage: function () {
    if (this.data.isRecognizing) return
    runWithCountryGate(() => {
      tt.chooseImage({
        count: 9,
        sizeType: ['compressed'],
        sourceType: ['album'],
        success: (res) => {
          const nextImages = (res.tempFilePaths || []).map((path) => ({
            path,
            status: 'ready',
            statusText: ''
          }))
          this.setData({
            imageList: [...this.data.imageList, ...nextImages]
          })
          this.refreshTexts()
        },
        fail: (error) => {
          console.error('选择图片失败:', error)
          tt.showToast({ title: t('home.selectImageFail'), icon: 'none' })
        }
      })
    })
  },

  deleteImage: function (e) {
    const index = Number(e.currentTarget.dataset.index)
    this.setData({
      imageList: this.data.imageList.filter((_, i) => i !== index)
    })
    this.refreshTexts()
  },

  clearImages: function () {
    if (this.data.isRecognizing) return
    tt.showModal({
      title: t('home.clearConfirmTitle'),
      content: t('home.clearConfirmContent'),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (res.confirm) {
          this.setData({ imageList: [] })
          this.refreshTexts()
        }
      }
    })
  },

  startRecognition: function () {
    runWithCountryGate(() => {
      if (this.data.isRecognizing) return
    if (!this.data.imageList.length) {
        tt.showToast({ title: t('home.selectImageFirst'), icon: 'none' })
        return
      }
      const total = this.data.imageList.length
      const beginQueue = () => {
        if (this.data.isRecognizing) return
        this.queueRecognitionTasks()
      }
      if (total === 1) {
        beginQueue()
        return
      }
      const title = t('home.batchTitle')
      const content = t('home.batchContent', { count: total })
      const confirmText = t('home.batchConfirm') || t('common.confirm')
      const cancelText = t('common.cancel')
      if (!title || !content || title === 'home.batchTitle' || content === 'home.batchContent') {
        beginQueue()
        return
      }
      tt.showModal({
        title,
        content,
        confirmText,
        cancelText,
        showCancel: true,
        success: (res) => {
          if (res.confirm) beginQueue()
        },
        fail: (err) => {
          console.warn('batch confirm modal failed', err)
          beginQueue()
        }
      })
    })
  },

  queueRecognitionTasks: function () {
    const paths = (this.data.imageList || [])
      .map((item) => (typeof item === 'string' ? item : item.path))
      .filter(Boolean)
    if (!paths.length || this.data.isRecognizing) return

    this.setData({
      isRecognizing: true,
      recognizeStatus: homeText('recognizing'),
      rowCount: 0,
      progressRowsText: ''
    })
    this.refreshTexts()

    const onProgress = (progress) => this.applyRecognizeProgress(progress)
    const shouldAbort = () => false
    const run = paths.length === 1
      ? runUploadAndWatch(paths[0], { onProgress, shouldAbort })
      : runBatchUploadAndWatch(paths[0], paths.slice(1), { onProgress, shouldAbort })

    run
      .then((result) => this.handleHomeRecognitionResult(result))
      .catch((err) => this.handleHomeRecognitionError(err))
  },

  applyRecognizeProgress: function (progress) {
    if (!progress) return
    const rowCount = progress.rowCount || this.data.rowCount || 0
    let recognizeStatus = homeText('recognizing')
    if (progress.status === 'uploading') {
      recognizeStatus = progress.uploadLabel || t('recognizing.statusUploading')
    } else if (progress.status === 'processing') {
      if (rowCount > 0) {
        recognizeStatus = t('recognizing.statusProcessingRows', { count: rowCount })
      } else {
        recognizeStatus = t('recognizing.statusProcessingEarly')
      }
    } else if (progress.status === 'processed') {
      recognizeStatus = t('recognizing.statusProcessed')
    }
    this.setData({
      recognizeStatus,
      rowCount,
      progressRowsText: rowCount > 0 ? homeText('progressRows', { count: rowCount }) : ''
    })
  },

  handleHomeRecognitionResult: function (result) {
    if (!result || result.aborted) {
      this.setData({ isRecognizing: false })
      this.refreshTexts()
      return
    }
    const taskId = result.taskId || (result.task && (result.task.taskId || result.task.id))
    if (taskId) {
      markTaskDataDirty()
      this.setData({ imageList: [], isRecognizing: false, rowCount: 0 })
      this.refreshTexts()
      tt.navigateTo({ url: `/pages/result/index?id=${taskId}` })
      return
    }
    this.setData({ isRecognizing: false })
    this.refreshTexts()
  },

  handleHomeRecognitionError: function (err) {
    this.setData({ isRecognizing: false })
    this.refreshTexts()
    const message = translateErrorMessage(err, t('home.recognizeError'))
    tt.showModal({
      title: t('home.recognizeFail'),
      content: message,
      showCancel: false,
      confirmText: t('recognizing.errorConfirm')
    })
  },

  loadTasks: function () {
    if (!App.globalData.baseUrl || !App.globalData.token) return

    fetchTaskSummary().then((summary) => {
      this._taskSummary = summary
      this._taskDataVersion = getApp().globalData.taskDataVersion || 0
      this.setData({ statusSummary: toStatusSummary(summary) }, () => this.refreshTexts())
    })

    taskApi.getTaskList({ current: 1, size: 3 })
      .then((body) => {
        if (!isApiSuccess(body)) return
        const page = getApiData(body) || {}
        const recent = mapTaskList(page.records || [])
        this._todayTaskCount = recent.filter((task) => this.isToday(task.createTime)).length
        this.setData({ recentTasks: recent }, () => this.refreshTexts())
      })
      .catch((error) => {
        console.error('加载近期任务失败:', error)
      })
  },

  isToday: function (time) {
    if (!time) return false
    const d = new Date(time)
    if (Number.isNaN(d.getTime())) return false
    const now = new Date()
    return d.getFullYear() === now.getFullYear()
      && d.getMonth() === now.getMonth()
      && d.getDate() === now.getDate()
  },

  goToTasks: function (e) {
    const tab = (e && e.currentTarget && e.currentTarget.dataset.tab) || 'all'
    try {
      const app = getApp()
      app.globalData.tasksInitialTab = tab
    } catch (err) {
      // ignore
    }
    tt.switchTab({ url: '/pages/tasks/index' })
  },

  goToTask: function (e) {
    const taskId = e.currentTarget.dataset.id
    const status = e.currentTarget.dataset.status
    if (!taskId) return
    if (status === 'processing') {
      if (this.data.isRecognizing) return
      this.setData({
        isRecognizing: true,
        recognizeStatus: homeText('recognizing'),
        rowCount: 0,
        progressRowsText: ''
      })
      this.refreshTexts()
      pollTaskUntilDone(taskId, {
        onProgress: (progress) => this.applyRecognizeProgress(progress),
        shouldAbort: () => false
      })
        .then((result) => this.handleHomeRecognitionResult(Object.assign({ taskId }, result || {})))
        .catch((err) => this.handleHomeRecognitionError(err))
      return
    }
    tt.navigateTo({ url: `/pages/result/index?id=${taskId}` })
  }
})
