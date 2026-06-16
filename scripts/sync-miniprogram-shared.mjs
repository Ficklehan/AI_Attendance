#!/usr/bin/env node
/**
 * 将 shared/js/*.cjs 同步到 feishu-miniprogram/shared-js/*.js
 * 飞书 BIZPACK 不支持 .cjs 扩展名，也无法稳定引用 miniprogramRoot 外的文件。
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const srcDir = path.join(root, 'shared/js')
const outDir = path.join(root, 'feishu-miniprogram/shared-js')
const banner = '/** AUTO-GENERATED from shared/js — run: npm run sync:miniprogram-shared */\n'

const markTokensSrc = path.join(root, 'shared/locales/mark-tokens.json')
const markTokensDest = path.join(root, 'feishu-miniprogram/shared-locales/mark-tokens.js')

fs.mkdirSync(outDir, { recursive: true })
fs.mkdirSync(path.join(root, 'feishu-miniprogram/shared-locales'), { recursive: true })

for (const name of fs.readdirSync(srcDir)) {
  if (!name.endsWith('.cjs')) continue
  const base = name.slice(0, -4)
  const src = path.join(srcDir, name)
  const dest = path.join(outDir, `${base}.js`)
  const body = fs.readFileSync(src, 'utf8')
  const adapted = body.replace(/require\((['"])(\.\/[^'"]+)\.cjs\1\)/g, 'require($1$2$1)')
  fs.writeFileSync(dest, `${banner}${adapted}`)
  console.log('synced', `${base}.js`)
}

if (fs.existsSync(markTokensSrc)) {
  const tokens = JSON.parse(fs.readFileSync(markTokensSrc, 'utf8'))
  fs.writeFileSync(
    markTokensDest,
    `${banner}module.exports = ${JSON.stringify(tokens, null, 2)}\n`,
  )
  console.log('synced', 'shared-locales/mark-tokens.js')
}

console.log('sync:miniprogram-shared complete')
