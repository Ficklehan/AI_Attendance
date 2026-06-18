import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  register: (data) => request.post('/auth/register', data),
  getCurrentUser: () => request.get('/auth/me')
}

export const taskApi = {
  getTaskList: (params) => request.get('/tasks', { params }),
  getTaskDetail: (taskId) => request.get(`/tasks/${taskId}`),
  confirmTask: (taskId, data) => request.post(`/tasks/${taskId}/confirm`, data),
  deleteTask: (taskId) => request.post(`/tasks/${taskId}/delete`),
  cancelTask: (taskId) => request.post(`/tasks/${taskId}/cancel`),
  getTaskStats: () => request.get('/tasks/stats')
}

export const uploadApi = {
  uploadImage: (file, onProgress) => {
    const formData = new FormData()
    formData.append('file', file)
    
    return request.post('/local/upload-stream', formData, {
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          onProgress(percent)
        }
      }
    })
  }
}

export const chatApi = {
  sendMessage: (data) => request.post('/chat/completion', data),
  analyzeImage: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/chat/image', formData)
  }
}

export const configApi = {
  getConfig: () => request.get('/config'),
  updateConfig: (data) => request.post('/config/update', data)
}
