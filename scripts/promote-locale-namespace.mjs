#!/usr/bin/env node
/**
 * 将指定顶层 namespace 从 pc/ 提升到 common/（各语言）
 * 用法: node scripts/promote-locale-namespace.mjs validation
 */
import path from 'path'
import { fileURLToPath } from 'url'
import { PC_LOCALES, readJsonIfExists, writeJson, deepMerge } from './locale-merge-utils.mjs'

const ns = process.argv[2]
if (!ns) {
  console.error('Usage: promote-locale-namespace.mjs <namespace>')
  process.exit(1)
}

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

for (const locale of PC_LOCALES) {
  const commonPath = path.join(root, 'shared/locales/common', `${locale}.json`)
  const pcPath = path.join(root, 'shared/locales/pc', `${locale}.json`)
  const common = readJsonIfExists(commonPath) || {}
  const pc = readJsonIfExists(pcPath) || {}
  if (!(ns in pc)) {
    console.warn('skip', locale, `(no ${ns} in pc)`)
    continue
  }
  const promoted = { [ns]: pc[ns] }
  delete pc[ns]
  writeJson(commonPath, deepMerge(common, promoted))
  writeJson(pcPath, pc)
  console.log('promoted', locale, ns)
}

console.log('done — update shared-manifest fullNamespaces if needed, then npm run build:locales')
