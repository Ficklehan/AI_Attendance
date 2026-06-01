const { isApiSuccess, getApiMessage } = require('./response')
const { t } = require('./i18n')
const { getCountry } = require('./preferences')

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

const request = (options) => {
  const { url, method = 'GET', data = {}, header = {} } = options
  const app = getAppSafe()
  const baseUrl = (app && app.globalData.baseUrl) || ''
  const token = (app && app.globalData.token) || ''

  const country = getCountry()
  const defaultHeader = {
    'Content-Type': 'application/json',
    'Authorization': token ? `Bearer ${token}` : ''
  }
  if (country) {
    defaultHeader['X-Country'] = country
  }

  return new Promise((resolve, reject) => {
    tt.request({
      url: `${baseUrl}${url}`,
      method,
      data,
      header: { ...defaultHeader, ...header },
      timeout: 30000,
      success: (res) => {
        if (res.statusCode === 200) {
          if (!isApiSuccess(res.data)) {
            tt.showToast({
              title: getApiMessage(res.data),
              icon: 'none'
            })
            return resolve(null)
          }
          resolve(res.data)
        } else {
          tt.showToast({
            title: t('common.networkError'),
            icon: 'none'
          })
          resolve(null)
        }
      },
      fail: (error) => {
        console.error('请求异常:', error)
        tt.showToast({
          title: t('common.networkFail'),
          icon: 'none'
        })
        resolve(null)
      }
    })
  })
}

const uploadFile = (filePath, fileName = 'image.jpg') => {
  const app = getAppSafe()
  const baseUrl = (app && app.globalData.baseUrl) || ''
  const token = (app && app.globalData.token) || ''
  const country = getCountry() || 'default'

  return new Promise((resolve, reject) => {
    tt.uploadFile({
      url: `${baseUrl}/local/upload-async`,
      filePath,
      name: 'image',
      fileName,
      formData: { country },
      header: {
        Authorization: token ? `Bearer ${token}` : '',
        'X-Country': country
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

module.exports = {
  request,
  uploadFile
}
