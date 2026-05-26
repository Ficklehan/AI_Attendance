const App = getApp()

Page({
  data: {
    cameraPosition: 'back',
    flashMode: 'off'
  },

  onLoad: function () {
    dd.setNavigationBarTitle({ title: '拍照识别' })
  },

  onReady: function () {
    this.cameraContext = dd.createCameraContext()
  },

  toggleCamera: function () {
    this.setData({
      cameraPosition: this.data.cameraPosition === 'back' ? 'front' : 'back'
    })
  },

  toggleFlash: function () {
    this.setData({
      flashMode: this.data.flashMode === 'off' ? 'on' : 'off'
    })
  },

  takePhoto: async function () {
    try {
      const res = await this.cameraContext.takePhoto({
        quality: 'high'
      })
      
      this.processPhoto(res.tempImagePath)
    } catch (error) {
      console.error('拍照失败:', error)
      dd.showToast({ title: '拍照失败', icon: 'none' })
    }
  },

  processPhoto: async function (imagePath) {
    dd.showLoading({ title: '正在处理...' })
    
    try {
      const res = await dd.getImageInfo({ src: imagePath })
      
      if (res.width < 1000 || res.height < 1000) {
        dd.hideLoading()
        dd.showModal({
          title: '图片质量提示',
          content: '拍摄的图片分辨率较低，可能影响识别效果。是否继续使用？',
          success: (modalRes) => {
            if (modalRes.confirm) {
              this.uploadPhoto(imagePath)
            }
          }
        })
        return
      }
      
      this.uploadPhoto(imagePath)
    } catch (error) {
      console.error('处理图片失败:', error)
      dd.hideLoading()
      dd.showToast({ title: '处理失败', icon: 'none' })
    }
  },

  uploadPhoto: async function (imagePath) {
    try {
      dd.showLoading({ title: '上传识别中...' })
      
      const res = await dd.uploadFile({
        url: `${App.globalData.baseUrl}/api/local/upload-stream`,
        filePath: imagePath,
        name: 'file',
        fileName: 'photo.jpg',
        header: {
          'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : '',
          'X-Country': App.globalData.currentCountry
        }
      })
      
      dd.hideLoading()
      const data = JSON.parse(res.data)
      
      if (data.success) {
        dd.redirectTo({
          url: `/pages/result/index?id=${data.data.taskId}`
        })
      } else {
        dd.showToast({ title: data.message || '识别失败', icon: 'none' })
      }
    } catch (error) {
      console.error('上传失败:', error)
      dd.hideLoading()
      dd.showToast({ title: '上传失败', icon: 'none' })
    }
  },

  chooseFromGallery: async function () {
    try {
      const res = await dd.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album']
      })
      
      if (res.tempFilePaths.length > 0) {
        this.processPhoto(res.tempFilePaths[0])
      }
    } catch (error) {
      console.error('选择图片失败:', error)
    }
  },

  onCameraError: function (error) {
    console.error('相机错误:', error)
    dd.showToast({ title: '相机初始化失败', icon: 'none' })
  }
})
