const App = getApp()
const { isApiSuccess, getApiData } = require('../../utils/response')
const { t } = require('../../utils/i18n')
const { syncCountryFromServer } = require('../../utils/preferences')
const { fetchTaskSummary } = require('../../utils/taskSummary')

Page({
  data: {
    userInfo: {},
    stats: {
      total: 0,
      completed: 0,
      pending: 0,
      records: 0
    },
    pendingCount: 0,
    summaryLine: '',
    avatarLetter: '用',
    texts: {}
  },

  onLoad: function () {
    this.refreshTexts()
    this.loadUserInfo()
    this.loadStats()
  },

  onShow: function () {
    syncCountryFromServer().finally(() => {
      this.refreshTexts()
      this.loadStats()
    })
  },

  refreshTexts: function () {
    const stats = this.data.stats || {}
    const pending = (stats.review != null ? stats.review : stats.pending) || 0
    const processing = stats.processing || 0
    const pendingTotal = pending + processing
    this.setData({
      texts: {
        title: t('profile.title'),
        quickSection: t('profile.quickSection'),
        sectionMenu: t('profile.sectionMenu'),
        menuAssistant: t('profile.menuAssistant'),
        menuAssistantDesc: t('profile.menuAssistantDesc'),
        menuSettings: t('profile.menuSettings'),
        menuSettingsDesc: t('profile.menuSettingsDesc'),
        menuHelp: t('profile.menuHelp'),
        menuHelpDesc: t('profile.menuHelpDesc'),
        menuAbout: t('profile.menuAbout'),
        menuAboutDesc: t('profile.menuAboutDesc'),
        menuLogout: t('profile.menuLogout'),
        menuLogoutDesc: t('profile.menuLogoutDesc'),
        userDefault: t('common.user'),
        version: t('profile.version')
      },
      pendingCount: pendingTotal,
      summaryLine: t('profile.todaySummaryLine', {
        total: stats.total || 0,
        pending: pendingTotal
      })
    })
    tt.setNavigationBarTitle({ title: t('profile.title') })
  },

  loadUserInfo: function () {
    const userInfo = App.globalData.userInfo || {}
    const name = userInfo.name || userInfo.realName || userInfo.username || ''
    const letter = name ? String(name).trim().charAt(0) : '用'
    this.setData({
      userInfo,
      avatarLetter: letter
    })
  },

  loadStats: function () {
    fetchTaskSummary().then((summary) => {
      const stats = {
        total: summary.total || 0,
        processing: summary.processing || 0,
        pending: summary.review || 0,
        review: summary.review || 0,
        completed: summary.confirmed || 0,
        confirmed: summary.confirmed || 0,
        failed: summary.failed || 0
      }
      this.setData({ stats }, () => this.refreshTexts())
    }).catch((error) => {
      console.error('加载统计失败:', error)
    })
  },

  goToTasks: function (e) {
    const status = e && e.currentTarget && e.currentTarget.dataset
      ? e.currentTarget.dataset.status
      : 'all'
    let tab = 'all'
    if (status === 'pending') tab = 'pending'
    else if (status === 'completed') tab = 'completed'
    try {
      const app = getApp()
      app.globalData.tasksInitialTab = tab
    } catch (err) {
      console.warn('set tasksInitialTab failed', err)
    }
    tt.switchTab({ url: '/pages/tasks/index' })
  },

  goToChat: function () {
    tt.navigateTo({ url: '/pages/chat/index' })
  },

  goToConfig: function () {
    tt.navigateTo({ url: '/pages/settings/index' })
  },

  goToHelp: function () {
    tt.showToast({ title: t('profile.helpDeveloping'), icon: 'none' })
  },

  goToAbout: function () {
    tt.showModal({
      title: t('profile.aboutTitle'),
      content: t('profile.aboutContent'),
      showCancel: false,
      confirmText: t('common.confirm')
    })
  },

  logout: function () {
    tt.showModal({
      title: t('profile.logoutTitle'),
      content: t('profile.logoutContent'),
      confirmText: t('common.confirm'),
      cancelText: t('common.cancel'),
      success: (res) => {
        if (res.confirm) {
          App.globalData.userInfo = null
          App.globalData.token = ''
          tt.removeStorageSync('token')
          tt.removeStorageSync('userInfo')
          tt.reLaunch({ url: '/pages/login/index' })
        }
      }
    })
  }
})
