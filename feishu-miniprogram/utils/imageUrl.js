const { request } = require('./request')
const { getApiData } = require('./response')

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function getApiBase() {
  const app = getAppSafe()
  return (app && app.globalData.baseUrl) || ''
}

function getDownloadHeaders() {
  const app = getAppSafe()
  const token = (app && app.globalData.token) || ''
  if (!token) {
    return {}
  }
  return { Authorization: `Bearer ${token}` }
}

function hasSignatureQuery(url) {
  if (!url || typeof url !== 'string') {
    return false
  }
  return url.indexOf('sig=') !== -1 && url.indexOf('exp=') !== -1
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

function collectImageKeys(task) {
  const keys = []
  if (!task) {
    return keys
  }
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
}

function appendSignature(baseUrl, signature) {
  if (!signature || signature.exp == null || !signature.uid || !signature.sig) {
    return baseUrl
  }
  const sep = baseUrl.indexOf('?') !== -1 ? '&' : '?'
  return `${baseUrl}${sep}exp=${signature.exp}&uid=${encodeURIComponent(signature.uid)}&sig=${encodeURIComponent(signature.sig)}`
}

function fetchImageSignatures(keys) {
  if (!keys || !keys.length) {
    return Promise.resolve({})
  }
  return request({
    url: '/local/image/signatures',
    method: 'POST',
    data: { keys }
  }).then((res) => getApiData(res) || {}).catch((err) => {
    console.warn('image signatures failed', err)
    return {}
  })
}

function toAbsoluteImageUrl(fileKey, base, signature) {
  const key = normalizeImageKey(fileKey)
  if (!key) {
    return ''
  }
  const root = base || getApiBase()
  if (!root) {
    return ''
  }
  const url = `${root}/local/image/${encodeURIComponent(key)}`
  return appendSignature(url, signature)
}

/**
 * 从任务数据解析全部原图（imageUrls + fileKey 回退），返回带短期签名 URL。
 */
function buildTaskImageList(task) {
  const base = getApiBase()
  const keys = collectImageKeys(task)
  if (!keys.length) {
    return Promise.resolve([])
  }
  return fetchImageSignatures(keys).then((signatures) => keys
    .map((key, index) => {
      const url = toAbsoluteImageUrl(key, base, signatures[key])
      return {
        key,
        index: index + 1,
        url,
        fileName: formatImageFileName(key)
      }
    })
    .filter((item) => item.url))
}

function formatImageFileName(key) {
  const normalized = normalizeImageKey(key)
  if (!normalized) {
    return ''
  }
  const parts = normalized.split('/')
  return parts[parts.length - 1] || normalized
}

function refreshImageUrl(item) {
  return fetchImageSignatures([item.key]).then((signatures) => {
    const url = toAbsoluteImageUrl(item.key, getApiBase(), signatures[item.key])
    return { ...item, url: url || item.url }
  })
}

function downloadImageItem(item, allowRetry) {
  return new Promise((resolve) => {
    if (!item.url) {
      resolve({ ...item, displayUrl: '' })
      return
    }

    const runDownload = (target, canRetry) => {
      tt.downloadFile({
        url: target.url,
        header: getDownloadHeaders(),
        success: (res) => {
          if (res.statusCode === 401 && canRetry) {
            refreshImageUrl(target)
              .then((fresh) => downloadImageItem(fresh, false).then(resolve))
              .catch(() => resolve({ ...target, displayUrl: target.url }))
            return
          }
          const displayUrl = res.statusCode === 200 && res.tempFilePath
            ? res.tempFilePath
            : target.url
          if (res.statusCode !== 200) {
            console.warn('image download bad status', target.key, res.statusCode)
          }
          resolve({ ...target, displayUrl })
        },
        fail: (err) => {
          const status = err && err.statusCode
          if (status === 401 && canRetry) {
            refreshImageUrl(target)
              .then((fresh) => downloadImageItem(fresh, false).then(resolve))
              .catch(() => resolve({ ...target, displayUrl: target.url }))
            return
          }
          console.warn('image download failed', target.key, err)
          resolve({ ...target, displayUrl: target.url })
        }
      })
    }

    if (!hasSignatureQuery(item.url) && allowRetry) {
      refreshImageUrl(item).then((fresh) => runDownload(fresh, true)).catch(() => runDownload(item, true))
      return
    }

    runDownload(item, allowRetry)
  })
}

function prepareLocalImages(imageList) {
  const list = imageList || []
  if (!list.length) {
    return Promise.resolve([])
  }
  return Promise.all(list.map((item) => downloadImageItem(item, true)))
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

function previewTaskImages(imageList, index) {
  return openImagePreview(imageList, index)
}

module.exports = {
  buildTaskImageList,
  prepareLocalImages,
  openImagePreview,
  previewTaskImages,
  toAbsoluteImageUrl,
  formatImageFileName,
  fetchImageSignatures,
  collectImageKeys
}
