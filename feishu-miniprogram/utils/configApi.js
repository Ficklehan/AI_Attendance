const { isApiSuccess, getApiData, getApiMessage } = require('./response')
const { mergeCountryOptions } = require('./countries')
const { t } = require('./i18n')
const { translateApiError } = require('./translateError')
const { apiCall, getAppSafe, getAuthToken } = require('./request')

function getAppInstance(app) {
  if (app && app.globalData) {
    return app
  }
  return getAppSafe()
}

function fetchCountryOptions(app) {
  const instance = getAppInstance(app)
  if (!instance || !getAuthToken()) {
    return Promise.resolve(mergeCountryOptions([]))
  }
  return apiCall({ url: '/config/country-options', timeout: 15000 })
    .then((res) => {
      if (isApiSuccess(res.data)) {
        const list = getApiData(res.data) || []
        instance.globalData.countryOptions = mergeCountryOptions(list)
        return instance.globalData.countryOptions
      }
      return mergeCountryOptions([])
    })
    .catch(() => mergeCountryOptions([]))
}

function fetchCurrentCountry(app) {
  const instance = getAppInstance(app)
  if (!instance || !getAuthToken()) {
    return Promise.resolve(null)
  }
  return apiCall({ url: '/config/current-country', timeout: 15000 })
    .then((res) => {
      if (isApiSuccess(res.data)) {
        const payload = getApiData(res.data) || {}
        return payload.country || null
      }
      return null
    })
    .catch(() => null)
}

function updateCurrentCountry(country, app) {
  const instance = getAppInstance(app)
  if (!instance || !getAuthToken()) {
    return Promise.reject(new Error(t('errors.loginRequired')))
  }
  return apiCall({
    url: '/config/current-country',
    method: 'PUT',
    data: { country },
    timeout: 15000
  }).then((res) => {
    if (isApiSuccess(res.data)) {
      return country
    }
    throw new Error(getApiMessage(res.data, t('settings.countrySyncFail')))
  }).catch((err) => {
    if (err && err.message && err.message.indexOf('login') !== -1) {
      throw err
    }
    throw new Error(translateApiError(
      { message: (err && err.errMsg) || (err && err.message) },
      t('errors.networkError')
    ))
  })
}

module.exports = {
  fetchCountryOptions,
  fetchCurrentCountry,
  updateCurrentCountry,
  getAppInstance
}
