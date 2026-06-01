const { isApiSuccess, getApiData, getApiMessage } = require('./response')
const { COUNTRIES, mergeCountryOptions } = require('./countries')
const { t } = require('./i18n')
const { translateApiError } = require('./translateError')

function getAppInstance(app) {
  if (app && app.globalData) {
    return app
  }
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function authHeader(app) {
  const instance = getAppInstance(app)
  if (!instance || !instance.globalData.token) {
    return {}
  }
  return { Authorization: `Bearer ${instance.globalData.token}` }
}

function fetchCountryOptions(app) {
  return new Promise((resolve) => {
    const instance = getAppInstance(app)
    if (!instance || !instance.globalData.token) {
      resolve(mergeCountryOptions([]))
      return
    }

    tt.request({
      url: `${instance.globalData.baseUrl}/config/country-options`,
      method: 'GET',
      timeout: 15000,
      header: authHeader(app),
      success: (res) => {
        if (isApiSuccess(res.data)) {
          const list = getApiData(res.data) || []
          instance.globalData.countryOptions = mergeCountryOptions(list)
          resolve(instance.globalData.countryOptions)
          return
        }
        resolve(mergeCountryOptions([]))
      },
      fail: () => resolve(mergeCountryOptions([]))
    })
  })
}

function fetchCurrentCountry(app) {
  return new Promise((resolve) => {
    const instance = getAppInstance(app)
    if (!instance || !instance.globalData.token) {
      resolve(null)
      return
    }

    tt.request({
      url: `${instance.globalData.baseUrl}/config/current-country`,
      method: 'GET',
      timeout: 15000,
      header: authHeader(app),
      success: (res) => {
        if (isApiSuccess(res.data)) {
          const payload = getApiData(res.data) || {}
          resolve(payload.country || null)
          return
        }
        resolve(null)
      },
      fail: () => resolve(null)
    })
  })
}

function updateCurrentCountry(country, app) {
  return new Promise((resolve, reject) => {
    const instance = getAppInstance(app)
    if (!instance || !instance.globalData.token) {
      reject(new Error(t('errors.loginRequired')))
      return
    }

    tt.request({
      url: `${instance.globalData.baseUrl}/config/current-country`,
      method: 'PUT',
      timeout: 15000,
      header: {
        ...authHeader(app),
        'Content-Type': 'application/json'
      },
      data: JSON.stringify({ country }),
      success: (res) => {
        if (res.statusCode === 200 && isApiSuccess(res.data)) {
          resolve(country)
          return
        }
        reject(new Error(getApiMessage(res.data, t('settings.countrySyncFail'))))
      },
      fail: (err) => {
        reject(new Error(translateApiError({ message: (err && err.errMsg) || (err && err.message) }, t('errors.networkError'))))
      }
    })
  })
}

module.exports = {
  fetchCountryOptions,
  fetchCurrentCountry,
  updateCurrentCountry,
  getAppInstance
}
