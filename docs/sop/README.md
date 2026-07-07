# Attendance Agent — System SOP

## Deliverables

| File | Description |
|------|-------------|
| `output/AttendanceAgent_SOP_Multilingual.docx` | Multilingual SOP (zh / en / fr / de) with screenshots |
| `output/仓库文员操作手册_AI考勤智能助手.docx` | **仓库文员专用**中文 SOP（登录·拍照识别·核对确认·考勤记录·飞书小程序） |
| `screenshots/` | UI capture assets |
| `.cursor/skills/attendance-pm-agent/` | Cursor PM agent skill |

## 仓库文员手册（推荐）

```bash
# 1. 启动后端 + 前端
./start.sh

# 2. 截取高清配图（使用本机 Chrome）
python3 docs/sop/scripts/capture_clerk_sop_screenshots.py

# 3. 生成 Word 手册
python3 docs/sop/scripts/generate_clerk_sop_docx.py
```

输出：`docs/sop/output/仓库文员操作手册_AI考勤智能助手.docx`

## Regenerate

```bash
# 1. Start backend + frontend
./start.sh

# 2. Capture screenshots (optional; needs playwright install chromium)
python docs/sop/scripts/capture_screenshots.py

# 3. Build Word document
python docs/sop/scripts/generate_sop_docx.py
```

## Languages in SOP

- 简体中文 (Part 1)
- English (Part 2)
- Français (Part 3)
- Deutsch (Part 4)

To add more locales, extend `LANG_SECTIONS` in `scripts/generate_sop_docx.py`.
