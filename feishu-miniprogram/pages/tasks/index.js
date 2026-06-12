const App = getApp()
const { isApiSuccess, getApiData } = require('../../utils/response')
const { mapTaskList, tabStatusParam, filterTasksByTab, normalizeTasksTab } = require('../../utils/task')
const {
  fetchTaskSummary,
  fetchFirstReviewTask,
  tabCountFromSummary,
  markTaskDataDirty,
  shouldReloadTaskData
} = require('../../utils/taskSummary')
const { taskApi } = require('../../utils/api')
const { t } = require('../../utils/i18n')

const SWIPE_ACTION_RPX = 150
let cachedSwipeOpenPx = null

function getSwipeOpenPx() {
  if (cachedSwipeOpenPx != null) return cachedSwipeOpenPx
  try {
    const { windowWidth } = tt.getSystemInfoSync()
    cachedSwipeOpenPx = -Math.round((SWIPE_ACTION_RPX / 750) * windowWidth)
  } catch (e) {
    cachedSwipeOpenPx = -80
  }
  return cachedSwipeOpenPx
}

function reviewCtaLabel(count) {
  const key = 'home.primaryCtaReview'
  const value = t(key, { count })
  if (value && value !== key) return value
  return `去核对 (${count})`
}

function decorateTaskRows(list, options = {}) {
  const { resetSelect = false } = options
  return (list || []).map((item) => ({
    ...item,
    selected: resetSelect ? false : !!item.selected
  }))
}

