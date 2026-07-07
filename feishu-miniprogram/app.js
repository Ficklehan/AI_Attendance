const { loadPreferences, syncCountryConfig, getCountry, redirectToCountrySetupIfNeeded } = require('./utils/preferences')
const { loadNightShiftRules } = require('./utils/nightShiftRules')
const { applyTabBarI18n, setLocale, DEFAULT_LOCALE } = require('./utils/i18n')
const { COUNTRIES } = require('./utils/countries')
const {
  baseUrl,
  resolveBaseUrl,
  USE_PUBLIC_API,
  LOCAL_BASE_URL,
  PUBLIC_BASE_URL,
  clearProdApiOverrideIfLocalDev
} = require('./config')

App({
  globalData: {
    token: '',
    userInfo: null,
    baseUrl,
    usePublicApi: USE_PUBLIC_API,
    localBaseUrl: LOCAL_BASE_URL,
    publicBaseUrl: PUBLIC_BASE_URL,
    currentCountry: '',
    countries: COUNTRIES,
    countryOptions: COUNTRIES,
    /** @type {string[]} 批量识别待处理本地图片路径 */
    recognitionQueue: [],
    imagePreviewSession: null,
    /** Incremented when tasks are confirmed/deleted so tab pages refresh summary */
    taskDataVersion: 0
  },

  onLaunch: function () {
    const savedLocale = tt.getStorageSync('locale')
    if (savedLocale) {
      setLocale(savedLocale, { skipTabBar: true })
    } else {
      setLocale(DEFAULT_LOCALE, { skipTabBar: true })
    }

    loadPreferences(this)

    const token = tt.getStorageSync('token')
    const userInfo = tt.getStorageSync('userInfo')
    if (token) {
      this.globalData.token = token
      syncCountryConfig(this).then(() => {
        if (userInfo) {
          redirectToCountrySetupIfNeeded()
        }
      }).catch((err) => {
        console.warn('启动时同步国家配置失败', err)
      })
    }
    if (userInfo) {
      this.globalData.userInfo = userInfo
    }

    applyTabBarI18n()
    clearProdApiOverrideIfLocalDev()
    this.refreshApiBaseUrl()
    loadNightShiftRules(false, getCountry()).catch(() => {})
    console.log('飞书小程序启动, API:', this.globalData.baseUrl, 'usePublicApi=', USE_PUBLIC_API)
  },

  refreshApiBaseUrl: function () {
    this.globalData.baseUrl = resolveBaseUrl()
    return this.globalData.baseUrl
  }
})
