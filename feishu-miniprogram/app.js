const { loadPreferences, syncCountryConfig } = require('./utils/preferences')
const { applyTabBarI18n, setLocale, DEFAULT_LOCALE } = require('./utils/i18n')
const { COUNTRIES } = require('./utils/countries')
const { baseUrl } = require('./config')

App({
  globalData: {
    token: '',
    userInfo: null,
    baseUrl,
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
      syncCountryConfig(this).catch((err) => {
        console.warn('启动时同步国家配置失败', err)
      })
    }
    if (userInfo) {
      this.globalData.userInfo = userInfo
    }

    applyTabBarI18n()
    console.log('飞书小程序启动')
  }
})
