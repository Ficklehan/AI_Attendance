const { isApiSuccess, getApiMessage } = require('./response')
const { t } = require('./i18n')

/** 延迟加载，避免 request → preferences → configApi → request 循环依赖 */
function readCountryAndLocale() {
  const { getCountry, getLocale } = require('./preferences')
  return { getCountry, getLocale }
}

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function getBaseUrl() {
  const app = getAppSafe()
  return (app && app.globalData.baseUrl) || ''
}

function getAuthToken() {
  const app = getAppSafe()
  return (app && app.globalData.token) || ''
}

/** 统一鉴权 / 国家 / 语言请求头（供 request 与页面级 apiCall 共用） */
function buildDefaultHeaders(extra = {}) {
  const token = getAuthToken()
  const headers = {
    'Content-Type': 'application/json',
    ...extra
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const { getCountry, getLocale } = readCountryAndLocale()
  const country = getCountry()
  if (country) {
    headers['X-Country'] = country
  }
  const locale = getLocale()
  if (locale) {
    headers['X-Locale'] = locale
  }
  return headers
}

/**
 * 底层 HTTP：返回 { statusCode, data }，失败 reject
 */
function apiCall(options) {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    timeout = 30000,
    showErrorToast = false
  } = options
  const path = url.startsWith('http') ? url : `${getBaseUrl()}${url}`
  return new Promise((resolve, reject) => {
    tt.request({
      url: path,
      method,
      data,
      header: buildDefaultHeaders(header),
      timeout,
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res)
          return
        }
        if (showErrorToast) {
          tt.showToast({ title: t('common.networkError'), icon: 'none' })
        }
        reject(new Error(`HTTP ${res.statusCode}`))
      },
      fail: (error) => {
        console.error('请求异常:', error)
        if (showErrorToast) {
          tt.showToast({ title: t('common.networkFail'), icon: 'none' })
        }
        reject(error)
      }
    })
  })
}

const request = (options) => {
  const { url, method = 'GET', data = {}, header = {} } = options
  return new Promise((resolve) => {
    apiCall({ url, method, data, header })
      .then((res) => {
        if (!isApiSuccess(res.data)) {
          tt.showToast({
            title: getApiMessage(res.data),
            icon: 'none'
          })
          return resolve(null)
        }
        resolve(res.data)
      })
      .catch(() => resolve(null))
  })
}

const uploadFile = (filePath, fileName = 'image.jpg') => {
  const baseUrl = getBaseUrl()
  const token = getAuthToken()
  const { getCountry } = readCountryAndLocale()
  const country = getCountry() || 'default'

  return new Promise((resolve, reject) => {
    tt.uploadFile({
      url: `${baseUrl}/local/upload-async`,
      filePath,
      name: 'image',
      fileName,
      formData: { country },
      header: buildDefaultHeaders(),
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
  uploadFile,
  apiCall,
  buildDefaultHeaders,
  getBaseUrl,
  getAuthToken,
  getAppSafe
}
