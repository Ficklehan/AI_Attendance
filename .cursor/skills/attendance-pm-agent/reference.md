# Attendance PM Agent — Reference

## API Quick Reference

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/attendance/api/auth/login` | POST | PC password login |
| `/attendance/api/auth/feishu/miniprogram` | POST | Mini-program Feishu login |
| `/attendance/api/tasks` | GET | Paginated task list |
| `/attendance/api/tasks/summary` | GET | Status counts (SSOT) |
| `/attendance/api/tasks/{id}` | GET | Task detail |
| `/attendance/api/tasks/{id}/confirm` | POST | Confirm & sync Feishu |
| `/attendance/api/tasks/{id}/retry-sync` | POST | Retry Bitable sync |
| `/attendance/api/local/upload-stream` | POST | SSE image upload + recognition |
| `/attendance/api/export/jobs` | POST/GET | Async Excel export |

## Environment Variables

```env
FEISHU_APP_ID=
FEISHU_APP_SECRET=
MIMO_API_KEY=
MIMO_API_URL=https://api.xiaomimimo.com/v1
MIMO_MODEL=mimo-v2.5
```

## SOP Maintenance

1. Update screenshots: `python docs/sop/scripts/capture_screenshots.py` (requires Playwright + running frontend on :5175)
2. Regenerate docx: `python docs/sop/scripts/generate_sop_docx.py`
3. Output: `docs/sop/output/AttendanceAgent_SOP_Multilingual.docx`

## Release Checklist (PM)

- [ ] Task state machine unchanged or docs updated
- [ ] New UI routes reflected in SOP screenshots
- [ ] i18n keys added for all supported locales
- [ ] Feishu field mapping validated per country
- [ ] Admin permission matrix still accurate
