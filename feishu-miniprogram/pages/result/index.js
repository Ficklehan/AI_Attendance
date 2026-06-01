const App = getApp()
const { markTaskDataDirty } = require('../../utils/taskSummary')
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { t } = require('../../utils/i18n')
const { getCountry } = require('../../utils/preferences')
const { taskApi } = require('../../utils/api')
const {
  parseRecords,
  buildDisplayRecords,
  mapTaskDetail
} = require('../../utils/task')
const { calculateRecordStats, hasRequiredMissing } = require('../../utils/recordDisplay')
const { formatRecognitionEngine } = require('../../utils/engineLabel')
const { getCountryLabel } = require('../../utils/preferences')
const {
  buildTaskImageList,
  openImagePreview
} = require('../../utils/imageUrl')
const {
  fetchAndApplyDuplicates,
  confirmRecordNotDuplicate
} = require('../../utils/duplicateCheck')

const PAGE_SIZE = 20
const SYNC_POLL_MS = 3000

Page({
  data: {
    taskId: '',
    loading: true,
    isSubmitting: false,
    isRetryingSync: false,
    taskStatus: '',
    syncStatus: 'none',
    syncError: '',
    syncBannerText: '',
    syncBannerType: '',
    showSyncBanner: false,
    canSubmit: false,
    canRetrySync: false,
    canCalibrate: false,
    pageSize: PAGE_SIZE,
    visibleCount: PAGE_SIZE,
    hasMore: false,
    loadingMore: false,
    recognitionEngine: '',
    recognitionEngineLabel: '',
    promptCountryLabel: '',
    taskInfo: {
      name: '',
      statusText: '',
      statusTag: 'tag-default',
      timeText: '',
      originalImage: '',
      imageCount: 0
    },
    imageList: [],
    displayImageRows: [],
    imagesExpanded: false,
    imagesLoading: false,
    records: [],
    displayRecords: [],
    issueRecords: [],
    stats: {
      total: 0,
      normal: 0,
      handwritten: 0,
      blurred: 0,
      night: 0,
      absent: 0,
      deleted: 0
    },
    recordsExpanded: false,
    issueCount: 0,
    submitCtaLabel: '',
    showConfirmSheet: false,
    showCompletion: false,
    duplicateScope: 'confirmed_only',
    duplicateScopeConfirmedActive: true,
    duplicateScopeAllActive: false,
    duplicateCount: 0,
    duplicateBannerText: '',
    expandedDuplicateKeys: [],
    expandedCalibrationKeys: [],
    duplicateRefreshing: false,
    texts: {}
  },

  onLoad: function (options) {
    this.refreshPageTexts()
    this.refreshUserPermissions()
    if (options && options.id) {
      this.setData({ taskId: options.id })
      this.loadTaskResult()
    } else {
      this.setData({ loading: false })
      tt.showToast({ title: t('result.missingTaskId'), icon: 'none' })
    }
  },

  onShow: function () {
    this.refreshPageTexts()
  },

  onUnload: function () {
    this.clearSyncPoll()
  },

  refreshPageTexts: function () {
    this.setData({
      texts: {
        retrySync: t('result.retrySync'),
        retryingSync: t('result.retryingSync'),
        confirmTitle: t('result.confirmTitle'),
        confirmContent: t('result.confirmContent'),
        confirmSubmit: t('result.confirmSubmit'),
        sheetCancel: t('result.sheetCancel'),
        completionTitle: t('result.completionTitle'),
        completionDesc: t('result.completionDesc'),
        completionTasks: t('result.completionTasks'),
        completionStay: t('result.completionStay'),
        expandRecords: t('result.expandRecords'),
        collapseRecords: t('result.collapseRecords'),
        viewTasks: t('result.viewTasks'),
        issuesTitle: t('result.issuesTitle'),
        statsAll: t('result.statsAll'),
        statsNormal: t('result.statsNormal'),
        statsBlurred: t('result.statsBlurred'),
        statsAbsent: t('result.statsAbsent'),
        recordsTitle: t('result.recordsTitle'),
        originalImagesTitle: t('result.originalImagesTitle'),
        viewImage: t('result.viewImage'),
        expandImages: t('result.expandImages'),
        collapseImages: t('result.collapseImages'),
        loadingImage: t('result.loadingImage'),
        noImages: t('result.noImages'),
        viewAllIssues: t('result.viewAllIssues'),
        allClearTitle: t('result.allClearTitle'),
        allClearDesc: t('result.allClearDesc'),
        anomalyLabel: t('result.anomalyLabel'),
        anomalyMorePrefix: t('result.anomalyMorePrefix'),
        issueUnit: t('result.issueUnit'),
        back: t('result.back'),
        duplicateScopeLabel: t('result.duplicateScopeLabel'),
        duplicateScopeConfirmed: t('result.duplicateScopeConfirmed'),
        duplicateScopeAll: t('result.duplicateScopeAll'),
        duplicateBanner: t('result.duplicateBanner'),
        duplicateTag: t('result.duplicateTag'),
        duplicateExpand: t('result.duplicateExpand'),
        duplicateCollapse: t('result.duplicateCollapse'),
        duplicateNotDuplicate: t('result.duplicateNotDuplicate'),
        duplicateDetailTitle: t('result.duplicateDetailTitle'),
        duplicateSourceTask: t('result.duplicateSourceTask'),
        duplicateColNo: t('result.duplicateColNo'),
        duplicateColName: t('result.duplicateColName'),
        calibrate: t('result.calibrate'),
        editRecord: t('result.editRecord'),
        calibrationManualTag: t('calibration.manualTag'),
        calibrationExpand: t('calibration.viewHistory'),
        calibrationCollapse: t('result.duplicateCollapse'),
        calibrationDetailTitle: t('calibration.historyTitle')
      },
      submitCtaLabel: t('result.confirmSubmit')
    })
  },

  openRecordEdit: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    if (!rowKey) return
    if (!this.data.canSubmit) {
      tt.showToast({ title: t('recordEdit.notProcessed'), icon: 'none' })
      return
    }
    tt.navigateTo({
      url: `/pages/record-edit/index?taskId=${encodeURIComponent(this.data.taskId)}&rowKey=${encodeURIComponent(rowKey)}`
    })
  },

  applyRecordDraft: function (rowKey, draft) {
    if (!rowKey || !draft) return
    const records = (this.data.records || []).map((r) => {
      if (r._rowKey !== rowKey) return r
      return { ...r, ...draft }
    })
    this.setData({ records }, () => {
      this.refreshStats()
      this.refreshDuplicateHints()
      this.refreshDisplayRecords()
    })
  },

  openCalibrate: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    if (!rowKey) return
    if (!this.data.canCalibrate) {
      tt.showToast({ title: t('calibration.permissionDenied'), icon: 'none' })
      return
    }
    tt.navigateTo({
      url: `/pages/record-calibrate/index?taskId=${encodeURIComponent(this.data.taskId)}&rowKey=${encodeURIComponent(rowKey)}`
    })
  },

  refreshUserPermissions: function () {
    tt.request({
      url: `${App.globalData.baseUrl}/auth/profile`,
      header: {
        Authorization: App.globalData.token ? `Bearer ${App.globalData.token}` : ''
      },
      success: (res) => {
        if (isApiSuccess(res.data)) {
          const user = getApiData(res.data) || {}
          App.globalData.userInfo = user
          tt.setStorageSync('userInfo', user)
          if (this.data.taskStatus === 'confirmed') {
            const canCalibrate = user.permissions && user.permissions.recordCalibrate === true
            this.setData({ canCalibrate })
          }
        }
      }
    })
  },

  onReachBottom: function () {
    this.loadMoreRecords()
  },

  clearSyncPoll: function () {
    if (this._syncPollTimer) {
      clearInterval(this._syncPollTimer)
      this._syncPollTimer = null
    }
  },

  startSyncPoll: function () {
    this.clearSyncPoll()
    this._syncPollTimer = setInterval(() => {
      if (this.data.syncStatus !== 'pending') {
        this.clearSyncPoll()
        return
      }
      this.loadTaskResult(true)
    }, SYNC_POLL_MS)
  },

  applySyncUi: function (task) {
    const taskStatus = task.status || ''
    const syncStatus = task.syncStatus || 'none'
    const syncError = task.syncError || ''
    let syncBannerText = ''
    let syncBannerType = ''
    let showSyncBanner = false

    if (taskStatus === 'confirmed') {
      if (syncStatus === 'pending') {
        showSyncBanner = true
        syncBannerType = 'warn'
        syncBannerText = t('sync.bannerPending')
      } else if (syncStatus === 'synced') {
        showSyncBanner = true
        syncBannerType = 'ok'
        syncBannerText = t('sync.bannerSynced')
      } else if (syncStatus === 'sync_failed') {
        showSyncBanner = true
        syncBannerType = 'err'
        syncBannerText = syncError
          ? `${t('sync.bannerFailed')}\n${syncError}`
          : t('sync.bannerFailed')
      }
    }

    const canSubmit = taskStatus === 'processed'
    const canRetrySync = taskStatus === 'confirmed' && syncStatus === 'sync_failed'
    const perms = (App.globalData.userInfo && App.globalData.userInfo.permissions) || {}
    const canCalibrate = taskStatus === 'confirmed' && perms.recordCalibrate === true

    this.setData({
      taskStatus,
      syncStatus,
      syncError,
      syncBannerText,
      syncBannerType,
      showSyncBanner,
      canSubmit,
      canRetrySync,
      canCalibrate
    })

    if (syncStatus === 'pending') {
      this.startSyncPoll()
    } else {
      this.clearSyncPoll()
    }

    this.refreshDisplayRecords()
  },

  loadTaskResult: function (silent) {
    if (!silent) {
      this.setData({ loading: true, visibleCount: PAGE_SIZE })
    }
    tt.request({
      url: `${App.globalData.baseUrl}/tasks/${this.data.taskId}`,
      header: {
        Authorization: App.globalData.token ? `Bearer ${App.globalData.token}` : ''
      },
      success: (res) => {
        if (isApiSuccess(res.data)) {
          const task = getApiData(res.data) || {}
          const payload = task.status === 'confirmed' && task.confirmedData
            ? task.confirmedData
            : (task.rawData || task.confirmedData)
          const records = parseRecords(payload)
          const engine = task.aiRawOutput || ''
          const promptCountry = engine.indexOf('mimo:') === 0 ? engine.slice(5) : (task.promptCountry || '')
          const imageList = buildTaskImageList(task)
          this.setData({
            taskInfo: mapTaskDetail(task),
            records,
            imageList: [],
            imagesLoading: imageList.length > 0,
            recognitionEngine: engine,
            recognitionEngineLabel: formatRecognitionEngine(engine, promptCountry),
            promptCountryLabel: promptCountry ? getCountryLabel(promptCountry) : ''
          })
          this.loadTaskImages(imageList)
          this.applySyncUi(task)
          this.refreshDuplicateHints()
          this.refreshStats()
          if (!silent && records.length === 0 && task.status === 'processed') {
            tt.showModal({
              title: '无识别结果',
              content: '任务已完成但未解析到记录，请换更清晰照片重试，或查看 PC 端同图是否正常。',
              showCancel: false
            })
          }
        } else if (!silent) {
          tt.showToast({ title: getApiMessage(res.data, t('result.loadFail')), icon: 'none' })
        }
      },
      fail: (error) => {
        console.error('加载任务失败:', error)
        if (!silent) {
          tt.showToast({ title: t('result.loadFail'), icon: 'none' })
        }
      },
      complete: () => {
        if (!silent) {
          this.setData({ loading: false })
        }
      }
    })
  },

  refreshDuplicateHints: function () {
    const { records, taskId, duplicateScope, canSubmit } = this.data
    if (!canSubmit || !records.length || !taskId) {
      this.setData({
        duplicateCount: 0,
        duplicateBannerText: ''
      }, () => this.refreshDisplayRecords())
      return
    }
    if (this.data.duplicateRefreshing) {
      return
    }
    this.setData({ duplicateRefreshing: true })
    const recordsCopy = records.map((r) => ({ ...r }))
    fetchAndApplyDuplicates(taskId, recordsCopy, duplicateScope)
      .then((result) => {
        this.setData({
          records: recordsCopy,
          duplicateCount: result.duplicateCount || 0,
          duplicateBannerText: (result.duplicateCount || 0) > 0
            ? t('result.duplicateBanner', { count: result.duplicateCount })
            : ''
        }, () => this.refreshDisplayRecords())
      })
      .finally(() => {
        this.setData({ duplicateRefreshing: false })
      })
  },

  onDuplicateScopeChange: function (e) {
    const scope = e.currentTarget.dataset.scope
    if (!scope || scope === this.data.duplicateScope) {
      return
    }
    this.setData({
      duplicateScope: scope,
      duplicateScopeConfirmedActive: scope === 'confirmed_only',
      duplicateScopeAllActive: scope === 'confirmed_and_processing',
      expandedDuplicateKeys: []
    }, () => {
      this.refreshDuplicateHints()
    })
  },

  toggleDuplicateExpand: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    if (!rowKey) return
    const keys = this.data.expandedDuplicateKeys || []
    const next = keys.indexOf(rowKey) !== -1
      ? keys.filter((k) => k !== rowKey)
      : keys.concat(rowKey)
    this.setData({ expandedDuplicateKeys: next }, () => this.refreshDisplayRecords())
  },

  confirmNotDuplicate: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    const records = this.data.records.map((r) => ({ ...r }))
    const record = records.find((r) => r._rowKey === rowKey)
    if (!record) return
    confirmRecordNotDuplicate(record)
    this.setData({ records }, () => this.refreshDuplicateHints())
  },

  attachDuplicateUi: function (rows) {
    const dupExpanded = this.data.expandedDuplicateKeys || []
    const calExpanded = this.data.expandedCalibrationKeys || []
    return (rows || []).map((row) => ({
      ...row,
      duplicateExpanded: dupExpanded.indexOf(row._rowKey) !== -1,
      calibrationExpanded: calExpanded.indexOf(row._rowKey) !== -1,
      duplicateMemberPreview: (row.duplicateMembers || []).slice(0, 4)
    }))
  },

  toggleCalibrationExpand: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    if (!rowKey) return
    const keys = this.data.expandedCalibrationKeys || []
    const idx = keys.indexOf(rowKey)
    const next = idx === -1 ? keys.concat(rowKey) : keys.filter((k) => k !== rowKey)
    this.setData({ expandedCalibrationKeys: next }, () => this.refreshDisplayRecords())
  },

  refreshDisplayRecords: function () {
    const { records, visibleCount, recordsExpanded, canSubmit } = this.data
    const allBuilt = this.attachDuplicateUi(buildDisplayRecords(records, records.length))
    const allIssues = allBuilt.filter((row) => row.hasAnomaly && !row.isDeleted)
    const issueRecords = allIssues.slice(0, 8)
    const issueCount = allIssues.length
    let submitCtaLabel = t('result.confirmSubmit')
    if (canSubmit && issueCount > 0) {
      submitCtaLabel = t('result.confirmSubmitWithIssues', { count: issueCount })
    }
    if (recordsExpanded) {
      const displayRecords = this.attachDuplicateUi(buildDisplayRecords(records, visibleCount))
      this.setData({
        displayRecords,
        issueRecords,
        issueCount,
        submitCtaLabel,
        hasMore: visibleCount < records.length
      })
      return
    }
    if (issueCount > 0) {
      this.setData({
        displayRecords: [],
        issueRecords,
        issueCount,
        submitCtaLabel,
        hasMore: false
      })
      return
    }
    const preview = allBuilt.slice(0, Math.min(5, allBuilt.length))
    this.setData({
      displayRecords: preview,
      issueRecords: [],
      issueCount: 0,
      submitCtaLabel,
      hasMore: preview.length < allBuilt.length
    })
  },

  toggleRecordsExpanded: function () {
    const recordsExpanded = !this.data.recordsExpanded
    this.setData({ recordsExpanded, visibleCount: PAGE_SIZE }, () => {
      this.refreshDisplayRecords()
    })
  },

  refreshStats: function () {
    this.setData({
      stats: calculateRecordStats(this.data.records)
    })
  },

  loadMoreRecords: function () {
    const { hasMore, loadingMore, records, visibleCount, pageSize } = this.data
    if (!hasMore || loadingMore || records.length === 0) {
      return
    }
    this.setData({ loadingMore: true })
    const next = Math.min(visibleCount + pageSize, records.length)
    this.setData({ visibleCount: next }, () => {
      this.refreshDisplayRecords()
      this.setData({ loadingMore: false })
    })
  },

  scrollToList: function () {
    tt.pageScrollTo({ selector: '#record-list', duration: 300 })
  },

  refreshVisibleImages: function () {
    const list = this.data.imageList || []
    const expanded = this.data.imagesExpanded
    const displayImageRows = (!expanded && list.length > 1) ? [list[0]] : list
    this.setData({ displayImageRows })
  },

  toggleImagesExpanded: function () {
    this.setData({ imagesExpanded: !this.data.imagesExpanded }, () => {
      this.refreshVisibleImages()
    })
  },

  loadTaskImages: function (imageList) {
    if (!imageList || !imageList.length) {
      this.setData({
        imageList: [],
        displayImageRows: [],
        imagesExpanded: false,
        imagesLoading: false
      })
      return
    }
    const prepared = imageList.map((item, index) => ({
      ...item,
      key: item.key || `img-${index}`,
      fileName: item.fileName || item.key,
      listIndex: index
    }))
    const taskInfo = {
      ...this.data.taskInfo,
      originalImage: prepared[0] ? prepared[0].url : '',
      imageCount: prepared.length
    }
    this.setData({
      imageList: prepared,
      imagesExpanded: false,
      imagesLoading: false,
      taskInfo
    }, () => this.refreshVisibleImages())
  },

  previewImage: function () {
    this.previewImageAt({ currentTarget: { dataset: { index: 0 } } })
  },

  previewImageAt: function (e) {
    const index = Number(e.currentTarget.dataset.index) || 0
    const list = this.data.imageList || []
    if (!list.length) {
      tt.showToast({ title: this.data.texts.noImages, icon: 'none' })
      return
    }
    tt.showLoading({ title: this.data.texts.loadingImage, mask: true })
    openImagePreview(list, index)
      .catch((err) => {
        console.warn('preview failed', err)
        tt.showToast({ title: this.data.texts.noImages, icon: 'none' })
      })
      .finally(() => {
        try {
          tt.hideLoading()
        } catch (e) {
          // ignore
        }
      })
  },

  goBack: function () {
    tt.navigateBack()
  },

  noop: function () {},

  closeConfirmSheet: function () {
    this.setData({ showConfirmSheet: false })
  },

  confirmSubmit: function () {
    this.setData({ showConfirmSheet: true })
  },

  confirmSheetSubmit: function () {
    this.setData({ showConfirmSheet: false })
    if (this.data.duplicateCount > 0) {
      tt.showModal({
        title: this.data.texts.duplicateTag,
        content: this.data.duplicateBannerText,
        confirmText: this.data.texts.confirmSubmit,
        cancelText: this.data.texts.sheetCancel,
        success: (res) => {
          if (res.confirm) {
            this.submitToFeishu()
          }
        }
      })
      return
    }
    this.submitToFeishu()
  },

  goTasksFromCompletion: function () {
    this.setData({ showCompletion: false })
    tt.switchTab({ url: '/pages/tasks/index' })
  },

  goTasks: function () {
    tt.switchTab({ url: '/pages/tasks/index' })
  },

  dismissCompletion: function () {
    this.setData({ showCompletion: false })
  },

  submitToFeishu: function () {
    const incompleteCount = (this.data.records || []).filter(hasRequiredMissing).length
    if (incompleteCount > 0) {
      tt.showToast({
        title: t('result.requiredFieldsMissing', { count: incompleteCount }),
        icon: 'none',
        duration: 3000
      })
      return
    }

    this.setData({ isSubmitting: true })

    tt.request({
      url: `${App.globalData.baseUrl}/tasks/${this.data.taskId}/confirm`,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        Authorization: App.globalData.token ? `Bearer ${App.globalData.token}` : '',
        'X-Country': getCountry()
      },
      data: {
        data: this.data.records
      },
      success: (res) => {
        if (isApiSuccess(res.data)) {
          markTaskDataDirty()
          this.setData({
            showCompletion: true,
            taskStatus: 'confirmed',
            syncStatus: 'pending',
            canSubmit: false,
            canRetrySync: false,
            showSyncBanner: true,
            syncBannerType: 'warn',
            syncBannerText: t('sync.bannerPending')
          })
          this.startSyncPoll()
        } else {
          tt.showToast({ title: getApiMessage(res.data, t('result.submitFail')), icon: 'none' })
        }
      },
      fail: (error) => {
        console.error('提交失败:', error)
        tt.showToast({ title: t('result.submitFail'), icon: 'none' })
      },
      complete: () => {
        this.setData({ isSubmitting: false })
      }
    })
  },

  retrySync: function () {
    if (this.data.isRetryingSync || !this.data.canRetrySync) {
      return
    }
    this.setData({ isRetryingSync: true })
    taskApi.retryFeishuSync(this.data.taskId).then((res) => {
      if (res && isApiSuccess(res)) {
        tt.showToast({ title: t('result.retrySyncSuccess'), icon: 'success' })
        this.setData({
          syncStatus: 'pending',
          canRetrySync: false,
          showSyncBanner: true,
          syncBannerType: 'warn',
          syncBannerText: t('sync.bannerPending')
        })
        this.startSyncPoll()
        this.loadTaskResult(true)
      } else {
        tt.showToast({ title: getApiMessage(res, t('result.retrySyncFail')), icon: 'none' })
      }
    }).catch((err) => {
      console.error('重试同步失败', err)
      tt.showToast({ title: t('result.retrySyncFail'), icon: 'none' })
    }).finally(() => {
      this.setData({ isRetryingSync: false })
    })
  }
})
