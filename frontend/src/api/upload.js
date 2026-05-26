import request from './index'

export const uploadImage = (file) => {
  const formData = new FormData()
  formData.append('image', file)
  
  return request({
    url: '/local/upload',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export const getImageUrl = (fileKey) => {
  return `/api/local/image/${fileKey}`
}

export const exportCsv = (taskId) => {
  return request({
    url: `/local/export/${taskId}/csv`,
    method: 'get',
    responseType: 'blob',
  })
}

export const getDebugInfo = (taskId) => {
  return request({
    url: `/local/debug/${taskId}`,
    method: 'get',
  })
}
