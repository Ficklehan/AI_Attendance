#!/usr/bin/env node
/** HD task review page: yellow alert banner + table, 2x DPR PNG (no JPEG). */
import { chromium } from 'playwright'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(__dirname, '../..')
const OUT = path.join(ROOT, 'docs/geu-ai-manual/assets/04-task-review-detail.png')
const BASE = 'http://localhost:5175/clockai'
const TASK_ID = process.env.MANUAL_TASK_ID || '20260618_013'

async function login(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
  await page.evaluate(() => localStorage.clear())
  await page.reload({ waitUntil: 'networkidle' })
  await page.locator('input').first().fill(process.env.MANUAL_USER || 'admin')
  await page.locator('input[type="password"]').fill(process.env.MANUAL_PASS || 'admin123')
  await page.locator('button.login-btn').click()
  await page.waitForURL(/\/(home|tasks)/, { timeout: 20000 })
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await (await browser.newContext({
    viewport: { width: 1680, height: 1050 },
    deviceScaleFactor: 2,
  })).newPage()

  await login(page)
  await page.goto(`${BASE}/tasks/${TASK_ID}`, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForSelector('.edit-table .ant-table-row, .task-edit-container', { timeout: 30000 })
  await page.waitForTimeout(2000)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.waitForTimeout(500)

  const clip = await page.evaluate(() => {
    const main = document.querySelector('.task-edit-container') || document.querySelector('.page-content') || document.querySelector('main')
    if (!main) return null
    const r = main.getBoundingClientRect()
    return {
      x: Math.max(0, r.left - 4),
      y: Math.max(0, r.top - 4),
      width: Math.min(r.width + 8, window.innerWidth - r.left),
      height: Math.min(r.height + 8, 980),
    }
  })

  await page.screenshot({
    path: OUT,
    type: 'png',
    ...(clip ? { clip } : {}),
    animations: 'disabled',
    timeout: 60000,
  })

  await browser.close()
  console.log('saved', OUT, clip)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
