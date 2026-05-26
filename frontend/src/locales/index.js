import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.js'
import enUS from './en-US.js'
import frFR from './fr-FR.js'
import nlNL from './nl-NL.js'
import csCZ from './cs-CZ.js'
import plPL from './pl-PL.js'
import deDE from './de-DE.js'

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
  'fr-FR': frFR,
  'nl-NL': nlNL,
  'cs-CZ': csCZ,
  'pl-PL': plPL,
  'de-DE': deDE
}

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages
})

export default i18n
