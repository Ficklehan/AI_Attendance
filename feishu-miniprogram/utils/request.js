const App = getApp()

const request = async (options) => {
  const { url, method = 'GET', data = {}, header = {} } = options

  const defaultHeader = {
    'Content-Type': 'application/json',
    'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
  }

  try {
    const res = await dd.httpRequest({
      url: `${App.globalData.baseUrl}${url}`,
      method,
      data,
      header: { ...defaultHeader, ...header },
      timeout: 30000
    })

    if (res.statusCode === 200) {
      if (res.data.success !== undefined && !res.data.success) {
        dd.showToast({
          title: res.data.message || '请求失败',
          icon: 'none'
        })
        return null
      }
      return res.data
    } else {
      dd.showToast({
        title: '网络错误',
        icon: 'none'
      })
      return null
    }
  } catch (error) {
    console.error('请求异常:', error)
    dd.showToast({
      title: '网络异常',
      icon: 'none'
    })
    return null
  }
}

const uploadFile = async (filePath, fileName = 'image.jpg') => {
  return new Promise((resolve, reject) => {
    dd.uploadFile({
      url: `${App.globalData.baseUrl}/api/local/upload-stream`,
      filePath,
      name: 'file',
      fileName,
      header: {
        'Authorization': App.globalData.token ? `Bearer ${App.globalData.token}` : ''
      },
      success: (res) => {
        try {
          const data = JSON.parse(res.data)
          resolve(data)
        } catch {
          resolve(res)
        }
      },
      fail: (error) => {
        reject(error)
      }
    })
  })
}

module.exports = {
  request,
  uploadFile
}
