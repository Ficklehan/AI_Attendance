#!/usr/bin/env node
/**
 * 合并 common + 各端 overlay，生成 PC / 小程序语言包（勿手改生成文件）
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { PC_LOCALES, loadSplitLocale } from './locale-merge-utils.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

const PC_BANNER = `/** AUTO-GENERATED from shared/locales/{common,pc} — run: npm run build:locales */\n`
const MP_BANNER = `/** AUTO-GENERATED from shared/locales/{common,miniprogram} — run: npm run build:locales */\n`

function localeToConstName(locale) {
  return locale.replace(/-([a-zA-Z])/g, (_, c) => c.toUpperCase())
}

function writePcLocales() {
  const outDir = path.join(root, 'frontend/src/locales')
  for (const locale of PC_LOCALES) {
    const { pcMerged } = loadSplitLocale(root, locale)
    if (!Object.keys(pcMerged).length) {
      console.warn('skip PC', locale, '(empty)')
      continue
    }
    const out = path.join(outDir, `${locale}.js`)
    fs.writeFileSync(out, `${PC_BANNER}export default ${JSON.stringify(pcMerged, null, 2)}\n`)
    console.log('built PC', locale)
  }
}

function writeMpLocaleMessages() {
  const lines = [MP_BANNER]
  const exports = []
  for (const locale of PC_LOCALES) {
    const { mpMerged } = loadSplitLocale(root, locale)
    const file = path.join(root, 'shared/locales/miniprogram', `${locale}.json`)
    if (!fs.existsSync(file) && !Object.keys(mpMerged).length) {
      console.warn('skip MP', locale)
      continue
    }
    const constName = localeToConstName(locale)
    lines.push(`const ${constName} = ${JSON.stringify(mpMerged, null, 2)}`)
    lines.push('')
    exports.push(`  '${locale}': ${constName}`)
  }
  lines.push('module.exports = {')
  lines.push(exports.join(',\n'))
  lines.push('}')
  lines.push('')
  fs.writeFileSync(path.join(root, 'feishu-miniprogram/utils/localeMessages.js'), `${lines.join('\n')}\n`)
  console.log('built MP localeMessages.js')
}

writePcLocales()
writeMpLocaleMessages()
await import('./sync-miniprogram-shared.mjs')
console.log('build:locales complete')
