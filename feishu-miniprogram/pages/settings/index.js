const App = getApp()
const { t, getLocale, setLocale, getLanguageOptions, getCountriesForPicker } = require('../../utils/i18n')
const {
  getCountry,
  getCountryLabel,
  saveCountry,
  saveLocale,
  isCountryConfigured,
  syncCountryConfig,
  redirectToCountrySetupIfNeeded,
} = require('../../utils/preferences')
const { needsWorkingCountrySetup } = require('../../utils/workingCountrySetup')

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
      const selectedCountry = currentCountry || (this.data.isSetup ? '' : 'default')
      this.setData({
        loading: false,
        selectedCountry,
        currentCountry,
        ...this.buildLocalizedState(currentCountry)
      })
      tt.setNavigationBarTitle({ title: t('settings.title') })
    })
  },

  buildLocalizedState: function (currentCountry) {
    const locale = getLocale()
    const languageOptions = getLanguageOptions()
    const countries = getCountriesForPicker().filter((item) => (
      !this.data.isSetup || (item.code && item.code !== 'default')
    ))
    return {
      texts: this.buildTexts(),
      countries,
      languages: languageOptions,
      selectedLocale: locale,
      currentCountryLabel: currentCountry ? getCountryLabel(currentCountry) : '',
      currentLocaleLabel: (languageOptions.find((item) => item.value === locale) || {}).label || locale
    }
  },

  refreshLocalizedLabels: function () {
    const currentCountry = this.data.currentCountry || getCountry() || ''
    this.setData(this.buildLocalizedState(currentCountry))
    tt.setNavigationBarTitle({ title: t('settings.title') })
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
    const next = !this.data.showCountryOptions
    if (next) {
      this.refreshLocalizedLabels()
    }
    this.setData({ showCountryOptions: next })
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
    this.setData({ selectedLocale: value })
    this.refreshLocalizedLabels()
    tt.showToast({ title: t('settings.languageSaved'), icon: 'success' })
  },

  onSave: function () {
    if (this.data.saving || this.data.loading) return
    const { selectedCountry, selectedLocale, isSetup } = this.data
    if (!selectedCountry || selectedCountry === 'default') {
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
  },

  onBackPress: function () {
    if (this.data.isSetup && needsWorkingCountrySetup()) {
      tt.showToast({ title: t('settings.mustSelectCountry'), icon: 'none' })
      return true
    }
    return false
  }
})
