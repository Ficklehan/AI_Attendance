const App = getApp()
const { t, getLocale, setLocale, getLanguageOptions, getCountriesForPicker } = require('../../utils/i18n')
const {
  getCountry,
  getCountryLabel,
  saveCountry,
  saveLocale,
  isCountryConfigured,
  syncCountryConfig
} = require('../../utils/preferences')

Page({
  data: {
    isSetup: false,
    selectedCountry: '',
    selectedLocale: 'zh-CN',
    showCountryOptions: false,
    showLanguageOptions: false,
    currentCountry: '',
    currentCountryLabel: '',
    currentLocaleLabel: '',
    countries: [],
    languages: [],
    texts: {},
    loading: true,
    saving: false
  },

  onLoad: function (options) {
    const isSetup = options.setup === '1'
    this.setData({
      isSetup,
      showCountryOptions: isSetup,
      showLanguageOptions: isSetup
    })
    this.loadConfig()
  },

  onShow: function () {
    this.loadConfig()
  },

  loadConfig: function () {
    this.setData({ loading: true })
    syncCountryConfig().finally(() => {
      const currentCountry = getCountry() || App.globalData.currentCountry || ''
      const locale = getLocale()
      const languageOptions = getLanguageOptions()
      const selectedCountry = currentCountry || (this.data.isSetup ? '' : 'default')
      this.setData({
        loading: false,
        texts: this.buildTexts(),
        countries: getCountriesForPicker(),
        languages: languageOptions,
        selectedCountry,
        selectedLocale: locale,
        currentCountry,
        currentCountryLabel: currentCountry ? getCountryLabel(currentCountry) : '',
        currentLocaleLabel: (languageOptions.find((item) => item.value === locale) || {}).label || locale
      })
      tt.setNavigationBarTitle({ title: t('settings.title') })
    })
  },

  buildTexts: function () {
    return {
      title: t('settings.title'),
      setupTitle: t('settings.setupTitle'),
      setupDesc: t('settings.setupDesc'),
      countryTitle: t('settings.countryTitle'),
      countryChangeAction: t('settings.countryChangeAction'),
      countryUnset: t('settings.countryUnset'),
      countryDesc: t('settings.countryDesc'),
      countryCurrent: t('settings.countryCurrent'),
      countryImpact: t('settings.countryImpact'),
      languageTitle: t('settings.languageTitle'),
      languageChangeAction: t('settings.languageChangeAction'),
      languageDesc: t('settings.languageDesc'),
      languageImpact: t('settings.languageImpact'),
      save: t('settings.save'),
      saving: t('settings.saving'),
      loading: t('common.loading')
    }
  },

  toggleCountryOptions: function () {
    this.setData({ showCountryOptions: !this.data.showCountryOptions })
  },

  toggleLanguageOptions: function () {
    this.setData({ showLanguageOptions: !this.data.showLanguageOptions })
  },

  onSelectCountry: function (e) {
    this.setData({ selectedCountry: e.currentTarget.dataset.code })
  },

  onSelectLanguage: function (e) {
    const value = e.currentTarget.dataset.value
    saveLocale(value)
    const languageOptions = getLanguageOptions()
    this.setData({ selectedLocale: value })
    this.setData({
      texts: this.buildTexts(),
      countries: getCountriesForPicker(),
      languages: languageOptions,
      currentLocaleLabel: (languageOptions.find((item) => item.value === value) || {}).label || value
    })
    tt.setNavigationBarTitle({ title: t('settings.title') })
    tt.showToast({ title: t('settings.languageSaved'), icon: 'success' })
  },

  onSave: function () {
    if (this.data.saving || this.data.loading) return
    const { selectedCountry, selectedLocale, isSetup } = this.data
    if (!selectedCountry) {
      tt.showToast({ title: t('settings.mustSelectCountry'), icon: 'none' })
      return
    }

    const applySave = () => {
      this.setData({ saving: true })
      saveLocale(selectedLocale)
      saveCountry(selectedCountry, { silent: isSetup })
        .then(() => {
          if (!isSetup) {
            setTimeout(() => tt.navigateBack(), 300)
            return
          }
          tt.switchTab({ url: '/pages/index/index' })
        })
        .catch(() => {})
        .finally(() => {
          this.setData({ saving: false })
        })
    }

    const previous = getCountry()
    if (!isSetup && previous && previous !== selectedCountry && isCountryConfigured()) {
      tt.showModal({
        title: t('settings.countryChangeTitle'),
        content: t('settings.countryChangeContent'),
        confirmText: t('common.confirm'),
        cancelText: t('common.cancel'),
        success: (res) => {
          if (res.confirm) applySave()
        }
      })
      return
    }

    applySave()
  }
})
