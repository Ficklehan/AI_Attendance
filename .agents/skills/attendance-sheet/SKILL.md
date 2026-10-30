---
name: attendance-sheet
description: >-
  Extracts European warehouse attendance timesheets into structured OCR rows
  with text, confidence, bbox, and name_crossed. Uses DeepSeek Harness tools
  only: tiler for large scans, column strips + y-clustering, crop_zoom, then
  ocr_image always. Vision only on that cell if OCR is empty or low-confidence.
  Use when given a timesheet photo or asked to read Arrivée/Départ grids.
---

# Attendance sheet OCR (Harness)

Read [`references/prompt.md`](references/prompt.md) for transcription iron rules (本格、不抄班次/邻行、欧式数字、印刷冒号格).

Java only sends the image. You return **OCR evidence**. Do **not** output Mark / 已删除 / 正常 / 模糊 / 未出勤 — Java judges those.

## Tools (mandatory)

1. **Large scan** — if either side > 1024px, call `tiler_route` `auto` or `always` before reading. Do not let the model downsample the whole A4 page.
2. **Structure** — `vision_ground` to locate page-header warehouse + date, headers, every body row, columns: NO, NAME, AGENCY, SHIFT, ARRIVEE, DEPAR, PAUSE, SIGNATURE, OBSERVATIONS. Count body rows first; keep the first data row. Do not read clock digits here.
3. **Column strips** — crop each clock/name **column** (not the whole page). Then split by row Y. Long diagonal strokes then occupy only a sliver of one cell.
4. **`crop_zoom`** — each cell from the **original** image, `target` 800, `mode` nearest, `sheet` false. Arrivée/Départ: if a printed colon splits the cell, also crop hour and minute (or four digit boxes). Never invent boxes from page-width ratios.
5. **`ocr_image` always** — every crop, even if the chat model can see images. Keep `text` only when complete and legal. Incomplete OCR is not a time. Copy `confidence` and `bbox` (original-pixel `[x1,y1,x2,y2]`).
6. **Low conf reread** — if clock `confidence < 0.85` or text is not `HH:MM`, `crop_zoom` that cell again and `ocr_image`. Still bad → vision on **that crop only** (prompt.md 【0】【3】【4】). Never pass shift, neighbor, Pause, remarks.
7. **`name_crossed`** — true only when a strike runs through the name cell and there is **no** replacement name. Old name struck + new name in cell → `name_crossed` false, `name.text` = new name, `name_has_replacement` true.

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
