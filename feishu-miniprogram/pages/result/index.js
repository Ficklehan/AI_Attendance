const App = getApp()

Page({
  data: {
    taskId: '',
    loading: true,
    isSubmitting: false,
    isExpanded: false,
    displayLimit: 10,
    
    taskInfo: {
      name: '',
      status: '',
      createTime: '',
      originalImage: ''
    },
    
    records: [],
    columns: [
      { key: 'NO', title: '工号' },
      { key: 'Name', title: '姓名' },
      { key: 'Agency', title: '中介机构' },
      { key: 'WorkDate', title: '日期' },
      { key: 'ArriveTime', title: '到达时间' },
      { key: 'DepartTime', title: '离开时间' },
      { key: 'BreakMinutes', title: '休息(分)' },
      { key: 'WorkHours', title: '出勤工时' },
      { key: 'SmartMark', title: '标记' }
    ],
    
    stats: {
      total: 0,
      normal: 0,
      handwritten: 0,
      absent: 0,
      deleted: 0
    }
  },

  onLoad: function (options) {
    if (options && options.id) {
      this.data.taskId = options.id
      this.loadTaskResult()
    }
  },

  get displayRecords() {
    if (this.data.isExpanded) {
      return this.data.records
    }
    return this.data.records.slice(0, this.data.displayLimit)
  },

  loadTaskResult: async function () {
    try {
      const res = await dd.httpRequest({
        url: `${App.globalData.baseUrl}/api/tasks/${this.data.taskId}`,
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
        }
      })

      if (res.data && res.data.success) {
        const data = res.data.data
        this.setData({
          taskInfo: {
            name: data.name || '未知任务',
            status: data.status || 'PENDING',
            createTime: data.createTime,
            originalImage: data.originalImage
          },
          records: data.records || []
        })
        this.calculateStats()
      }
    } catch (error) {
      console.error('加载任务失败:', error)
    } finally {
      this.setData({ loading: false })
    }
  },

  calculateStats: function () {
    const records = this.data.records
    let normal = 0
    let handwritten = 0
    let absent = 0
    let deleted = 0

    records.forEach(record => {
      if (record.isDeleted) {
        deleted++
      } else if (this.isAbsentRow(record)) {
        absent++
      } else if (record.SmartMark && record.SmartMark.includes('手写')) {
        handwritten++
      } else {
        normal++
      }
    })

    this.setData({
      stats: {
        total: records.length,
        normal,
        handwritten,
        absent,
        deleted
      }
    })
  },

  isAbsentRow: function (record) {
    return record.SmartMark === '未出勤' || 
           (!record.ArriveTime && !record.DepartTime)
  },

  toggleExpand: function () {
    this.setData({
      isExpanded: !this.data.isExpanded
    })
  },

  goBack: function () {
    dd.navigateBack()
  },

  confirmSubmit: async function () {
    dd.showModal({
      title: '确认提交',
      content: '确定要提交这些考勤数据到飞书多维表吗？',
      success: async (res) => {
        if (res.confirm) {
          this.submitToFeishu()
        }
      }
    })
  },

  submitToFeishu: async function () {
    this.setData({ isSubmitting: true })

    try {
      const res = await dd.httpRequest({
        url: `${App.globalData.baseUrl}/api/tasks/${this.data.taskId}/confirm`,
        method: 'POST',
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
        }
      })

      if (res.data && res.data.success) {
        dd.showToast({ title: '提交成功', icon: 'success' })
        
        setTimeout(() => {
          dd.switchTab({
            url: '/pages/tasks/index'
          })
        }, 1500)
      } else {
        dd.showToast({ title: res.data.message || '提交失败', icon: 'none' })
      }
    } catch (error) {
      console.error('提交失败:', error)
      dd.showToast({ title: '提交失败', icon: 'none' })
    } finally {
      this.setData({ isSubmitting: false })
    }
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
      'FAILED': 'tag-error',
      'SUBMITTED': 'tag-success'
    }
    return tags[status] || 'tag-default'
  },

  getStatusText: function (status) {
    const texts = {
      'PENDING': '待识别',
      'RECOGNIZING': '识别中',
      'COMPLETED': '识别完成',
      'FAILED': '失败',
      'SUBMITTED': '已提交'
    }
    return texts[status] || '未知'
  },

  getMarkTag: function (mark) {
    if (!mark) return 'tag-default'
    if (mark.includes('已删除')) return 'tag-default'
    if (mark.includes('未出勤')) return 'tag-error'
    if (mark.includes('手写')) return 'tag-warning'
    if (mark.includes('正常')) return 'tag-success'
    return 'tag-default'
  }
})
