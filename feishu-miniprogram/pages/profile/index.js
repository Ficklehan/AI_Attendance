const App = getApp()

Page({
  data: {
    userInfo: {},
    stats: {
      total: 0,
      completed: 0,
      pending: 0,
      records: 0
    }
  },

  onLoad: function () {
    dd.setNavigationBarTitle({ title: '我的' })
    this.loadUserInfo()
    this.loadStats()
  },

  onShow: function () {
    this.loadStats()
  },

  loadUserInfo: function () {
    this.setData({
      userInfo: App.globalData.userInfo || {}
    })
  },

  loadStats: async function () {
    try {
      const res = await dd.httpRequest({
        url: `${App.globalData.baseUrl}/api/tasks/stats`,
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
        }
      })

      if (res.data && res.data.success) {
        this.setData({
          stats: res.data.data
        })
      }
    } catch (error) {
      console.error('加载统计失败:', error)
    }
  },

  editProfile: function () {
    dd.showToast({ title: '编辑功能开发中', icon: 'none' })
  },

  goToTasks: function (e) {
    const status = e.currentTarget.dataset.status
    dd.switchTab({
      url: '/pages/tasks/index'
    })
  },

  goToConfig: function () {
    dd.showToast({ title: '设置功能开发中', icon: 'none' })
  },

  goToHelp: function () {
    dd.showToast({ title: '帮助中心开发中', icon: 'none' })
  },

  goToAbout: function () {
    dd.showModal({
      title: '关于AI考勤助手',
      content: '版本：v1.0.0\n\nAI考勤助手是一款基于AI技术的考勤表识别工具，支持拍照识别、自动解析考勤数据，并同步到飞书多维表。',
      showCancel: false
    })
  },

  logout: function () {
    dd.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: (res) => {
        if (res.confirm) {
          App.globalData.userInfo = null
          App.globalData.token = null
          
          dd.reLaunch({
            url: '/pages/index/index'
          })
        }
      }
    })
  }
})
