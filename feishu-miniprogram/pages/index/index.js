const App = getApp()

Page({
  data: {
    imageList: [],
    currentCountry: 'CN',
    countries: [],
    recentTasks: [],
    isRecognizing: false
  },

  onLoad: function () {
    this.setData({
      countries: App.globalData.countries,
      currentCountry: App.globalData.currentCountry
    })
    this.loadRecentTasks()
  },

  goToCamera: function () {
    dd.navigateTo({
      url: '/pages/camera/index'
    })
  },

  chooseImage: async function () {
    try {
      const res = await dd.chooseImage({
        count: 9,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera']
      })
      
      const newImages = res.tempFilePaths
      this.setData({
        imageList: [...this.data.imageList, ...newImages]
      })
    } catch (error) {
      console.error('选择图片失败:', error)
    }
  },

  deleteImage: function (e) {
    const index = e.currentTarget.dataset.index
    const newList = this.data.imageList.filter((_, i) => i !== index)
    this.setData({
      imageList: newList
    })
  },

  clearImages: function () {
    dd.showModal({
      title: '确认清空',
      content: '确定要清空所有已选择的图片吗？',
      success: (res) => {
        if (res.confirm) {
          this.setData({
            imageList: []
          })
        }
      }
    })
  },

  selectCountry: function (e) {
    const code = e.currentTarget.dataset.code
    this.setData({
      currentCountry: code
    })
    App.globalData.currentCountry = code
    dd.setStorageSync({
      key: 'currentCountry',
      data: code
    })
  },

  startRecognition: async function () {
    if (!this.data.imageList.length) {
      dd.showToast({ title: '请先选择图片', icon: 'none' })
      return
    }

    this.setData({ isRecognizing: true })

    try {
      const uploadPromises = this.data.imageList.map((path, index) => 
        this.uploadAndRecognize(path, index)
      )
      
      const results = await Promise.all(uploadPromises)
      const successResults = results.filter(r => r && r.success)
      
      if (successResults.length > 0) {
        const taskId = successResults[0].data.taskId
        dd.redirectTo({
          url: `/pages/result/index?id=${taskId}`
        })
      } else {
        dd.showToast({ title: '识别失败', icon: 'none' })
      }
    } catch (error) {
      console.error('识别异常:', error)
      dd.showToast({ title: '识别异常', icon: 'none' })
    } finally {
      this.setData({ isRecognizing: false })
    }
  },

  uploadAndRecognize: async function (filePath, index) {
    try {
      const res = await dd.uploadFile({
        url: `${App.globalData.baseUrl}/api/local/upload-stream`,
        filePath,
        name: 'file',
        fileName: `image_${index}.jpg`,
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : '',
          'X-Country': this.data.currentCountry
        }
      })
      
      return JSON.parse(res.data)
    } catch (error) {
      console.error(`上传第${index}张图片失败:`, error)
      return null
    }
  },

  loadRecentTasks: async function () {
    try {
      const res = await dd.httpRequest({
        url: `${App.globalData.baseUrl}/api/tasks?limit=5`,
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
        }
      })
      
      if (res.data && res.data.success) {
        this.setData({
          recentTasks: res.data.data || []
        })
      }
    } catch (error) {
      console.error('加载任务失败:', error)
    }
  },

  goToTasks: function () {
    dd.switchTab({
      url: '/pages/tasks/index'
    })
  },

  goToResult: function (e) {
    const taskId = e.currentTarget.dataset.id
    dd.navigateTo({
      url: `/pages/result/index?id=${taskId}`
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

  getTaskStatusTag: function (status) {
    const tags = {
      'PENDING': 'tag-default',
      'RECOGNIZING': 'tag-warning',
      'COMPLETED': 'tag-success',
      'SUBMITTED': 'tag-primary',
      'FAILED': 'tag-error'
    }
    return tags[status] || 'tag-default'
  },

  getTaskStatusText: function (status) {
    const texts = {
      'PENDING': '待处理',
      'RECOGNIZING': '识别中',
      'COMPLETED': '已完成',
      'SUBMITTED': '已提交',
      'FAILED': '失败'
    }
    return texts[status] || '未知'
  }
})
