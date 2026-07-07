#!/usr/bin/env python3
"""Capture warehouse-clerk SOP screenshots (PC + Feishu mini-program previews)."""

from __future__ import annotations

import asyncio
from pathlib import Path

try:
    from playwright.async_api import async_playwright
except ImportError:
    raise SystemExit("pip install playwright && playwright install chromium")

ROOT = Path(__file__).resolve().parents[3]
SHOT = Path(__file__).resolve().parent.parent / "screenshots"
PREVIEW = Path(__file__).resolve().parent.parent / "previews"
SHOT.mkdir(parents=True, exist_ok=True)

BASE = "http://127.0.0.1:5175/attendance"
LOGIN_USER = "admin"
LOGIN_PASS = "admin123"

PC_PAGES = [
    ("01-home-upload", "/home", True),
    ("02-task-list", "/tasks", True),
    ("03-task-records", "/task-records", True),
]

MINI_PREVIEWS = [
    ("08-mini-login", "mini-login.html"),
    ("09-mini-workbench", "mini-home.html"),
    ("10-mini-processing", "mini-processing.html"),
    ("12-mini-result", "mini-result.html"),
    ("13-mini-tasks", "mini-tasks.html"),
]


async def login(page):
    await page.goto(f"{BASE}/login", wait_until="networkidle", timeout=60000)
    await page.wait_for_timeout(800)
    if "/login" not in page.url:
        return
    await page.fill('input[placeholder*="用户"], input[placeholder*="username"], input[type="text"]', LOGIN_USER)
    await page.fill('input[type="password"]', LOGIN_PASS)
    await page.click('button:has-text("登录"), button:has-text("Login")')
    await page.wait_for_url("**/home**", timeout=30000)
    await page.wait_for_timeout(1200)


async def capture_pc(browser):
    context = await browser.new_context(
        viewport={"width": 1440, "height": 900},
        device_scale_factor=2,
    )
    page = await context.new_page()

    await page.goto(f"{BASE}/login", wait_until="networkidle", timeout=60000)
    await page.wait_for_timeout(1000)
    await page.screenshot(path=str(SHOT / "00-login.png"), full_page=False)
    print("PC  00-login.png")

    await login(page)

    for name, path, _ in PC_PAGES:
        if name == "00-login":
            continue
        await page.goto(f"{BASE}{path}", wait_until="networkidle", timeout=60000)
        await page.wait_for_timeout(1800)
        await page.screenshot(path=str(SHOT / f"{name}.png"), full_page=False)
        print(f"PC  {name}.png")

    # Task detail — open first processed/confirmed task
    await page.goto(f"{BASE}/tasks", wait_until="networkidle")
    await page.wait_for_timeout(1200)
    for selector in [
        'a[href*="/tasks/"]',
        'button:has-text("去核对")',
        'tr a',
        '.task-id-link',
    ]:
        loc = page.locator(selector).first
        if await loc.count() > 0:
            try:
                await loc.click(timeout=5000)
                await page.wait_for_timeout(2200)
                await page.screenshot(path=str(SHOT / "04-task-edit.png"), full_page=False)
                print("PC  04-task-edit.png")
                break
            except Exception:
                continue

    await context.close()


async def capture_mini(browser):
    for name, html in MINI_PREVIEWS:
        url = (PREVIEW / html).as_uri()
        context = await browser.new_context(
            viewport={"width": 390, "height": 844},
            device_scale_factor=3,
        )
        page = await context.new_page()
        await page.goto(url, wait_until="networkidle", timeout=30000)
        await page.wait_for_timeout(600)
        await page.screenshot(path=str(SHOT / f"{name}.png"), full_page=True)
        print(f"Mini {name}.png")
        await context.close()


async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, channel="chrome")
        await capture_pc(browser)
        await capture_mini(browser)
        await browser.close()
    print(f"\nScreenshots saved to {SHOT}")


if __name__ == "__main__":
    asyncio.run(main())