Page({
  data: {
    currentTab: 'all',
    tabs: [],
    tasks: [],
    loading: true,
    loadError: false,
    hasMore: true,
    page: 1,
    pageSize: 10,
    headerHint: '',
    reviewCount: 0,
    showReviewDock: false,
    primaryCtaLabel: '',
    keyword: '',
    selectionMode: false,
    selectedCount: 0,
    swipeDragging: false,
    swipeOpenPx: getSwipeOpenPx(),
    openedTaskId: '',
    dragTaskId: '',
    dragOffsetX: 0,
    texts: {}
  },

  onLoad: function () {
    this._taskDataVersion = getApp().globalData.taskDataVersion || 0
    this.refreshTexts()
    this.applyInitialTab()
    this.loadSummary()
    this.loadTasks()
  },

  onShow: function () {
    this.refreshTexts()
    if (this.data.selectionMode) {
      this.exitSelectionMode()
    }
    const tabChanged = this.applyInitialTab()
    if (tabChanged || shouldReloadTaskData(this)) {
      this.loadSummary()
      this.loadTasks()
    } else {
      this.loadSummary()
    }
  },

  applyInitialTab: function () {
    let tab = null
    try {
      const app = getApp()
      tab = app.globalData.tasksInitialTab
      if (tab) {
        app.globalData.tasksInitialTab = null
      }
    } catch (e) {
      tab = null
    }
    if (!tab) return false
    const normalized = normalizeTasksTab(tab)
    if (normalized === this.data.currentTab) return false
    this.setData({
      currentTab: normalized,
      tasks: [],
      page: 1,
      hasMore: true,
      headerHint: this.buildHeaderHint(0),
      selectionMode: false,
      selectedCount: 0
    })
    return true
  },

  refreshTexts: function () {
    const reviewCount = this.data.reviewCount || 0
    const selectedCount = this.data.selectedCount || 0
    this.setData({
      texts: {
        title: t('tasks.title'),
        loading: t('tasks.loading'),
        emptyTitle: t('tasks.emptyTitle'),
        emptyDesc: t('tasks.emptyDesc'),
        startRecognize: t('home.primaryCtaScan') || t('tasks.startRecognize'),
        loadMore: t('tasks.loadMore'),
        delete: t('tasks.delete'),
        deleteConfirmTitle: t('tasks.deleteConfirmTitle'),
        deleteConfirmContent: t('tasks.deleteConfirmContent'),
        deleteSuccess: t('tasks.deleteSuccess'),
        deleteFail: t('tasks.deleteFail'),
        deleteNotAllowed: t('tasks.deleteNotAllowed'),
        loadFailTitle: t('tasks.loadFailTitle'),
        loadFailDesc: t('tasks.loadFailDesc'),
        retry: t('tasks.retry'),
        swipeHint: t('tasks.swipeHint'),
        longPressHint: t('tasks.longPressHint'),
        search: t('common.search'),
        searchPlaceholder: t('tasks.searchPlaceholder'),
        searchEmptyTitle: t('tasks.searchEmptyTitle'),
        searchEmptyDesc: t('tasks.searchEmptyDesc'),
        cancelSelect: t('tasks.cancelSelect'),
        batchDelete: t('tasks.batchDelete', { count: selectedCount }),
        batchDeleteTitle: t('tasks.batchDeleteTitle'),
        batchDeleteContent: t('tasks.batchDeleteContent', { count: selectedCount }),
        batchDeleteSuccess: t('tasks.batchDeleteSuccess', { count: selectedCount }),
        batchDeleteFail: t('tasks.batchDeleteFail'),
        selectedHint: t('tasks.selectedHint', { count: selectedCount })
      },
      tabs: [
        { label: t('tasks.tabAll'), value: 'all' },
        { label: t('tasks.tabPending'), value: 'pending' },
        { label: t('tasks.tabReview'), value: 'review' },
        { label: t('tasks.tabCompleted'), value: 'completed' }
      ],
      headerHint: this.buildHeaderHint(this.data.tasks.length || 0),
      primaryCtaLabel: reviewCtaLabel(reviewCount),
      showReviewDock: reviewCount > 0 && !this.data.selectionMode
    })
    tt.setNavigationBarTitle({ title: t('tasks.title') })
  },

  buildHeaderHint: function (listTotal) {
    if (this.data.selectionMode) {
      return t('tasks.selectedHint', { count: this.data.selectedCount || 0 })
    }
    const tab = normalizeTasksTab(this.data.currentTab)
    const summary = this._taskSummary
    const count = summary ? tabCountFromSummary(tab, summary) : (listTotal || 0)
    if (tab === 'pending') return t('tasks.hintPending', { count })
    if (tab === 'review') return t('tasks.hintReview', { count })
    if (tab === 'completed') return t('tasks.hintCompleted', { count })
    return t('tasks.hintAll', { count })
  },

  countSelected: function (tasks) {
    return (tasks || this.data.tasks).filter((item) => item.selected).length
  },

  loadSummary: function () {
    fetchTaskSummary().then((summary) => {
      this._taskSummary = summary
      const reviewCount = summary.review || 0
      this.setData({
        reviewCount,
        showReviewDock: reviewCount > 0 && !this.data.selectionMode,
        primaryCtaLabel: reviewCtaLabel(reviewCount),
        headerHint: this.buildHeaderHint(0)
      })
    })
  },

  switchTab: function (e) {
    const tab = normalizeTasksTab(e.currentTarget.dataset.value)
    this.exitSelectionMode()
    this.setData({
      currentTab: tab,
      tasks: [],
      page: 1,
      hasMore: true,
      headerHint: this.buildHeaderHint(0)
    })
    this.loadTasks()
  },

  onSearchInput: function (e) {
    this.setData({ keyword: e.detail.value || '' })
  },

  onSearchConfirm: function () {
    this.exitSelectionMode()
    this.setData({
      tasks: [],
      page: 1,
      hasMore: true,
      loadError: false
    })
    this.loadTasks()
  },

  clearSearch: function () {
    if (!this.data.keyword) return
    this.exitSelectionMode()
    this.setData({
      keyword: '',
      tasks: [],
      page: 1,
      hasMore: true,
      loadError: false
    })
    this.loadTasks()
  },

  buildListParams: function () {
    const params = {
      current: this.data.page,
      size: this.data.pageSize,
      status: tabStatusParam(this.data.currentTab)
    }
    const kw = (this.data.keyword || '').trim()
    if (kw) {
      params.keyword = kw
    }
    return params
  },

  loadTasks: function () {
    if (this.data.loading && this.data.page > 1) return

    this.setData({ loading: true })

    taskApi.getTaskList(this.buildListParams())
      .then((body) => {
        if (isApiSuccess(body)) {
          const page = getApiData(body) || {}
          let list = mapTaskList(page.records || [])
          list = filterTasksByTab(list, this.data.currentTab)
          const preserveSelect = this.data.selectionMode
          list = decorateTaskRows(list, { resetSelect: !preserveSelect })
          const swipeReset = this.data.page === 1 && !preserveSelect
          const newTasks = this.data.page === 1 ? list : [...decorateTaskRows(this.data.tasks, { resetSelect: !preserveSelect }), ...list]

          this.setData({
            tasks: newTasks,
            hasMore: page.total > newTasks.length,
            headerHint: this.buildHeaderHint(page.total != null ? page.total : newTasks.length),
            loading: false,
            loadError: false,
            selectedCount: preserveSelect ? this.countSelected(newTasks) : 0,
            openedTaskId: swipeReset ? '' : this.data.openedTaskId,
            dragTaskId: '',
            dragOffsetX: 0
          })
          if (preserveSelect) this.refreshTexts()
          this.loadSummary()
        } else {
          this.setData({ loading: false, loadError: true })
        }
      })
      .catch((error) => {
        console.error('加载任务失败:', error)
        this.setData({ loading: false, loadError: true })
      })
  },

  loadMore: function () {
    if (!this.data.hasMore || this.data.loading) return
    this.setData({ page: this.data.page + 1 })
    this.loadTasks()
  },

  retryLoad: function () {
    this.setData({
      page: 1,
      tasks: [],
      hasMore: true,
      loadError: false
    })
    this.loadTasks()
  },

  onTaskScroll: function () {
    this.closeAllSwipes()
  },

  closeAllSwipes: function () {
    this._swipeActiveId = null
    this._swipeDirection = null
    if (!this.data.openedTaskId && !this.data.dragTaskId) {
      if (this.data.swipeDragging) {
        this.setData({ swipeDragging: false })
      }
      return
    }
    this.setData({
      openedTaskId: '',
      dragTaskId: '',
      dragOffsetX: 0,
      swipeDragging: false
    })
  },

  clampSwipeX: function (x) {
    const swipeOpen = this.data.swipeOpenPx || getSwipeOpenPx()
    if (x > 0) return 0
    if (x < swipeOpen) return swipeOpen
    return x
  },

  handleTouchStart: function (e) {
    if (this.data.selectionMode) return
    const index = Number(e.currentTarget.dataset.index)
    const taskId = e.currentTarget.dataset.id
    if (Number.isNaN(index) || !taskId) return

    const touch = e.touches[0]
    this._swipeIndex = index
    this._swipeActiveId = taskId
    this._swipeStartX = touch.clientX
    this._touchStartY = touch.clientY
    this._swipeDirection = null

    const swipeOpen = this.data.swipeOpenPx || getSwipeOpenPx()
    this._swipeStartOffset = this.data.openedTaskId === taskId ? swipeOpen : 0
    this._gestureMinSwipeX = this._swipeStartOffset
    this._gestureMaxSwipeX = this._swipeStartOffset

    if (this.data.openedTaskId && this.data.openedTaskId !== taskId) {
      this.setData({ openedTaskId: '' })
    }
  },

  handleTouchMove: function (e) {
    if (!this._swipeActiveId || this.data.selectionMode) return

    const touch = e.touches[0]
    const dx = touch.clientX - this._swipeStartX
    const dy = touch.clientY - this._touchStartY

    if (!this._swipeDirection) {
      if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return
      if (Math.abs(dy) > Math.abs(dx) + 4) {
        this._swipeActiveId = null
        this._swipeIndex = null
        return
      }
      this._swipeDirection = 'h'
      this.setData({ swipeDragging: true })
    }
    if (this._swipeDirection !== 'h') return

    const swipeX = this.clampSwipeX(this._swipeStartOffset + dx)
    this._gestureMinSwipeX = Math.min(this._gestureMinSwipeX, swipeX)
    this._gestureMaxSwipeX = Math.max(this._gestureMaxSwipeX, swipeX)
    this.setData({
      dragTaskId: this._swipeActiveId,
      dragOffsetX: swipeX
    })
  },

  finishSwipeGesture: function (e) {
    const taskId = this._swipeActiveId
    if (!taskId) return

    const swipeOpen = this.data.swipeOpenPx || getSwipeOpenPx()
    const wasOpen = this._swipeStartOffset < 0
    const openThreshold = swipeOpen * 0.35
    const closeThreshold = swipeOpen * 0.5

    let finalX = wasOpen
      ? (this._gestureMaxSwipeX != null ? this._gestureMaxSwipeX : this._swipeStartOffset)
      : (this._gestureMinSwipeX != null ? this._gestureMinSwipeX : 0)

    const endTouch = (e && e.changedTouches && e.changedTouches[0]) || null
    if (endTouch) {
      const dx = endTouch.clientX - this._swipeStartX
      finalX = this.clampSwipeX(this._swipeStartOffset + dx)
    }

    let openedTaskId = this.data.openedTaskId
    if (wasOpen) {
      const swipeRight = endTouch && endTouch.clientX - this._swipeStartX > 8
      const shouldClose = finalX > closeThreshold || swipeRight
      openedTaskId = shouldClose ? '' : taskId
    } else if (finalX <= openThreshold) {
      openedTaskId = taskId
    } else if (openedTaskId === taskId) {
      openedTaskId = ''
    }

    this.setData({
      openedTaskId,
      dragTaskId: '',
      dragOffsetX: 0,
      swipeDragging: false
    })

    this._swipeActiveId = null
    this._swipeIndex = null
    this._swipeDirection = null
    this._gestureMinSwipeX = null
    this._gestureMaxSwipeX = null
  },

  handleTouchEnd: function (e) {
    this.finishSwipeGesture(e)
  },

  handleTouchCancel: function (e) {
    this.finishSwipeGesture(e)
  },

  onTaskLongPress: function (e) {
    if (this.data.selectionMode) return
    const index = Number(e.currentTarget.dataset.index)
    const id = e.currentTarget.dataset.id
    if (!id) return

    this.closeAllSwipes()
    try {
      tt.vibrateShort({ type: 'light' })
    } catch (err) {
      // ignore
    }

    const tasks = this.data.tasks.map((item, i) => ({
      ...item,
      selected: item.id === id || i === index
    }))
    this.closeAllSwipes()
    const selectedCount = this.countSelected(tasks)
    this.setData({
      selectionMode: true,
      tasks,
      selectedCount,
      showReviewDock: false,
      headerHint: this.buildHeaderHint(0)
    })
    this.refreshTexts()
  },

  toggleSelect: function (e) {
    const id = e.currentTarget.dataset.id
    if (!id) return
    const tasks = this.data.tasks.map((item) =>
      item.id === id ? { ...item, selected: !item.selected } : item
    )
    const selectedCount = this.countSelected(tasks)
    this.setData({
      tasks,
      selectedCount,
      headerHint: this.buildHeaderHint(0)
    })
    this.refreshTexts()
  },

  exitSelectionMode: function () {
    if (!this.data.selectionMode) return
    const tasks = decorateTaskRows(this.data.tasks, { resetSelect: true })
    this.closeAllSwipes()
    this.setData({
      selectionMode: false,
      selectedCount: 0,
      tasks,
      showReviewDock: (this.data.reviewCount || 0) > 0,
      headerHint: this.buildHeaderHint(tasks.length)
    })
    this.refreshTexts()
  },

  getDeletableSelectedTasks: function () {
    return this.data.tasks.filter((item) => item.selected && item.status !== 'confirmed')
  },

  confirmDeleteTask: function (e) {
    const taskId = e.currentTarget.dataset.id
    if (!taskId) return
    this.closeAllSwipes()

    const row = this.data.tasks.find((item) => item.id === taskId)
    if (row && row.status === 'confirmed') {
      tt.showToast({ title: t('tasks.deleteNotAllowed'), icon: 'none' })
      return
    }

    tt.showModal({
      title: t('tasks.deleteConfirmTitle'),
      content: t('tasks.deleteConfirmContent'),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      confirmColor: '#ff3b30',
      success: (res) => {
        if (res.confirm) {
          this.deleteTask(taskId)
        }
      }
    })
  },

  confirmBatchDelete: function () {
    if (!this.data.selectedCount) {
      tt.showToast({ title: t('tasks.batchDeleteEmpty'), icon: 'none' })
      return
    }
    const selected = this.data.tasks.filter((item) => item.selected)
    if (!selected.length) {
      tt.showToast({ title: t('tasks.batchDeleteEmpty'), icon: 'none' })
      return
    }

    const deletable = this.getDeletableSelectedTasks()
    if (!deletable.length) {
      tt.showToast({ title: t('tasks.deleteNotAllowed'), icon: 'none' })
      return
    }

    if (deletable.length < selected.length) {
      tt.showToast({ title: t('tasks.batchDeleteSkipConfirmed'), icon: 'none', duration: 2500 })
    }

    tt.showModal({
      title: t('tasks.batchDeleteTitle'),
      content: t('tasks.batchDeleteContent', { count: deletable.length }),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      confirmColor: '#ff3b30',
      success: (res) => {
        if (res.confirm) {
          this.batchDeleteTasks(deletable.map((item) => item.id))
        }
      }
    })
  },

  deleteTask: function (taskId) {
    taskApi.deleteTask(taskId).then((res) => {
      if (res && isApiSuccess(res)) {
        tt.showToast({ title: t('tasks.deleteSuccess'), icon: 'success' })
        const tasks = this.data.tasks.filter((item) => item.id !== taskId)
        this.setData({
          tasks,
          selectedCount: this.data.selectionMode ? this.countSelected(tasks) : 0
        })
        if (tasks.length === 0 && this.data.page > 1) {
          this.setData({ page: 1 })
        }
        markTaskDataDirty()
        this.loadSummary()
        this.loadTasks()
      } else {
        tt.showToast({ title: t('tasks.deleteFail'), icon: 'none' })
      }
    }).catch((err) => {
      console.error('删除任务失败', err)
      tt.showToast({ title: t('tasks.deleteFail'), icon: 'none' })
    })
  },

  batchDeleteTasks: function (taskIds) {
    if (!taskIds.length) return
    tt.showLoading({ title: t('tasks.batchDeleting'), mask: true })

    const okIds = []
    const runNext = (idx) => {
      if (idx >= taskIds.length) {
        tt.hideLoading()
        if (okIds.length) {
          const removed = new Set(okIds)
          const tasks = this.data.tasks.filter((item) => !removed.has(item.id))
          this.setData({
            tasks,
            selectedCount: this.data.selectionMode ? this.countSelected(tasks) : 0
          })
          markTaskDataDirty()
          this.loadSummary()
          if (!tasks.length) {
            this.setData({ page: 1 })
          }
          this.loadTasks()
          if (okIds.length === taskIds.length) {
            this.exitSelectionMode()
            tt.showToast({
              title: t('tasks.batchDeleteSuccess', { count: okIds.length }),
              icon: 'success'
            })
          } else {
            tt.showToast({
              title: t('tasks.batchDeletePartial', { ok: okIds.length, total: taskIds.length }),
              icon: 'none',
              duration: 2500
            })
            this.refreshTexts()
          }
        } else {
          tt.showToast({ title: t('tasks.batchDeleteFail'), icon: 'none' })
        }
        return
      }

      const taskId = taskIds[idx]
      taskApi.deleteTask(taskId)
        .then((res) => {
          if (res && isApiSuccess(res)) {
            okIds.push(taskId)
          }
          runNext(idx + 1)
        })
        .catch((err) => {
          console.error('批量删除失败', taskId, err)
          runNext(idx + 1)
        })
    }

    runNext(0)
  },

  onReviewDock: function () {
    fetchFirstReviewTask().then((reviewTask) => {
      if (reviewTask && reviewTask.id) {
        tt.navigateTo({ url: `/pages/result/index?id=${reviewTask.id}` })
        return
      }
      if (this.data.currentTab !== 'review') {
        this.setData({
          currentTab: 'review',
          tasks: [],
          page: 1,
          hasMore: true
        })
        this.loadTasks()
        return
      }
      tt.showToast({ title: t('tasks.emptyTitle'), icon: 'none' })
    })
  },

  goToDetail: function (e) {
    if (this.data.selectionMode) {
      this.toggleSelect(e)
      return
    }
    const taskId = e.currentTarget.dataset.id
    const status = e.currentTarget.dataset.status
    if (!taskId) return
    if (status === 'processing') {
      tt.navigateTo({ url: `/pages/recognizing/index?taskId=${taskId}` })
      return
    }
    tt.navigateTo({ url: `/pages/result/index?id=${taskId}` })
  },

  goToHome: function () {
    tt.switchTab({ url: '/pages/index/index' })
  }
})
