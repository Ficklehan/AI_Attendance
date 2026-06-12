#!/usr/bin/env node
/**
 * 校验生成产物与 shared/locales 源一致（先 build 再 diff）
 */
import { execSync } from 'child_process'
import path from 'path'
import { fileURLToPath } from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

if (!process.argv.includes('--no-build')) {
  execSync('node scripts/build-locales.mjs', { cwd: root, stdio: 'inherit' })
}

const targets = [
  'frontend/src/locales',
  'feishu-miniprogram/utils/localeMessages.js',
]

let dirty = false
for (const rel of targets) {
  const out = execSync(`git diff --name-only -- ${rel}`, { cwd: root, encoding: 'utf8' }).trim()
  if (out) {
    dirty = true
    console.error('Generated locale files out of sync:', out.split('\n').join(', '))
  }
}

if (dirty) {
  console.error('\nRun: cd frontend && npm run build:locales — then commit generated files.')
  process.exit(1)
}

console.log('Generated locale files are in sync with shared/locales sources')
