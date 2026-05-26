import request from './index'

export const getTaskList = (params) => {
  return request({
    url: '/tasks',
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
