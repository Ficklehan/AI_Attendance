const App = getApp()

Page({
  data: {
    currentTab: 'all',
    tabs: [
      { label: '全部', value: 'all', count: 0 },
      { label: '待处理', value: 'pending', count: 0 },
      { label: '已完成', value: 'completed', count: 0 }
    ],
    tasks: [],
    loading: true,
    hasMore: true,
    page: 1,
    pageSize: 10
  },

  onLoad: function () {
    dd.setNavigationBarTitle({ title: '任务列表' })
    this.loadTasks()
  },

  onShow: function () {
    this.loadTasks()
  },

  switchTab: function (e) {
    const tab = e.currentTarget.dataset.value
    this.setData({
      currentTab: tab,
      tasks: [],
      page: 1,
      hasMore: true
    })
    this.loadTasks()
  },

  showFilter: function () {
    dd.showToast({ title: '筛选功能开发中', icon: 'none' })
  },

  loadTasks: async function () {
    if (this.data.loading && this.data.page > 1) return

    this.setData({ loading: true })

    try {
      const res = await dd.httpRequest({
        url: `${App.globalData.baseUrl}/api/tasks`,
        method: 'GET',
        data: {
          page: this.data.page,
          size: this.data.pageSize,
          status: this.data.currentTab === 'all' ? '' : 
                  this.data.currentTab === 'pending' ? 'PENDING,RECOGNIZING' : 'COMPLETED,SUBMITTED'
        },
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
        }
      })

      if (res.data && res.data.success) {
        const data = res.data.data
        const newTasks = this.data.page === 1 ? data.content : [...this.data.tasks, ...data.content]
        
        this.setData({
          tasks: newTasks,
          hasMore: data.totalElements > newTasks.length,
          loading: false
        })

        this.updateTabCounts()
      }
    } catch (error) {
      console.error('加载任务失败:', error)
      this.setData({ loading: false })
    }
  },

  loadMore: function () {
    if (!this.data.hasMore || this.data.loading) return
    
    this.setData({
      page: this.data.page + 1
    })
    this.loadTasks()
  },

  updateTabCounts: function () {
    const tasks = this.data.tasks
    const counts = {
      all: tasks.length,
      pending: tasks.filter(t => ['PENDING', 'RECOGNIZING'].includes(t.status)).length,
      completed: tasks.filter(t => ['COMPLETED', 'SUBMITTED'].includes(t.status)).length
    }

    const tabs = this.data.tabs.map(tab => ({
      ...tab,
      count: counts[tab.value]
    }))

    this.setData({ tabs })
  },

  goToDetail: function (e) {
    const taskId = e.currentTarget.dataset.id
    dd.navigateTo({
      url: `/pages/result/index?id=${taskId}`
    })
  },

  viewDetail: function (e) {
    const taskId = e.currentTarget.dataset.id
    dd.navigateTo({
      url: `/pages/result/index?id=${taskId}`
    })
  },

  goToHome: function () {
    dd.switchTab({
      url: '/pages/index/index'
    })
  },

  formatTime: function (time) {
    if (!time) return ''
    const date = new Date(time)
    const month = date.getMonth() + 1
    const day = date.getDate()
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${month}月${day}日 ${hour}:${minute}`
  },

  getStatusTag: function (status) {
    const tags = {
      'PENDING': 'tag-default',
      'RECOGNIZING': 'tag-warning',
      'COMPLETED': 'tag-success',
      'SUBMITTED': 'tag-success',
      'FAILED': 'tag-error'
    }
    return tags[status] || 'tag-default'
  },

  getStatusText: function (status) {
    const texts = {
      'PENDING': '待识别',
      'RECOGNIZING': '识别中',
      'COMPLETED': '已完成',
      'SUBMITTED': '已提交',
      'FAILED': '失败'
    }
    return texts[status] || '未知'
  },

  getCountryName: function (code) {
    const countries = {
      'CN': '中国',
      'FR': '法国',
      'DE': '德国',
      'US': '美国'
    }
    return countries[code] || code
  },

  getNormalCount: function (records) {
    return records.filter(r => !r.isDeleted && r.SmartMark !== '未出勤').length
  },

  getAbsentCount: function (records) {
    return records.filter(r => r.SmartMark === '未出勤').length
  }
})
