const App = getApp()
const { markTaskDataDirty } = require('../../utils/taskSummary')
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { t } = require('../../utils/i18n')
const { taskApi } = require('../../utils/api')
const { apiCall } = require('../../utils/request')
const {
  parseRecords,
  buildDisplayRecords,
  mapTaskDetail
} = require('../../utils/task')
const { calculateRecordStats } = require('../../utils/recordDisplay')
const {
  countRequiredMissing,
  showRequiredValidationModal,
} = require('../../utils/confirmValidationDisplay')
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
const { ensureFeishuLogin } = require('../../utils/feishuLogin')
const { setPendingReturn, buildResultPath } = require('../../utils/deepLink')
const { refreshRecordNightShiftMark } = require('../../utils/recognitionLabels')
const { loadNightShiftRules } = require('../../utils/nightShiftRules')
const { loadConfirmValidationConfig } = require('../../utils/confirmValidationConfig')
const { parseImageQualityWarning } = require('../../utils/recognitionUpload')
const { resolveTaskRecordsJson } = require('../../shared-js/taskRecordPayload')

const PAGE_SIZE = 20
const { startAdaptivePoll } = require('../../utils/adaptivePoll')

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
    expandedAnomalyKeys: [],
    duplicateRefreshing: false,
    requiredMissingCount: 0,
    requiredValidationBannerText: '',
    scrollIntoViewId: '',
    flashRowKey: '',
    showImageQualityBanner: false,
    imageQualityBannerText: '',
    texts: {}
  },

  onLoad: function (options) {
    this.refreshPageTexts()
    loadNightShiftRules().catch(() => {})
    if (options && options.id) {
      const patch = { taskId: options.id }
      if (options.qualityWarn === '1') {
        patch.showImageQualityBanner = true
        patch.imageQualityBannerText = t('upload.imageQualityWarning', {
          blurPercent: Number(options.blurPercent) || 0,
          unknownPercent: Number(options.unknownPercent) || 0
        })
      }
      this.setData(patch)
      this.bootstrapTaskPage()
    } else {
      this.setData({ loading: false })
      tt.showToast({ title: t('result.missingTaskId'), icon: 'none' })
    }
  },

  onShow: function () {
    this.refreshPageTexts()
    if ((this.data.records || []).length) {
      this.refreshDisplayRecords()
    }
  },

  onUnload: function () {
    this.clearSyncPoll()
  },

  refreshPageTexts: function () {
    try {
      tt.setNavigationBarTitle({ title: t('result.title') })
    } catch (e) {
      console.warn('setNavigationBarTitle failed', e)
    }
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
        anomalyExpand: t('result.anomalyExpand'),
        anomalyCollapse: t('result.anomalyCollapse'),
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
        requiredValidationViewDetail: t('result.requiredValidationViewDetail'),
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
      const updated = { ...r, ...draft }
      refreshRecordNightShiftMark(updated)
      return updated
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

  bootstrapTaskPage: function (forceLogin, silent) {
    ensureFeishuLogin({ silent: true, force: Boolean(forceLogin) })
      .then(() => {
        this.refreshUserPermissions()
        return loadConfirmValidationConfig()
      })
      .then(() => this.loadTaskResult(Boolean(silent)))
      .catch(() => {
        const taskId = this.data.taskId
        if (taskId) {
          setPendingReturn(buildResultPath(taskId))
        }
        tt.redirectTo({
          url: taskId
            ? `/pages/login/index?taskId=${encodeURIComponent(taskId)}&auto=1`
            : '/pages/login/index?auto=1'
        })
      })
  },

  refreshUserPermissions: function () {
    apiCall({ url: '/auth/profile' })
      .then((res) => {
        if (isApiSuccess(res.data)) {
          const user = getApiData(res.data) || {}
          App.globalData.userInfo = user
          tt.setStorageSync('userInfo', user)
          if (this.data.taskStatus === 'confirmed') {
            const canCalibrate = user.permissions && user.permissions.recordCalibrate === true
            this.setData({ canCalibrate })
          }
        }
      })
      .catch(() => {})
  },

  onReachBottom: function () {
    this.loadMoreRecords()
  },

  clearSyncPoll: function () {
    if (this._stopSyncPoll) {
      this._stopSyncPoll()
      this._stopSyncPoll = null
    }
  },

  refreshSyncStatusOnly: function () {
    const prev = this.data.syncStatus
    return taskApi.getTaskProgress(this.data.taskId)
      .then((res) => {
        if (!isApiSuccess(res.data)) return
        const p = getApiData(res.data) || {}
        const syncStatus = p.syncStatus || 'none'
        const syncError = p.syncError || ''
        if (prev === 'pending' && syncStatus !== 'pending') {
          return this.loadTaskResult(true)
        }
        this.applySyncUi({
          status: p.status || this.data.taskStatus,
          syncStatus,
          syncError,
        }, { skipRecordRefresh: true, skipPollRestart: true })
      })
  },

  startSyncPoll: function () {
    this.clearSyncPoll()
    this._stopSyncPoll = startAdaptivePoll({
      intervalMs: 4000,
      shouldContinue: () => this.data.syncStatus === 'pending',
      isPaused: () => false,
      tick: () => {
        if (this.data.syncStatus !== 'pending') {
          this.clearSyncPoll()
          return Promise.resolve()
        }
        return this.refreshSyncStatusOnly()
      }
    })
  },

  applySyncUi: function (task, options) {
    const skipRecordRefresh = options && options.skipRecordRefresh
    const skipPollRestart = options && options.skipPollRestart
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

    const canConfirm = task.canConfirm !== false
    const canSubmit = taskStatus === 'processed' && canConfirm
    const canRetrySync = taskStatus === 'confirmed' && syncStatus === 'sync_failed' && canConfirm
    const perms = (App.globalData.userInfo && App.globalData.userInfo.permissions) || {}
    const canCalibrate = taskStatus === 'confirmed' && perms.recordCalibrate === true && canConfirm

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

    if (!skipPollRestart) {
      if (syncStatus === 'pending') {
        this.startSyncPoll()
      } else {
        this.clearSyncPoll()
      }
    }

    if (!skipRecordRefresh) {
      this.refreshDisplayRecords()
    }
  },

  loadTaskResult: function (silent) {
    if (!silent) {
      this.setData({ loading: true, visibleCount: PAGE_SIZE })
    }
    apiCall({ url: `/tasks/${this.data.taskId}` })
      .then((res) => {
        if (isApiSuccess(res.data)) {
          const task = getApiData(res.data) || {}
          const payload = resolveTaskRecordsJson(task)
          const records = parseRecords(payload)
          const engine = task.aiRawOutput || ''
          const promptCountry = engine.indexOf('mimo:') === 0 ? engine.slice(5) : (task.promptCountry || '')
          const imageListPromise = buildTaskImageList(task)
          imageListPromise.then((imageList) => {
            const qualityPatch = {}
            if (!this.data.showImageQualityBanner) {
              const warning = parseImageQualityWarning(task)
              if (warning) {
                qualityPatch.showImageQualityBanner = true
                qualityPatch.imageQualityBannerText = t('upload.imageQualityWarning', warning)
              }
            }
            this.setData({
              taskInfo: mapTaskDetail(task, imageList),
              records,
              imageList: [],
              imagesLoading: imageList.length > 0,
              recognitionEngine: engine,
              recognitionEngineLabel: formatRecognitionEngine(engine, promptCountry),
              promptCountryLabel: promptCountry ? getCountryLabel(promptCountry) : '',
              ...qualityPatch
            })
            this.loadTaskImages(imageList)
            this.applySyncUi(task)
            this.refreshDuplicateHints()
            this.refreshStats()
            if (!silent && records.length === 0 && task.status === 'processed') {
              tt.showModal({
                title: t('result.noRecordsModalTitle'),
                content: t('result.noRecordsModalContent'),
                showCancel: false
              })
            }
          })
          return
        } else {
          const code = res.data && Number(res.data.code)
          if ((code === 401 || code === 1004) && !this._authRetried) {
            this._authRetried = true
            this.bootstrapTaskPage(true, silent)
            return
          }
          if (!silent) {
            tt.showToast({ title: getApiMessage(res.data, t('result.loadFail')), icon: 'none' })
          }
        }
      })
      .catch((error) => {
        console.error('加载任务失败:', error)
        if (!silent) {
          tt.showToast({ title: t('result.loadFail'), icon: 'none' })
        }
      })
      .then(() => {
        if (!silent) {
          this.setData({ loading: false })
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
    const anomalyExpanded = this.data.expandedAnomalyKeys || []
    return (rows || []).map((row) => ({
      ...row,
      duplicateExpanded: dupExpanded.indexOf(row._rowKey) !== -1,
      calibrationExpanded: calExpanded.indexOf(row._rowKey) !== -1,
      anomalyExpanded: anomalyExpanded.indexOf(row._rowKey) !== -1,
      duplicateMemberPreview: (row.duplicateMembers || []).slice(0, 4)
    }))
  },

  toggleAnomalyExpand: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    if (!rowKey) return
    const keys = this.data.expandedAnomalyKeys || []
    const idx = keys.indexOf(rowKey)
    const next = idx === -1 ? keys.concat(rowKey) : keys.filter((k) => k !== rowKey)
    this.setData({ expandedAnomalyKeys: next }, () => this.refreshDisplayRecords())
  },

  toggleCalibrationExpand: function (e) {
    const rowKey = e.currentTarget.dataset.rowKey
    if (!rowKey) return
    const keys = this.data.expandedCalibrationKeys || []
    const idx = keys.indexOf(rowKey)
    const next = idx === -1 ? keys.concat(rowKey) : keys.filter((k) => k !== rowKey)
    this.setData({ expandedCalibrationKeys: next }, () => this.refreshDisplayRecords())
  },

  refreshRequiredValidation: function () {
    const { records, canSubmit } = this.data
    const requiredMissingCount = canSubmit ? countRequiredMissing(records) : 0
    this.setData({
      requiredMissingCount,
      requiredValidationBannerText: requiredMissingCount > 0
        ? t('result.requiredValidationBanner', { count: requiredMissingCount })
        : '',
    })
  },

  showRequiredValidationDetail: function () {
    const self = this
    showRequiredValidationModal(this.data.records, t, {
      beforeModal: function (issue) {
        if (issue && issue.rowKey) {
          self.scrollToValidationIssue(issue)
        }
      },
    })
  },

  scrollToValidationIssue: function (issue) {
    if (!issue || !issue.rowKey) return
    const line = issue.line || 1
    const visibleCount = Math.max(this.data.visibleCount, line)
    this.setData({
      recordsExpanded: true,
      visibleCount,
      scrollIntoViewId: `record-${issue.rowKey}`,
      flashRowKey: issue.rowKey,
    }, () => {
      this.refreshDisplayRecords()
      setTimeout(() => {
        this.setData({ flashRowKey: '', scrollIntoViewId: '' })
      }, 2200)
    })
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
    const finish = (payload) => {
      this.setData(payload, () => {
        this.refreshRequiredValidation()
        this.refreshPageTexts()
      })
    }
    if (recordsExpanded) {
      const displayRecords = this.attachDuplicateUi(buildDisplayRecords(records, visibleCount))
      finish({
        displayRecords,
        issueRecords,
        issueCount,
        submitCtaLabel,
        hasMore: visibleCount < records.length
      })
      return
    }
    if (issueCount > 0) {
      finish({
        displayRecords: [],
        issueRecords,
        issueCount,
        submitCtaLabel,
        hasMore: false
      })
      return
    }
    const preview = allBuilt.slice(0, Math.min(5, allBuilt.length))
    finish({
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
    if (showRequiredValidationModal(this.data.records, t)) {
      return
    }

    this.setData({ isSubmitting: true })

    const records = (this.data.records || []).map((r) => {
      const copy = { ...r }
      refreshRecordNightShiftMark(copy)
      return copy
    })

    apiCall({
      url: `/tasks/${this.data.taskId}/confirm`,
      method: 'POST',
      data: { data: records }
    })
      .then((res) => {
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
      })
      .catch((error) => {
        console.error('提交失败:', error)
        tt.showToast({ title: t('result.submitFail'), icon: 'none' })
      })
      .then(() => {
        this.setData({ isSubmitting: false })
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
