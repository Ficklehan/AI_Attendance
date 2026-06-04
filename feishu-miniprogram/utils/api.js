const { request } = require('./request')

const authApi = {
  login: (data) => request({ url: '/auth/login', method: 'POST', data }),
  getCurrentUser: () => request({ url: '/auth/profile', method: 'GET' })
}

const taskApi = {
  getTaskList: (params) => request({ url: '/tasks', method: 'GET', data: params }),
  getTaskDetail: (taskId) => request({ url: `/tasks/${taskId}`, method: 'GET' }),
  getTaskProgress: (taskId) => request({ url: `/tasks/${taskId}/progress`, method: 'GET' }),
  confirmTask: (taskId, data) => request({ url: `/tasks/${taskId}/confirm`, method: 'POST', data }),
  checkDuplicateNames: (taskId, records, scope) => request({
    url: `/tasks/${taskId}/duplicate-check`,
    method: 'POST',
    data: { records, scope: scope || 'confirmed_only' }
  }),
  retryFeishuSync: (taskId) => request({ url: `/tasks/${taskId}/retry-sync`, method: 'POST' }),
  deleteTask: (taskId) => request({ url: `/tasks/${taskId}`, method: 'DELETE' }),
  getTaskStats: () => request({ url: '/tasks/stats', method: 'GET' }),
  getTaskSummary: () => request({ url: '/tasks/summary', method: 'GET' }),
  calibrateRecord: (taskId, data) => request({
    url: `/tasks/${taskId}/calibrate-record`,
    method: 'POST',
    data
  })
}

const chatApi = {
  sendMessage: (data) => request({ url: '/chat/completion', method: 'POST', data })
}

const configApi = {
  getCountryOptions: () => request({ url: '/config/country-options', method: 'GET' }),
  getCurrentCountry: () => request({ url: '/config/current-country', method: 'GET' }),
  setCurrentCountry: (country) => request({
    url: '/config/current-country',
    method: 'PUT',
    data: { country }
  })
}

module.exports = {
  authApi,
  taskApi,
  chatApi,
  configApi
}
