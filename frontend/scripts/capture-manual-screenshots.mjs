#!/usr/bin/env node
/** Capture / refresh manual screenshots. */
import { chromium } from 'playwright'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const OUT = path.join(ROOT, 'docs/geu-ai-manual/assets')
const BASE = 'http://localhost:5175/attendance'
const TASK_ID = process.env.MANUAL_TASK_ID || '20260618_013'

const CORE = [
  { file: '02-home-upload.png', url: `${BASE}/home`, wait: 2000 },
  { file: '03-tasks-list.png', url: `${BASE}/tasks`, wait: 2500 },
  { file: '04-task-review-full.png', url: `${BASE}/tasks/${TASK_ID}`, wait: 4000, wide: true },
  { file: '05-task-records.png', url: `${BASE}/task-records`, wait: 2500 },
]

const SETTINGS = [
  { file: '07-settings-ai.png', url: `${BASE}/settings/ai`, wait: 2500 },
  { file: '08-settings-feishu.png', url: `${BASE}/settings/feishu`, wait: 2500 },
  { file: '09-settings-reminders.png', url: `${BASE}/settings/reminders`, wait: 2500 },
]

const MINI = [
  { file: '12-mini-home.png', src: 'docs/sop/previews/mini-home.html' },
  { file: '13-mini-processing.png', src: 'docs/sop/previews/mini-processing.html' },
  { file: '14-mini-result.png', src: 'docs/sop/previews/mini-result.html' },
  { file: '15-mini-tasks.png', src: 'docs/sop/previews/mini-tasks.html' },
]

fs.mkdirSync(OUT, { recursive: true })

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.evaluate(() => localStorage.clear())
  await page.reload({ waitUntil: 'networkidle' })
  await page.locator('input').first().fill(process.env.MANUAL_USER || 'admin')
  await page.locator('input[type="password"]').fill(process.env.MANUAL_PASS || 'admin123')
  await page.locator('button.login-btn').click()
  await page.waitForURL(/\/(home|tasks)/, { timeout: 20000 })
}

async function capture(page, item) {
  await page.goto(item.url, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForTimeout(item.wait)
  if (item.wide) {
    await page.evaluate(() => window.scrollTo(0, 280))
    await page.waitForTimeout(600)
  }
  await page.screenshot({
    path: path.join(OUT, item.file),
    fullPage: !!item.fullPage,
    animations: 'disabled',
    timeout: 60000,
  })
  console.log('saved', item.file)
}

async function captureMini(page, item) {
  await page.setViewportSize({ width: 430, height: 932 })
  await page.goto(`file://${path.join(ROOT, item.src)}`, { waitUntil: 'domcontentloaded', timeout: 15000 })
  await page.waitForTimeout(1000)
  await page.screenshot({ path: path.join(OUT, item.file), fullPage: true, animations: 'disabled' })
  console.log('saved', item.file)
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await (await browser.newContext({
    viewport: { width: 1680, height: 1050 },
    deviceScaleFactor: 2,
  })).newPage()

  await login(page)
  for (const item of CORE) await capture(page, item)
  for (const item of SETTINGS) await capture(page, item)
  for (const item of MINI) await captureMini(page, item)

  await browser.close()
  console.log('Done ->', OUT)
}

main().catch((e) => { console.error(e); process.exit(1) })
