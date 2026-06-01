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

const SWIPE_OPEN = -80

function reviewCtaLabel(count) {
  const key = 'home.primaryCtaReview'
  const value = t(key, { count })
  if (value && value !== key) return value
  return `去核对 (${count})`
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
      headerHint: this.buildHeaderHint(0)
    })
    return true
  },

  refreshTexts: function () {
    const reviewCount = this.data.reviewCount || 0
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
        loadFailTitle: t('tasks.loadFailTitle'),
        loadFailDesc: t('tasks.loadFailDesc'),
        retry: t('tasks.retry'),
        swipeHint: t('tasks.swipeHint'),
        search: t('common.search'),
        searchPlaceholder: t('tasks.searchPlaceholder'),
        searchEmptyTitle: t('tasks.searchEmptyTitle'),
        searchEmptyDesc: t('tasks.searchEmptyDesc')
      },
      tabs: [
        { label: t('tasks.tabAll'), value: 'all' },
        { label: t('tasks.tabPending'), value: 'pending' },
        { label: t('tasks.tabReview'), value: 'review' },
        { label: t('tasks.tabCompleted'), value: 'completed' }
      ],
      headerHint: this.buildHeaderHint(this.data.tasks.length || 0),
      primaryCtaLabel: reviewCtaLabel(reviewCount),
      showReviewDock: reviewCount > 0
    })
    tt.setNavigationBarTitle({ title: t('tasks.title') })
  },

  buildHeaderHint: function (listTotal) {
    const tab = normalizeTasksTab(this.data.currentTab)
    const summary = this._taskSummary
    const count = summary ? tabCountFromSummary(tab, summary) : (listTotal || 0)
    if (tab === 'pending') return t('tasks.hintPending', { count })
    if (tab === 'review') return t('tasks.hintReview', { count })
    if (tab === 'completed') return t('tasks.hintCompleted', { count })
    return t('tasks.hintAll', { count })
  },

  loadSummary: function () {
    fetchTaskSummary().then((summary) => {
      this._taskSummary = summary
      const reviewCount = summary.review || 0
      this.setData({
        reviewCount,
        showReviewDock: reviewCount > 0,
        primaryCtaLabel: reviewCtaLabel(reviewCount),
        headerHint: this.buildHeaderHint(0)
      })
    })
  },

  switchTab: function (e) {
    const tab = normalizeTasksTab(e.currentTarget.dataset.value)
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

    tt.request({
      url: `${App.globalData.baseUrl}/tasks`,
      method: 'GET',
      data: this.buildListParams(),
      header: {
        Authorization: App.globalData.token ? `Bearer ${App.globalData.token}` : ''
      },
      success: (res) => {
        if (isApiSuccess(res.data)) {
          const page = getApiData(res.data) || {}
          let list = mapTaskList(page.records || [])
          list = filterTasksByTab(list, this.data.currentTab)
          list = list.map((item) => ({ ...item, swipeX: 0 }))
          const newTasks = this.data.page === 1 ? list : [...this.data.tasks, ...list]

          this.setData({
            tasks: newTasks,
            hasMore: page.total > newTasks.length,
            headerHint: this.buildHeaderHint(page.total != null ? page.total : newTasks.length),
            loading: false,
            loadError: false
          })
          this.loadSummary()
        } else {
          this.setData({ loading: false, loadError: true })
        }
      },
      fail: (error) => {
        console.error('加载任务失败:', error)
        this.setData({ loading: false, loadError: true })
      }
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

  handleTouchStart: function (e) {
    const index = e.currentTarget.dataset.index
    if (index == null) return
    this._swipeIndex = index
    this._swipeStartX = e.touches[0].clientX
    const item = this.data.tasks[index]
    this._swipeStartOffset = (item && item.swipeX) || 0
  },

  handleTouchMove: function (e) {
    if (this._swipeIndex == null) return
    const dx = e.touches[0].clientX - this._swipeStartX
    let swipeX = this._swipeStartOffset + dx
    if (swipeX > 0) swipeX = 0
    if (swipeX < SWIPE_OPEN) swipeX = SWIPE_OPEN
    const tasks = this.data.tasks.map((item, i) => ({
      ...item,
      swipeX: i === this._swipeIndex ? swipeX : 0
    }))
    this.setData({ tasks })
  },

  handleTouchEnd: function () {
    if (this._swipeIndex == null) return
    const index = this._swipeIndex
    const item = this.data.tasks[index] || {}
    const snap = (item.swipeX || 0) < SWIPE_OPEN / 2 ? 0 : SWIPE_OPEN
    const tasks = this.data.tasks.map((row, i) => ({
      ...row,
      swipeX: i === index ? snap : 0
    }))
    this.setData({ tasks })
    this._swipeIndex = null
  },

  confirmDeleteTask: function (e) {
    const taskId = e.currentTarget.dataset.id
    if (!taskId) return
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

  deleteTask: function (taskId) {
    taskApi.deleteTask(taskId).then((res) => {
      if (res && isApiSuccess(res)) {
        tt.showToast({ title: t('tasks.deleteSuccess'), icon: 'success' })
        const tasks = this.data.tasks.filter((item) => item.id !== taskId)
        this.setData({ tasks })
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

  goToDetail: function (e) {
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
