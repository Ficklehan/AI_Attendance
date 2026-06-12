#!/usr/bin/env node
/**
 * 校验 PC 端 zh-CN / en-US 语言包键是否一致（防止漏翻）
 */
import path from 'path'
import { fileURLToPath, pathToFileURL } from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const localesDir = path.join(root, 'frontend/src/locales')

function flattenKeys(obj, prefix = '') {
  const keys = []
  if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) {
    return keys
  }
  for (const [key, value] of Object.entries(obj)) {
    const full = prefix ? `${prefix}.${key}` : key
    if (value != null && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenKeys(value, full))
    } else {
      keys.push(full)
    }
  }
  return keys
}

async function loadLocale(file) {
  const mod = await import(pathToFileURL(path.join(localesDir, file)).href)
  return mod.default
}

const zh = await loadLocale('zh-CN.js')
const en = await loadLocale('en-US.js')

const zhKeys = new Set(flattenKeys(zh))
const enKeys = new Set(flattenKeys(en))

const onlyZh = [...zhKeys].filter((k) => !enKeys.has(k)).sort()
const onlyEn = [...enKeys].filter((k) => !zhKeys.has(k)).sort()

if (onlyZh.length || onlyEn.length) {
  console.error('Locale key mismatch between zh-CN.js and en-US.js')
  if (onlyZh.length) {
    console.error(`\nOnly in zh-CN (${onlyZh.length}):`)
    onlyZh.slice(0, 50).forEach((k) => console.error(`  - ${k}`))
    if (onlyZh.length > 50) console.error(`  ... and ${onlyZh.length - 50} more`)
  }
  if (onlyEn.length) {
    console.error(`\nOnly in en-US (${onlyEn.length}):`)
    onlyEn.slice(0, 50).forEach((k) => console.error(`  - ${k}`))
    if (onlyEn.length > 50) console.error(`  ... and ${onlyEn.length - 50} more`)
  }
  process.exit(1)
}

console.log(`Locale parity OK (${zhKeys.size} keys)`)
