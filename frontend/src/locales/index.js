import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN.js'
import enUS from './en-US.js'
import frFR from './fr-FR.js'
import nlNL from './nl-NL.js'
import csCZ from './cs-CZ.js'
import plPL from './pl-PL.js'
import deDE from './de-DE.js'
import esES from './es-ES.js'

const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
  'fr-FR': frFR,
  'nl-NL': nlNL,
  'cs-CZ': csCZ,
  'pl-PL': plPL,
  'de-DE': deDE,
  'es-ES': esES
}

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  // 各语言包未覆盖的 key 先回退英文，再回退中文，避免界面露出 tasks.xxx 键名
  fallbackLocale: {
    'zh-CN': ['zh-CN'],
    'en-US': ['en-US', 'zh-CN'],
    'fr-FR': ['en-US', 'zh-CN'],
    'nl-NL': ['en-US', 'zh-CN'],
    'cs-CZ': ['en-US', 'zh-CN'],
    'pl-PL': ['en-US', 'zh-CN'],
    'de-DE': ['en-US', 'zh-CN'],
    'es-ES': ['en-US', 'zh-CN'],
    default: ['en-US', 'zh-CN'],
  },
  messages,
})

export default i18n
