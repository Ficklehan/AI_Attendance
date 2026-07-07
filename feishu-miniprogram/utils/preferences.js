const { findCountry } = require('./countries')
const { t, formatCountryLabel } = require('./i18n')
const { translateApiError } = require('./translateError')
const {
  fetchCountryOptions,
  fetchCurrentCountry,
  updateCurrentCountry
} = require('./configApi')
const { needsWorkingCountrySetup, hasPersonalWorkingCountry } = require('./workingCountrySetup')

const STORAGE_COUNTRY = 'currentCountry'
const STORAGE_COUNTRY_CONFIGURED = 'countryConfigured'
const STORAGE_LOCALE = 'locale'

function getCountry() {
  const app = getAppSafe()
  return (app && app.globalData.currentCountry) || tt.getStorageSync(STORAGE_COUNTRY) || ''
}

function getUserInfoSafe() {
  const app = getAppSafe()
  return (app && app.globalData.userInfo) || tt.getStorageSync('userInfo') || null
}

function isCountryConfigured() {
  const userInfo = getUserInfoSafe()
  if (hasPersonalWorkingCountry(userInfo)) {
    return true
  }
  return !!getCountry() && !!tt.getStorageSync(STORAGE_COUNTRY_CONFIGURED) && getCountry() !== 'default'
}

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function applyCountryLocally(code, app) {
  const instance = app || getAppSafe()
  if (instance) {
    instance.globalData.currentCountry = code
  }
  tt.setStorageSync(STORAGE_COUNTRY, code)
  tt.setStorageSync(STORAGE_COUNTRY_CONFIGURED, true)
}

function getCountryLabel(code) {
  const item = findCountry(code)
  return formatCountryLabel(code, item && item.flag, item && item.name)
}

function applyCountryFromUserInfo(userInfo, options = {}) {
  if (!userInfo || needsWorkingCountrySetup(userInfo)) return
  const code = userInfo.personalWorkingCountry || userInfo.workingCountry
  if (!code || code === 'default') return
  applyCountryLocally(code, options.app)
}

function saveCountry(code, options = {}) {
  const app = options.app || getAppSafe()

  const applyLocal = () => {
    applyCountryLocally(code, app)
    if (app && app.globalData.userInfo) {
      app.globalData.userInfo = {
        ...app.globalData.userInfo,
        personalWorkingCountry: code,
        workingCountry: code,
      }
      try {
        tt.setStorageSync('userInfo', app.globalData.userInfo)
      } catch (e) {
        // ignore
      }
    }
    if (!options.silent) {
      tt.showToast({ title: t('settings.countrySaved'), icon: 'success' })
    }
  }

  if (!app || !app.globalData.token) {
    applyLocal()
    return Promise.resolve(code)
  }

  return updateCurrentCountry(code, app)
    .then(() => {
      applyLocal()
      return code
    })
    .catch((err) => {
      console.error('同步国家到服务端失败', err)
      tt.showToast({
        title: translateApiError({ message: err && err.message }, t('settings.countrySyncFail')),
        icon: 'none'
      })
      return Promise.reject(err)
    })
}

function getLocale() {
  const app = getAppSafe()
  if (app && app.globalData && app.globalData.locale) {
    return app.globalData.locale
  }
  return tt.getStorageSync(STORAGE_LOCALE) || 'zh-CN'
}

function saveLocale(locale) {
  const { setLocale } = require('./i18n')
  setLocale(locale)
  tt.setStorageSync(STORAGE_LOCALE, locale)
  const app = getAppSafe()
  if (app && app.globalData) {
    app.globalData.locale = locale
  }
}

function loadPreferences(app) {
  const country = tt.getStorageSync(STORAGE_COUNTRY)
  if (country) {
    app.globalData.currentCountry = country
  }
  const locale = tt.getStorageSync(STORAGE_LOCALE)
  if (locale) {
    const { setLocale } = require('./i18n')
    setLocale(locale, { skipTabBar: true })
    app.globalData.locale = locale
  }
}

/** 从服务端拉取与 PC 一致的当前工作国家 */
function syncCountryFromServer(app) {
  return fetchCurrentCountry(app).then((country) => {
    if (country) {
      applyCountryLocally(country, app)
    }
    return country
  })
}

/** 拉取国家列表 + 当前工作国家（与 PC 配置页同源） */
function syncCountryConfig(app) {
  return fetchCountryOptions(app).then(() => syncCountryFromServer(app))
}

function ensureCountryConfigured() {
  if (!needsWorkingCountrySetup(getUserInfoSafe()) && isCountryConfigured()) {
    return true
  }
  tt.redirectTo({ url: '/pages/settings/index?setup=1' })
  return false
}

function redirectToCountrySetupIfNeeded() {
  if (!needsWorkingCountrySetup(getUserInfoSafe()) && isCountryConfigured()) {
    return false
  }
  tt.redirectTo({ url: '/pages/settings/index?setup=1' })
  return true
}

module.exports = {
  STORAGE_COUNTRY,
  STORAGE_COUNTRY_CONFIGURED,
  STORAGE_LOCALE,
  getCountry,
  getLocale,
  isCountryConfigured,
  getCountryLabel,
  saveCountry,
  saveLocale,
  loadPreferences,
  syncCountryFromServer,
  syncCountryConfig,
  ensureCountryConfigured,
  redirectToCountrySetupIfNeeded,
  fetchCountryOptions,
  applyCountryFromUserInfo
}
