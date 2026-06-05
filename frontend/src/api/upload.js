import request from './index'

const UPLOAD_RETRY_DELAYS_MS = [2000, 5000, 10000]

async function withUploadRetry(fn) {
  let lastError
  for (let attempt = 0; attempt <= UPLOAD_RETRY_DELAYS_MS.length; attempt += 1) {
    try {
      return await fn()
    } catch (error) {
      lastError = error
      if (attempt < UPLOAD_RETRY_DELAYS_MS.length) {
        await new Promise((resolve) => {
          setTimeout(resolve, UPLOAD_RETRY_DELAYS_MS[attempt])
        })
      }
    }
  }
  throw lastError
}

export const uploadImageAsync = (formData) => withUploadRetry(() => request({
  url: '/local/upload-async',
  method: 'post',
  data: formData,
  headers: {
    'Content-Type': 'multipart/form-data',
    'X-Client': 'pc-web',
  },
  timeout: 180000,
}))

export const startTaskRecognition = (taskId) => withUploadRetry(() => request({
  url: `/local/tasks/${taskId}/recognize`,
  method: 'post',
  headers: {
    'X-Client': 'pc-web',
  },
}))
