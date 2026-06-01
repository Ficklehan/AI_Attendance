# Attendance Agent — System SOP

## Deliverables

| File | Description |
|------|-------------|
| `output/AttendanceAgent_SOP_Multilingual.docx` | Multilingual SOP (zh / en / fr / de) with screenshots |
| `screenshots/` | UI capture assets |
| `.cursor/skills/attendance-pm-agent/` | Cursor PM agent skill |

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
