#!/usr/bin/env node
/**
 * 迁移用：从完整 PC（legacy messages/）+ 完整 MP 拆分为 common / pc / miniprogram overlay。
 * 日常请直接编辑 shared/locales/{common,pc,miniprogram}/ 后 npm run build:locales
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import {
  PC_LOCALES,
  splitLocaleSources,
  writeJson,
  readJsonIfExists,
} from './locale-merge-utils.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const legacyPcDir = path.join(root, 'shared/locales/messages')
const legacyMpDir = path.join(root, 'shared/locales/miniprogram')

for (const locale of PC_LOCALES) {
  const pcFull = readJsonIfExists(path.join(legacyPcDir, `${locale}.json`))
  const mpFull = readJsonIfExists(path.join(legacyMpDir, `${locale}.json`))
  if (!pcFull) {
    console.warn('skip', locale, '(no PC source)')
    continue
  }
  const { common, pc, mp } = splitLocaleSources(pcFull, mpFull || {})
  writeJson(path.join(root, 'shared/locales/common', `${locale}.json`), common)
  writeJson(path.join(root, 'shared/locales/pc', `${locale}.json`), pc)
  if (mpFull) {
    writeJson(path.join(root, 'shared/locales/miniprogram', `${locale}.json`), mp)
  }
  console.log('split', locale, {
    commonKeys: Object.keys(common).length,
    pcKeys: Object.keys(pc).length,
    mpKeys: Object.keys(mp).length,
  })
}

console.log('split-locale-sources complete')
