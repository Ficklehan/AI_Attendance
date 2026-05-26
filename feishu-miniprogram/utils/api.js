const { request, uploadFile } = require('./request')

const authApi = {
  login: (data) => request({ url: '/api/auth/login', method: 'POST', data }),
  register: (data) => request({ url: '/api/auth/register', method: 'POST', data }),
  getCurrentUser: () => request({ url: '/api/auth/me', method: 'GET' })
}

const taskApi = {
  getTaskList: (params) => request({ url: '/tasks', method: 'GET', data: params }),
  getTaskDetail: (taskId) => request({ url: `/tasks/${taskId}`, method: 'GET' }),
  confirmTask: (taskId, data) => request({ url: `/tasks/${taskId}/confirm`, method: 'POST', data }),
  deleteTask: (taskId) => request({ url: `/tasks/${taskId}`, method: 'DELETE' }),
  cancelTask: (taskId) => request({ url: `/tasks/${taskId}/cancel`, method: 'POST' }),
  getTaskStats: () => request({ url: '/tasks/stats', method: 'GET' })
}

const uploadApi = {
  uploadImage: (filePath, fileName) => uploadFile(filePath, fileName),
  uploadImageWithProgress: (filePath, onProgress) => {
    return new Promise((resolve, reject) => {
      const dd = getApp().globalData.dd
      dd.uploadFile({
        url: `${getApp().globalData.baseUrl}/api/local/upload-stream`,
        filePath,
        name: 'file',
        fileName: fileName || 'image.jpg',
        header: {
          'Authorization': getApp().globalData.token ? `Bearer ${getApp().globalData.token}` : ''
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
}

const chatApi = {
  sendMessage: (data) => request({ url: '/api/chat/completion', method: 'POST', data }),
  analyzeImage: (filePath) => {
    return new Promise((resolve, reject) => {
      const dd = getApp().globalData.dd
      dd.uploadFile({
        url: `${getApp().globalData.baseUrl}/api/chat/image`,
        filePath,
        name: 'file',
        fileName: 'image.jpg',
        header: {
          'Authorization': getApp().globalData.token ? `Bearer ${getApp().globalData.token}` : ''
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
}

const configApi = {
  getConfig: () => request({ url: '/api/config', method: 'GET' }),
  updateConfig: (data) => request({ url: '/api/config', method: 'PUT', data })
}

const feishuApi = {
  getAuthUrl: () => request({ url: '/api/feishu/auth-url', method: 'GET' }),
  syncToBitable: (data) => request({ url: '/api/bitable/sync', method: 'POST', data })
}

module.exports = {
  authApi,
  taskApi,
  uploadApi,
  chatApi,
  configApi,
  feishuApi
}
