const messages = require('./localeMessages')
const { COUNTRIES } = require('./countries')

const SUPPORTED_LOCALES = ['zh-CN', 'en-US', 'fr-FR', 'nl-NL', 'cs-CZ', 'pl-PL', 'de-DE', 'es-ES']
const DEFAULT_LOCALE = 'zh-CN'

let currentLocale = DEFAULT_LOCALE

function getLocale() {
  return currentLocale
}

function setLocale(locale, options = {}) {
  if (SUPPORTED_LOCALES.includes(locale)) {
    currentLocale = locale
  }
  if (!options.skipTabBar) {
    applyTabBarI18n()
  }
}

function getMessage(locale, key) {
  const parts = key.split('.')
  const locales = [locale, 'en-US', DEFAULT_LOCALE]
  for (const loc of locales) {
    let node = messages[loc]
    if (!node) continue
    for (const part of parts) {
      if (!node || typeof node !== 'object') {
        node = null
        break
      }
      node = node[part]
    }
    if (typeof node === 'string') return node
  }
  return null
}

function t(key, params) {
  let text = getMessage(currentLocale, key) || getMessage(DEFAULT_LOCALE, key) || key
  if (params && typeof params === 'object') {
    Object.keys(params).forEach((k) => {
      text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), String(params[k]))
    })
  }
  return text
}

function localeToLanguageKey(locale) {
  const map = {
    'zh-CN': 'zhCN',
    'en-US': 'enUS',
    'fr-FR': 'frFR',
    'nl-NL': 'nlNL',
    'cs-CZ': 'csCZ',
    'pl-PL': 'plPL',
    'de-DE': 'deDE',
    'es-ES': 'esES'
  }
  return map[locale] || 'zhCN'
}

function getLanguageOptionsFixed() {
  return SUPPORTED_LOCALES.map((value) => ({
    value,
    label: t(`language.${localeToLanguageKey(value)}`)
  }))
}

function getCountriesForPicker() {
  let app = null
  try {
    app = getApp()
  } catch (e) {
    app = null
  }
  const options = (app && app.globalData && app.globalData.countryOptions) || COUNTRIES
  return options.map((c) => ({
    code: c.code,
    flag: c.flag,
    name: t(c.code === 'default' ? 'country.default' : `country.${c.code}`)
  }))
}

function applyTabBarI18n() {
  const tabs = [
    { index: 0, text: t('tab.recognize') },
    { index: 1, text: t('tab.tasks') },
    { index: 2, text: t('tab.profile') }
  ]
  tabs.forEach((item) => {
    try {
      tt.setTabBarItem(item)
    } catch (e) {
      console.warn('setTabBarItem failed', e)
    }
  })
}

module.exports = {
  SUPPORTED_LOCALES,
  DEFAULT_LOCALE,
  getLocale,
  setLocale,
  t,
  getLanguageOptions: getLanguageOptionsFixed,
  getCountriesForPicker,
  applyTabBarI18n,
  localeToLanguageKey
}
