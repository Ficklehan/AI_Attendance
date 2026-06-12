#!/usr/bin/env node
/**
 * 从现有生成 JS 拉回 JSON，并拆分为 common / pc / miniprogram
 */
import path from 'path'
import { fileURLToPath, pathToFileURL } from 'url'
import { createRequire } from 'module'
import {
  PC_LOCALES,
  splitLocaleSources,
  writeJson,
} from './locale-merge-utils.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(import.meta.url)

const pcFullByLocale = {}
for (const locale of PC_LOCALES) {
  const file = path.join(root, 'frontend/src/locales', `${locale}.js`)
  const mod = await import(pathToFileURL(file).href)
  pcFullByLocale[locale] = mod.default
}

const mpMessages = require(path.join(root, 'feishu-miniprogram/utils/localeMessages.js'))

for (const locale of PC_LOCALES) {
  const pcFull = pcFullByLocale[locale]
  const mpFull = mpMessages[locale]
  if (!pcFull) continue
  const { common, pc, mp } = splitLocaleSources(pcFull, mpFull || {})
  writeJson(path.join(root, 'shared/locales/common', `${locale}.json`), common)
  writeJson(path.join(root, 'shared/locales/pc', `${locale}.json`), pc)
  if (mpFull) {
    writeJson(path.join(root, 'shared/locales/miniprogram', `${locale}.json`), mp)
  }
  console.log('extracted', locale)
}

console.log('extract-locales complete')
