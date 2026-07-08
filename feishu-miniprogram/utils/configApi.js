const { isApiSuccess, getApiData, getApiMessage } = require('./response')
const { mergeCountryOptions } = require('./countries')
const { t } = require('./i18n')
const { translateApiError } = require('./translateError')
const { apiCall, getAppSafe, getAuthToken } = require('./request')

const STORAGE_COUNTRY = 'currentCountry'

function getAppInstance(app) {
  if (app && app.globalData) {
    return app
  }
  return getAppSafe()
}

function isCurrentUserAdmin(app) {
  const instance = getAppInstance(app)
  const userInfo = instance && instance.globalData.userInfo
  if (!userInfo) return false
  const roles = Array.isArray(userInfo.roles) && userInfo.roles.length
    ? userInfo.roles
    : (userInfo.role ? [userInfo.role] : [])
  return roles.includes('admin')
}

function readLocalCountry() {
  try {
    return tt.getStorageSync(STORAGE_COUNTRY) || ''
  } catch (e) {
    return ''
  }
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

/** 与 PC country store 一致：拉取 profile 同步 personalWorkingCountry，再解析当前工作国家 */
function fetchCurrentCountry(app) {
  const instance = getAppInstance(app)
  if (!instance || !getAuthToken()) {
    return Promise.resolve(null)
  }

  const localCountry = readLocalCountry()

  return apiCall({ url: '/auth/profile', timeout: 15000 })
    .then((res) => {
      let personalCountry = null
      if (isApiSuccess(res.data)) {
        const user = getApiData(res.data) || {}
        if (user.id) {
          instance.globalData.userInfo = Object.assign({}, instance.globalData.userInfo || {}, user)
          try {
            tt.setStorageSync('userInfo', instance.globalData.userInfo)
          } catch (e) {
            // ignore
          }
        }
        personalCountry = user.personalWorkingCountry || null
      }
      if (personalCountry && personalCountry !== 'default') {
        return personalCountry
      }
      if (localCountry && localCountry !== 'default') {
        return localCountry
      }
      return personalCountry || localCountry || 'default'
    })
    .catch(() => {
      if (localCountry && localCountry !== 'default') {
        return localCountry
      }
      return localCountry || 'default'
    })
}

function postCountryUpdate(url, country) {
  return apiCall({
    url,
    method: 'POST',
    data: { country },
    timeout: 15000
  }).then((res) => {
    if (!isApiSuccess(res.data)) {
      throw new Error(getApiMessage(res.data, t('settings.countrySyncFail')))
    }
    const payload = getApiData(res.data) || {}
    return payload.country || country
  })
}

function updateCurrentCountry(country, app, options = {}) {
  const instance = getAppInstance(app)
  if (!instance || !getAuthToken()) {
    return Promise.reject(new Error(t('errors.loginRequired')))
  }

  const admin = isCurrentUserAdmin(instance)
  const url = options.global === true && admin ? '/config/current-country' : '/auth/working-country'

  return postCountryUpdate(url, country)
    .then((effective) => {
      if (instance.globalData.userInfo) {
        instance.globalData.userInfo.workingCountry = effective
        if (options.global !== true) {
          instance.globalData.userInfo.personalWorkingCountry =
            country && country !== 'default' ? country : null
        }
        try {
          tt.setStorageSync('userInfo', instance.globalData.userInfo)
        } catch (e) {
          // ignore
        }
      }
      return country && String(country).trim() ? String(country).trim() : 'default'
    })
    .catch((err) => {
      const message = (err && err.message) || ''
      const is404 = message.indexOf('404') !== -1
      // 生产环境若尚未部署 /auth/working-country，仅保存本地（请求头 X-Country 仍生效）
      if (!admin && is404) {
        console.warn('[configApi] /auth/working-country 不可用，已回退为本地保存')
        return country
      }
      if (message.indexOf('login') !== -1) {
        throw err
      }
      throw new Error(translateApiError(
        { message: (err && err.errMsg) || message },
        t('settings.countrySyncFail')
      ))
    })
}

module.exports = {
  fetchCountryOptions,
  fetchCurrentCountry,
  updateCurrentCountry,
  getAppInstance,
  isCurrentUserAdmin
}
