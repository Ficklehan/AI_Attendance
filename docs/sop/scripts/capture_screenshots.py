#!/usr/bin/env python3
"""Capture SOP screenshots from running Attendance Agent frontend."""

import asyncio
import shutil
from pathlib import Path

try:
    from playwright.async_api import async_playwright
except ImportError:
    raise SystemExit("Install playwright: pip install playwright && playwright install chromium")

# Vite base=/clockai/ — 浏览器地址需带此前缀
BASE = "http://localhost:5175/clockai"
OUT = Path(__file__).resolve().parent.parent / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)

PAGES = [
    ("01-home-upload", "/home"),
    ("02-task-list", "/tasks"),
    ("03-task-records", "/task-records"),
    ("04-settings-ai", "/settings/ai"),
    ("05-settings-feishu", "/settings/feishu"),
    ("06-settings-users", "/settings/users"),
    ("07-settings-audit", "/settings/audit"),
]

MINI_ASSETS = [
    ("08-mini-login", "feishu-miniprogram/assets/attendance-login-hero-vertical.png"),
    ("09-mini-workbench", "feishu-miniprogram/assets/attendance-workbench-hero.png"),
    ("10-mini-processing", "feishu-miniprogram/assets/attendance-processing-bg.png"),
]


async def main():
    root = Path(__file__).resolve().parents[3]
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1440, "height": 900})
        page = await context.new_page()

        # Try login page screenshot (may redirect if session exists)
        await page.goto(f"{BASE}/login", wait_until="networkidle", timeout=30000)
        await page.wait_for_timeout(1500)
        await page.screenshot(path=str(OUT / "00-login.png"), full_page=True)

        for name, path in PAGES:
            await page.goto(f"{BASE}{path}", wait_until="networkidle", timeout=30000)
            await page.wait_for_timeout(2000)
            await page.screenshot(path=str(OUT / f"{name}.png"), full_page=True)
            print(f"Captured {name}.png -> {page.url}")

        # Task edit — open first pending task if available
        await page.goto(f"{BASE}/tasks", wait_until="networkidle")
        await page.wait_for_timeout(1500)
        link = page.locator('button:has-text("2026"), a:has-text("2026")').first
        if await link.count() > 0:
            await link.click()
            await page.wait_for_timeout(2000)
            await page.screenshot(path=str(OUT / "11-task-edit.png"), full_page=True)
            print(f"Captured 11-task-edit.png -> {page.url}")

        await browser.close()

    for name, rel in MINI_ASSETS:
        src = root / rel
        if src.exists():
            shutil.copy2(src, OUT / f"{name}.png")
            print(f"Copied mini asset {name}.png")

    print(f"\nDone. Screenshots in {OUT}")


if __name__ == "__main__":
    asyncio.run(main())
