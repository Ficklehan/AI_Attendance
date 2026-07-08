#!/usr/bin/env node
/**
 * 校验 feishu-miniprogram/shared-js 是否与 shared/js 同步。
 * 用法：node scripts/check-miniprogram-shared-sync.mjs [--fix]
 */
import { execSync } from 'child_process'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sharedDir = path.join(root, 'shared/js')
const miniDir = path.join(root, 'feishu-miniprogram/shared-js')
const fix = process.argv.includes('--fix')

function adaptForMini(body) {
  return body.replace(/require\((['"])(\.\/[^'"]+)\.cjs\1\)/g, 'require($1$2$1)')
}

if (fix) {
  execSync('npm run sync:miniprogram-shared', { cwd: root, stdio: 'inherit' })
}

const cjsFiles = fs.readdirSync(sharedDir).filter((f) => f.endsWith('.cjs')).sort()
const missing = []
const drift = []

for (const cjs of cjsFiles) {
  const base = cjs.replace(/\.cjs$/, '')
  const jsPath = path.join(miniDir, `${base}.js`)
  if (!fs.existsSync(jsPath)) {
    missing.push(base)
    continue
  }
  const expected = adaptForMini(fs.readFileSync(path.join(sharedDir, cjs), 'utf8').trim())
  const actual = fs.readFileSync(jsPath, 'utf8')
    .replace(/^\/\*\* AUTO-GENERATED.*?\*\/\n?/s, '')
    .trim()
  if (expected !== actual) {
    drift.push(base)
  }
}

if (missing.length || drift.length) {
  console.error('shared/js ↔ feishu-miniprogram/shared-js out of sync')
  if (missing.length) console.error('  missing mirrors:', missing.join(', '))
  if (drift.length) console.error('  content drift:', drift.join(', '))
  console.error('Run: npm run sync:miniprogram-shared')
  process.exit(1)
}

console.log(`miniprogram shared-js in sync (${cjsFiles.length} modules)`)
