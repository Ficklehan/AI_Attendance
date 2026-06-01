import request from './index'

export const getTaskList = (params) => {
  return request({
    url: '/tasks',
    method: 'get',
    params,
  })
}

export const getTaskSummary = () => {
  return request({
    url: '/tasks/summary',
    method: 'get',
  })
}

export const getEmployeeRecordList = (params) => {
  return request({
    url: '/tasks/records',
    method: 'get',
    params,
  })
}

export const getTaskDetail = (taskId) => {
  return request({
    url: `/tasks/${taskId}`,
    method: 'get',
  })
}

export const confirmTask = (taskId, data) => {
  return request({
    url: `/tasks/${taskId}/confirm`,
    method: 'post',
    data,
  })
}

export const deleteTask = (taskId) => {
  return request({
    url: `/tasks/${taskId}`,
    method: 'delete',
  })
}

export const cancelTask = (taskId) => {
  return request({
    url: `/tasks/${taskId}/cancel`,
    method: 'post',
  })
}

export const retryFeishuSync = (taskId) => {
  return request({
    url: `/tasks/${taskId}/retry-sync`,
    method: 'post',
  })
}

export const checkTaskDuplicateNames = (taskId, records, scope = 'confirmed_only') => {
  return request({
    url: `/tasks/${taskId}/duplicate-check`,
    method: 'post',
    data: { records, scope },
  })
}

export const calibrateTaskRecord = (taskId, data) => {
  return request({
    url: `/tasks/${taskId}/calibrate-record`,
    method: 'post',
    data,
  })
}
