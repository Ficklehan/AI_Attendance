import { chromium } from '@playwright/test';
import path from 'path';

const outDir = path.join(process.cwd(), 'tmp-screenshots');
import fs from 'fs';
fs.mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();

const pages = [
  { name: 'login', url: 'http://localhost:5175/login' },
  { name: 'home-redirect', url: 'http://localhost:5175/home' },
];

for (const p of pages) {
  await page.goto(p.url, { waitUntil: 'networkidle', timeout: 20000 }).catch(async () => {
    await page.goto(p.url, { waitUntil: 'domcontentloaded', timeout: 20000 });
  });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(outDir, `${p.name}.png`), fullPage: true });
}

await browser.close();
console.log('Saved', outDir);
