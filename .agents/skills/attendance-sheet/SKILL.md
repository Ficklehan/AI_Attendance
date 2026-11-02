---
name: attendance-sheet
description: >-
  Extracts European warehouse attendance timesheets into structured rows.
  Ground columns, crop_zoom NAME/ARRIVEE/DEPAR strips, DeepSeek Flash describes
  each strip alone. Never describe the original page. No local OCR, no OVH.
  Use when given a timesheet photo or asked to read Arrivée/Départ grids.
---

# Attendance sheet extract (Harness)

Read [`references/prompt.md`](references/prompt.md) for transcription iron rules (本格、不抄班次/邻行、欧式数字、印刷冒号格).

Java only sends the original image path. You return **OCR evidence**. Do **not** output Mark / 已删除 / 正常 / 模糊 / 未出勤 — Java judges those.

## Tools

Pixel isolation: Flash must never see SHIFT and clocks in the same image. Do not `vision_describe` the original page.

1. **`vision_ground`** (first output, annotate false) on the original path. Boxes only, no times. Each box is the FULL cell from its left grid line to its right grid line (not just the header text): HEADER (warehouse/date band above the body), NAME, ARRIVEE, DEPAR, SHIFT. ARRIVEE/DEPAR boxes must not include SHIFT/Horaires.

2. **`crop_zoom` once** on the original path: `sheet` false, `mode` nearest, `target` 6000. Separate PNGs labelled `header` / `name` / `arrivo` / `uscita` / `shift`. Never `sheet` true (stacking SHIFT with clocks leaks). Clock crops must not include SHIFT pixels.

3. **`vision_describe`** with `json` false on the NAME / HEADER / SHIFT crop PNGs only, one crop per call (`paths` = that one file).
   - NAME crop: full wrapped names; struck with no replacement → `name_crossed` true.
   - HEADER crop: warehouse and `date_raw`.
   - SHIFT crop (after clocks): `shift.text` only. Never write into arrivo/uscita.

4. **`ocr_image`** (`full` true; call `ocr_setup` first if needed) on the ARRIVEE and DEPAR crop PNGs (dsh-ocr local OCR).
   - ARRIVEE crop: column reads top-to-bottom = body rows top-to-bottom; each line is handwritten HH:MM, or empty if blank / only a printed colon. Do not invent times.
   - DEPAR crop: same rule.

Forbidden: `bash`; `read_image`; one cell one tool; `vision_describe` on the original path; putting SHIFT crop and a clock crop in the same describe.

## `name_crossed`

true only when a strike runs through the name cell and there is **no** replacement name. Old name struck + new name in cell → `name_crossed` false, `name.text` = new name, `name_has_replacement` true.

## Merge

`arrivo` only from the ARRIVEE crop describe. `uscita` only from the DEPAR crop describe. If that crop says empty, that clock stays empty even when shift is `10:00-19:00`.

## Output

Final answer = **one JSON object**, no markdown fence, no 16-field arrays, no Mark:

```json
{
  "page_num": "1/3",
  "warehouse": "MARLY",
  "date_raw": "09/07/2026",
  "rows": [
    {
      "row_no": "2",
      "no": { "text": "2", "confidence": 0.99, "bbox": [10, 40, 40, 70] },
      "name": { "text": "SISSOKO", "confidence": 0.94, "bbox": [50, 40, 200, 70] },
      "name_crossed": false,
      "name_has_replacement": false,
      "agency": { "text": "STAFFMATCH", "confidence": 0.9, "bbox": [210, 40, 300, 70] },
      "shift": { "text": "10:00-13:00", "confidence": 0.95, "bbox": [310, 40, 400, 70] },
      "arrivo": { "text": "10:00", "confidence": 0.96, "bbox": [410, 40, 480, 70], "empty": false },
      "uscita": { "text": "19:00", "confidence": 0.72, "bbox": [490, 40, 560, 70], "empty": false },
      "pausa": { "text": "0", "confidence": 0.99, "bbox": [570, 40, 600, 70] },
      "signature": { "text": "已签字", "confidence": 0.8, "bbox": [610, 40, 680, 70] },
      "observations": { "text": "", "confidence": 1, "bbox": [690, 40, 760, 70] }
    }
  ]
}
```

Clock cells: printed colon only / blank → `"text":""`, `"empty": true`, `confidence` 1. Ink but unreadable → `"text":""`, `"empty": false`, low confidence. `bbox` original pixels `[x1,y1,x2,y2]`. `date_raw` = header left-to-right. `warehouse` from header. Skip fully blank name + empty clocks.
