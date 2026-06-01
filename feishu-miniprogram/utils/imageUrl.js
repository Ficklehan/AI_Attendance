function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function getToken() {
  try {
    const app = getAppSafe()
    return (app && app.globalData.token) || tt.getStorageSync('token') || ''
  } catch (e) {
    return ''
  }
}

function getApiBase() {
  const app = getAppSafe()
  return (app && app.globalData.baseUrl) || ''
}

function withAuthToken(url) {
  if (!url || typeof url !== 'string') {
    return url
  }
  if (url.indexOf('/local/image/') === -1) {
    return url
  }
  const token = getToken()
  if (!token || url.indexOf('token=') !== -1) {
    return url
  }
  const sep = url.indexOf('?') !== -1 ? '&' : '?'
  return url + sep + 'token=' + encodeURIComponent(token)
}

function normalizeImageKey(value) {
  if (!value || typeof value !== 'string') {
    return ''
  }
  const raw = value.trim()
  if (!raw) {
    return ''
  }
  if (raw.startsWith('http')) {
    const match = raw.match(/\/local\/image\/([^?]+)/)
    return match ? decodeURIComponent(match[1]) : ''
  }
  if (raw.indexOf('/local/image/') !== -1) {
    const parts = raw.split('/local/image/')
    return decodeURIComponent((parts[1] || '').split('?')[0])
  }
  return raw.split('?')[0]
}

function toAbsoluteImageUrl(fileKey, base) {
  const key = normalizeImageKey(fileKey)
  if (!key) {
    return ''
  }
  if (key.startsWith('http')) {
    return withAuthToken(key)
  }
  const root = base || getApiBase()
  if (!root) {
    return ''
  }
  return withAuthToken(`${root}/local/image/${encodeURIComponent(key)}`)
}

/**
 * 从任务数据解析全部原图（imageUrls + fileKey 回退）。
 */
function buildTaskImageList(task) {
  if (!task) {
    return []
  }
  const base = getApiBase()
  const keys = []

  if (task.imageUrls) {
    try {
      const raw = typeof task.imageUrls === 'string' ? JSON.parse(task.imageUrls) : task.imageUrls
      if (Array.isArray(raw)) {
        raw.forEach((entry) => {
          const key = normalizeImageKey(entry)
          if (key && keys.indexOf(key) === -1) {
            keys.push(key)
          }
        })
      }
    } catch (e) {
      console.warn('parse imageUrls failed', e)
    }
  }

  const fileKey = normalizeImageKey(task.fileKey)
  if (fileKey && keys.indexOf(fileKey) === -1) {
    keys.unshift(fileKey)
  }

  return keys
    .map((key, index) => {
      const url = toAbsoluteImageUrl(key, base)
      return {
        key,
        index: index + 1,
        url,
        fileName: formatImageFileName(key)
      }
    })
    .filter((item) => item.url)
}

function formatImageFileName(key) {
  const normalized = normalizeImageKey(key)
  if (!normalized) {
    return ''
  }
  const parts = normalized.split('/')
  return parts[parts.length - 1] || normalized
}

/**
 * 小程序 image / previewImage 对带鉴权头更稳：先下载到本地临时路径。
 */
function prepareLocalImages(imageList) {
  const list = imageList || []
  if (!list.length) {
    return Promise.resolve([])
  }
  const token = getToken()
  return Promise.all(
    list.map((item) => new Promise((resolve) => {
      tt.downloadFile({
        url: item.url,
        header: token ? { Authorization: `Bearer ${token}` } : {},
        success: (res) => {
          const displayUrl = res.statusCode === 200 && res.tempFilePath
            ? res.tempFilePath
            : item.url
          resolve({ ...item, displayUrl })
        },
        fail: (err) => {
          console.warn('image download failed', item.key, err)
          resolve({ ...item, displayUrl: item.url })
        }
      })
    }))
  )
}

function openImagePreview(imageList, index) {
  const list = (imageList || []).filter((item) => item && (item.url || item.displayUrl))
  if (!list.length) {
    return Promise.reject(new Error('no images'))
  }
  const startIndex = Math.max(0, Math.min(index || 0, list.length - 1))
  return prepareLocalImages(list).then((prepared) => {
    const session = { images: prepared, index: startIndex }
    const app = getAppSafe()
    if (app) {
      app.globalData.imagePreviewSession = session
    }
    try {
      tt.setStorageSync('_imagePreviewSession', session)
    } catch (e) {
      console.warn('image preview storage failed', e)
    }
    return new Promise((resolve, reject) => {
      tt.navigateTo({
        url: `/pages/image-preview/index?index=${startIndex}`,
        success: resolve,
        fail: reject
      })
    })
  })
}

/** @deprecated 使用 openImagePreview */
function previewTaskImages(imageList, index) {
  return openImagePreview(imageList, index)
}

module.exports = {
  withAuthToken,
  buildTaskImageList,
  prepareLocalImages,
  openImagePreview,
  previewTaskImages,
  toAbsoluteImageUrl,
  formatImageFileName
}
