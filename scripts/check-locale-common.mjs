#!/usr/bin/env node
/**
 * 校验 common 语言包 zh-CN / en-US 键一致
 */
import path from 'path'
import { fileURLToPath } from 'url'
import { readJsonIfExists } from './locale-merge-utils.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function flattenKeys(obj, prefix = '') {
  const keys = []
  if (obj == null || typeof obj !== 'object' || Array.isArray(obj)) return keys
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

const zh = readJsonIfExists(path.join(root, 'shared/locales/common/zh-CN.json')) || {}
const en = readJsonIfExists(path.join(root, 'shared/locales/common/en-US.json')) || {}

const zhKeys = new Set(flattenKeys(zh))
const enKeys = new Set(flattenKeys(en))
const onlyZh = [...zhKeys].filter((k) => !enKeys.has(k)).sort()
const onlyEn = [...enKeys].filter((k) => !zhKeys.has(k)).sort()

if (onlyZh.length || onlyEn.length) {
  console.error('Common locale key mismatch (zh-CN vs en-US)')
  onlyZh.slice(0, 30).forEach((k) => console.error('  only zh:', k))
  onlyEn.slice(0, 30).forEach((k) => console.error('  only en:', k))
  process.exit(1)
}

console.log(`Common locale parity OK (${zhKeys.size} keys)`)
