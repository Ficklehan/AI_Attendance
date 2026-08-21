#!/usr/bin/env node
/** Full task review capture: anomaly banner + all table columns (no horizontal scroll). */
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

async function prepareAndMeasure(page) {
  return page.evaluate(() => {
    const card = document.querySelector('.edit-card')
    const table = document.querySelector('.edit-table table')
    if (!card || !table) return null

    const style = document.createElement('style')
    style.id = 'manual-capture-expand'
    style.textContent = `
      html, body { overflow: visible !important; }
      .task-edit-container, .page-inner, .edit-card, .edit-tabs,
      .ant-tabs-content, .ant-tabs-tabpane, .edit-table,
      .edit-table .ant-table, .edit-table .ant-table-wrapper,
      .edit-table .ant-table-content, .edit-table .ant-table-body,
      .edit-table .ant-table-header, .edit-table .ant-table-container {
        overflow: visible !important;
        max-width: none !important;
      }
      .edit-table table { width: max-content !important; min-width: 100% !important; }
      .edit-table .ant-table-cell { white-space: nowrap; }
      .anomaly-detail-list { display: block !important; }
      .sticky-submit-bar, .ant-layout-sider, .nav { visibility: visible; }
    `
    document.head.appendChild(style)

    const scrollW = table.scrollWidth
    document.querySelectorAll(
      '.edit-table .ant-table-content, .edit-table .ant-table-body, .edit-table .ant-table-header, .edit-table .ant-table-container'
    ).forEach((el) => {
      el.style.width = `${scrollW}px`
      el.style.maxWidth = 'none'
      el.style.overflow = 'visible'
    })

    const cardRect = card.getBoundingClientRect()
    const tableRect = table.getBoundingClientRect()
    const left = Math.min(cardRect.left, tableRect.left)
    const right = Math.max(cardRect.right, tableRect.right)
    const width = right - left + 24
    const height = Math.min(card.scrollHeight + 32, 4000)

    return {
      scrollW,
      clipX: Math.max(0, left + window.scrollX - 12),
      clipY: Math.max(0, cardRect.top + window.scrollY - 12),
      clipW: width,
      clipH: height,
      rows: document.querySelectorAll('.edit-table .ant-table-row').length,
    }
  })
}

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' })
  const page = await (await browser.newContext({
    viewport: { width: 1680, height: 1050 },
    deviceScaleFactor: 2,
  })).newPage()

  await login(page)
  await page.goto(`${BASE}/tasks/${TASK_ID}`, { waitUntil: 'networkidle', timeout: 60000 })
  await page.waitForSelector('.edit-table .ant-table-row', { timeout: 30000 })
  await page.waitForTimeout(2000)
  await page.evaluate(() => window.scrollTo(0, 0))

  let metrics = await prepareAndMeasure(page)
  if (!metrics || metrics.rows === 0) throw new Error('Table rows not rendered')

  const viewW = Math.min(Math.max(metrics.clipW + 80, 1680), 4800)
  const viewH = Math.min(Math.max(metrics.clipH + 120, 1050), 4200)
  await page.setViewportSize({ width: viewW, height: viewH })
  await page.waitForTimeout(2500)
  await page.waitForSelector('.edit-table .ant-table-row', { timeout: 30000 })

  metrics = await prepareAndMeasure(page)
  if (!metrics || metrics.rows === 0) throw new Error('Table rows lost after viewport resize')

  await page.screenshot({
    path: OUT,
    type: 'png',
    clip: {
      x: metrics.clipX,
      y: metrics.clipY,
      width: Math.min(metrics.clipW, viewW - metrics.clipX - 4),
      height: Math.min(metrics.clipH, viewH - metrics.clipY - 4),
    },
    animations: 'disabled',
    timeout: 120000,
  })

  await browser.close()
  console.log('saved', OUT, metrics)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
