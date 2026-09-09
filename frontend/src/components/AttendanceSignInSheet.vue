<template>
  <div
    class="sign-in-sheet"
    :class="{
      'sign-in-sheet--latin': !cjk,
      'sign-in-sheet--page-break': pageBreakAfter,
      'sign-in-sheet--editable': editable,
    }"
  >
    <div class="sign-in-sheet__punch-mark" aria-hidden="true">
      <svg viewBox="0 0 80 80" focusable="false">
        <polygon points="0,0 80,0 80,80" fill="#d9e4f5" />
        <line x1="0" y1="0" x2="80" y2="80" stroke="#111" stroke-width="1.4" />
        <circle cx="24" cy="24" r="3.4" fill="#111" />
        <circle cx="52" cy="52" r="3.4" fill="#111" />
      </svg>
    </div>
    <div class="sign-in-sheet__punch">{{ copy.punchZone }}</div>
    <h1 class="sign-in-sheet__title">{{ copy.title }}</h1>
    <p class="sign-in-sheet__decl-title">{{ copy.declarationTitle }}</p>
    <p v-for="(line, index) in copy.declaration" :key="index" class="sign-in-sheet__decl">{{ line }}</p>

    <div class="sign-in-sheet__meta">
      <div class="sign-in-sheet__meta-item">
        <span class="sign-in-sheet__label">{{ copy.country }}</span>
        <span class="sign-in-sheet__value">{{ countryLabel }}</span>
      </div>
      <div class="sign-in-sheet__meta-item">
        <span class="sign-in-sheet__label">{{ copy.warehouse }}</span>
        <span class="sign-in-sheet__value">{{ warehouse }}</span>
      </div>
      <div class="sign-in-sheet__meta-item sign-in-sheet__meta-item--date">
        <span class="sign-in-sheet__label">{{ copy.date }}</span>
        <span class="sign-in-sheet__value">{{ printedDate }}</span>
      </div>
    </div>

    <p class="sign-in-sheet__supervisor">
      <span class="sign-in-sheet__supervisor-sign">{{ copy.supervisorSign }}</span>
      <span class="sign-in-sheet__supervisor-page">
        <span>{{ pageLabel }}</span>
        <span>{{ copy.supervisorDate }}</span>
      </span>
    </p>

    <div
      class="sign-in-sheet__table-wrap"
      @focusin="onEditFocusIn"
      @focusout="onEditFocusOut"
    >
      <div
        v-if="editable && editGuide"
        class="sign-in-sheet__edit-guide"
        :class="{ 'sign-in-sheet__edit-guide--dim': editFocused }"
        aria-hidden="true"
      >
        <span class="sign-in-sheet__edit-guide-text">{{ editGuide }}</span>
      </div>
      <table class="sign-in-sheet__table" @paste="onPaste">
      <colgroup>
        <col class="col-no" style="width: 3%">
        <col class="col-name" style="width: 17%">
        <col class="col-agency" style="width: 12%">
        <col class="col-shift" style="width: 10%">
        <col class="col-time" style="width: 16%">
        <col class="col-time" style="width: 16%">
        <col class="col-break" style="width: 6%">
        <col class="col-sign" style="width: 10%">
        <col class="col-note" style="width: 10%">
      </colgroup>
      <thead>
        <tr>
          <th
            :class="{ 'th-clickable th-editable': editable }"
            :title="editable ? autoNumberTitle : undefined"
            @click="onSeqHeaderClick"
          >
            {{ copy.columns[0] }}
          </th>
          <th :class="{ 'th-editable': editable }">{{ copy.columns[1] }}</th>
          <th :class="{ 'th-editable': editable }">{{ copy.columns[2] }}</th>
          <th :class="{ 'th-editable': editable }">{{ copy.columns[3] }}</th>
          <th class="th-time th-readonly">{{ copy.columns[4] }}</th>
          <th class="th-time th-readonly">{{ copy.columns[5] }}</th>
          <th class="th-readonly">{{ copy.columns[6] }}</th>
          <th class="th-readonly">{{ copy.columns[7] }}</th>
          <th class="th-readonly">{{ copy.columns[8] }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in displayRows" :key="`${page}-${index}`">
          <td class="cell-no cell-editable" @mousedown="onEditableCellMouseDown">
            <input
              v-if="editable"
              class="sheet-cell-input sheet-cell-input--seq sheet-cell-input--lg"
              data-sheet-field="seq"
              :data-sheet-row="index"
              :value="row.seq"
              @focus="onCellFocus(index, 'seq')"
              @input="updateCell(index, 'seq', $event.target.value)"
            >
            <span v-else class="cell-text cell-text--lg">{{ row.seq }}</span>
          </td>
          <td class="cell-editable cell-name" @mousedown="onEditableCellMouseDown">
            <input
              v-if="editable"
              class="sheet-cell-input sheet-cell-input--lg sheet-cell-input--name"
              data-sheet-field="name"
              :data-sheet-row="index"
              :value="row.name"
              @focus="onCellFocus(index, 'name')"
              @input="updateCell(index, 'name', $event.target.value)"
            >
            <span v-else class="cell-text cell-text--lg cell-text--name">{{ row.name }}</span>
          </td>
          <td class="cell-editable cell-agency" @mousedown="onEditableCellMouseDown">
            <input
              v-if="editable"
              class="sheet-cell-input sheet-cell-input--lg sheet-cell-input--agency"
              data-sheet-field="agency"
              :data-sheet-row="index"
              :value="row.agency"
              @focus="onCellFocus(index, 'agency')"
              @input="updateCell(index, 'agency', $event.target.value)"
            >
            <span v-else class="cell-text cell-text--lg cell-text--agency">{{ row.agency }}</span>
          </td>
          <td class="cell-editable" @mousedown="onEditableCellMouseDown">
            <input
              v-if="editable"
              class="sheet-cell-input sheet-cell-input--lg"
              data-sheet-field="shift"
              :data-sheet-row="index"
              :value="row.shift"
              @focus="onCellFocus(index, 'shift')"
              @input="updateCell(index, 'shift', $event.target.value)"
            >
            <span v-else class="cell-text cell-text--lg">{{ row.shift }}</span>
          </td>
          <td class="cell-time cell-readonly">
            <div class="time-slots">
              <span class="time-digit"></span>
              <span class="time-digit"></span>
              <span class="time-sep">:</span>
              <span class="time-digit"></span>
              <span class="time-digit"></span>
            </div>
          </td>
          <td class="cell-time cell-readonly">
            <div class="time-slots">
              <span class="time-digit"></span>
              <span class="time-digit"></span>
              <span class="time-sep">:</span>
              <span class="time-digit"></span>
              <span class="time-digit"></span>
            </div>
          </td>
          <td class="cell-readonly"></td>
          <td class="cell-readonly"></td>
          <td class="cell-readonly"></td>
        </tr>
      </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  createEmptySheetRow,
  ensureMinSheetRows,
  formatSheetPageLabel,
  getSheetCopy,
  isCjkSheet,
  sheetFieldIndex,
  SHEET_BLANK_ROWS,
} from '@/utils/attendanceSheetPrint'

const props = defineProps({
  sheetLang: { type: String, default: 'zh-CN' },
  countryLabel: { type: String, default: '' },
  warehouse: { type: String, default: '' },
  printedDate: { type: String, default: '' },
  rows: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
  page: { type: Number, default: 1 },
  totalPages: { type: Number, default: 1 },
  pageBreakAfter: { type: Boolean, default: false },
  rowCount: { type: Number, default: SHEET_BLANK_ROWS },
  autoNumberTitle: { type: String, default: '' },
  editGuide: { type: String, default: '' },
})

const emit = defineEmits(['update:rows', 'paste-tsv', 'auto-number'])

const focusedCell = ref({ row: 0, field: 'seq' })
const editFocused = ref(false)
let focusBlurTimer = null

const copy = computed(() => getSheetCopy(props.sheetLang))
const cjk = computed(() => isCjkSheet(props.sheetLang))

const displayRows = computed(() => {
  if (Array.isArray(props.rows) && props.rows.length) {
    return ensureMinSheetRows(props.rows, props.rowCount || SHEET_BLANK_ROWS)
  }
  return ensureMinSheetRows([], props.rowCount || SHEET_BLANK_ROWS)
})

const pageLabel = computed(() =>
  formatSheetPageLabel(copy.value.supervisorPage, props.page || 1, props.totalPages || 1),
)

function onCellFocus(rowIndex, field) {
  focusedCell.value = { row: rowIndex, field }
}

function onEditFocusIn() {
  if (focusBlurTimer) {
    clearTimeout(focusBlurTimer)
    focusBlurTimer = null
  }
  editFocused.value = true
}

function onEditFocusOut() {
  focusBlurTimer = setTimeout(() => {
    editFocused.value = false
    focusBlurTimer = null
  }, 0)
}

/** Rare fallback if click hits cell chrome outside the filled input. */
function onEditableCellMouseDown(event) {
  if (!props.editable) return
  const input = event.currentTarget?.querySelector?.('input.sheet-cell-input')
  if (!input || event.target === input) return
  input.focus({ preventScroll: true })
}

function resolvePasteOrigin(event) {
  const target = event?.target
  if (target?.dataset?.sheetField != null && target?.dataset?.sheetRow != null) {
    return {
      row: Number(target.dataset.sheetRow) || 0,
      col: sheetFieldIndex(target.dataset.sheetField),
    }
  }
  return {
    row: focusedCell.value.row || 0,
    col: sheetFieldIndex(focusedCell.value.field),
  }
}

function updateCell(index, field, value) {
  if (!props.editable) return
  const next = displayRows.value.map((row) => ({ ...row }))
  if (!next[index]) next[index] = createEmptySheetRow()
  next[index] = { ...next[index], [field]: value }
  emit('update:rows', next)
}

function onPaste(event) {
  if (!props.editable) return
  const text = event.clipboardData?.getData('text/plain') || ''
  if (!text.includes('\t') && !text.includes('\n')) return
  event.preventDefault()
  const origin = resolvePasteOrigin(event)
  emit('paste-tsv', { text, startRow: origin.row, startCol: origin.col })
}

function onSeqHeaderClick() {
  if (!props.editable) return
  emit('auto-number')
}
</script>

<style lang="scss" scoped>
.sign-in-sheet {
  position: relative;
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 8px 12px 10px;
  background: #fff;
  color: #111;
  font-family: 'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  box-sizing: border-box;
  min-height: 0;
}

/* Screen preview only: fill remaining viewport; print sheets stay natural height */
.sign-in-sheet--editable {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.sign-in-sheet__punch-mark {
  position: absolute;
  top: 0;
  right: 0;
  width: 22mm;
  height: 22mm;
  pointer-events: none;
  z-index: 2;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;

  svg {
    display: block;
    width: 100%;
    height: 100%;
  }
}

.sign-in-sheet__punch {
  position: relative;
  z-index: 1;
  font-size: 8pt;
  font-style: italic;
  color: #888;
  text-align: right;
  margin: 0 24mm 2mm 0;
}

.sign-in-sheet__title {
  margin: 0 0 1.5mm;
  text-align: center;
  font-size: 18pt;
  font-weight: 700;
  letter-spacing: 0.35em;
  line-height: 1.15;
}

.sign-in-sheet--latin .sign-in-sheet__title {
  letter-spacing: 0.08em;
  font-size: 16pt;
}

.sign-in-sheet__decl-title {
  margin: 0 0 1.5mm;
  font-size: 10pt;
  font-weight: 700;
  text-align: left;
  color: #c00000;
}

.sign-in-sheet__decl {
  margin: 0 0 0.8mm;
  font-size: 8.5pt;
  line-height: 1.35;
}

.sign-in-sheet__meta {
  display: grid;
  grid-template-columns: 1.1fr 1.4fr 1fr;
  gap: 4mm;
  margin: 2mm 0 1.5mm;
  font-size: 11pt;
  font-weight: 600;
}

.sign-in-sheet__meta-item {
  display: flex;
  align-items: flex-end;
  gap: 2mm;
  min-width: 0;
}

.sign-in-sheet__label {
  flex-shrink: 0;
  font-weight: 700;
}

.sign-in-sheet__value {
  flex: 1;
  min-width: 12mm;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-bottom: 1px solid #111;
  padding: 0 1mm 1mm;
}

.sign-in-sheet__supervisor {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8mm;
  margin: 2mm 0 2.5mm;
  min-height: 9mm;
  padding: 2mm 0 2.2mm;
  font-size: 11pt;
  font-weight: 700;
  line-height: 1.45;
}

.sign-in-sheet__supervisor-sign {
  min-width: 0;
}

.sign-in-sheet__supervisor-page {
  margin-left: auto;
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 10mm;
  text-align: right;
}

.sign-in-sheet__table-wrap {
  position: relative;
  width: 100%;
  min-height: 0;
}

.sign-in-sheet--editable .sign-in-sheet__table-wrap {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* 仅预览：虚线框 + 水印在上层但不接收鼠标，避免挡选中 */
.sign-in-sheet__edit-guide {
  position: absolute;
  z-index: 2;
  top: 0;
  left: 0;
  /* 序号3% + 姓名17% + 供应商12% + 班次10% */
  width: 42%;
  height: 100%;
  box-sizing: border-box;
  border: 2px dashed rgba(196, 140, 20, 0.7);
  border-radius: 2px;
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  transition: opacity 0.15s ease;
}

.sign-in-sheet__edit-guide--dim {
  opacity: 0.18;
}

.sign-in-sheet__edit-guide-text {
  max-width: 90%;
  padding: 6px 10px;
  color: rgba(140, 95, 10, 0.4);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
  text-align: center;
  letter-spacing: 0.04em;
  transform: rotate(-14deg);
  user-select: none;
  pointer-events: none;
  white-space: pre-line;
}

.sign-in-sheet__table {
  position: relative;
  z-index: 1;
  width: 100%;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 8.5pt;
  box-sizing: border-box;
  outline: 1px solid #111;
}

.sign-in-sheet--editable .sign-in-sheet__table {
  flex: 1 1 auto;
  height: 100%;
}

/* Equalize body rows when preview stretches the table */
.sign-in-sheet--editable .sign-in-sheet__table tbody tr {
  height: 1px;
}

.sign-in-sheet__table th,
.sign-in-sheet__table td {
  display: table-cell;
  box-sizing: border-box;
  border: 1px solid #111;
  border-right: 0;
  border-bottom: 0;
  padding: 1mm 1.2mm;
  text-align: center;
  vertical-align: middle;
  word-break: break-word;
}

.sign-in-sheet__table th:first-child,
.sign-in-sheet__table td:first-child {
  border-left: 1px solid #111;
}

.sign-in-sheet__table thead th {
  border-top: 1px solid #111;
}

.sign-in-sheet__table th:last-child,
.sign-in-sheet__table td:last-child {
  border-right: 1px solid #111;
}

.sign-in-sheet__table tbody tr:last-child td {
  border-bottom: 1px solid #111;
}

.sign-in-sheet__table th {
  font-weight: 700;
  background: #e8eef8;
  height: 28px;
}

.sign-in-sheet__table th.th-time {
  font-size: 9.5pt;
}

.sign-in-sheet__table th.th-clickable {
  cursor: pointer;
  user-select: none;
}

.sign-in-sheet__table th.th-clickable:hover {
  background: #d7e3f7;
}

/* Preview-only cue: editable columns share one accent color */
.sign-in-sheet--editable .th-editable {
  background: #ffe08a;
  font-size: 10.5pt;
  letter-spacing: 0.02em;
}

.sign-in-sheet--editable .th-clickable.th-editable:hover {
  background: #ffd45c;
}

.sign-in-sheet--editable .th-readonly {
  background: #e8eef8;
  opacity: 1;
}

.sign-in-sheet__table tbody td {
  height: auto;
}

.cell-no {
  font-variant-numeric: tabular-nums;
  width: 3%;
}

.cell-time {
  padding: 0 !important;
  width: 16%;
}

.sign-in-sheet--editable .cell-editable {
  position: relative;
  background: rgba(255, 224, 138, 0.28);
  padding: 0 !important;
  cursor: text;
  overflow: hidden;
  /* Do not use height:100% — stretched table dumps leftover height into the first row */
}

.sign-in-sheet--editable .cell-editable:focus-within {
  z-index: 3;
  background: rgba(255, 244, 200, 0.98);
  box-shadow: inset 0 0 0 2px #1677ff;
  outline: none;
}

.sheet-cell-input {
  display: block;
  width: 100%;
  min-width: 0;
  min-height: 28px;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  text-align: center;
  font: inherit;
  color: inherit;
  padding: 4px 6px;
  box-sizing: border-box;
  border-radius: 0;
}

/* Fill the cell in-flow so corners stay clickable without absolute stretch bugs */
.sign-in-sheet--editable .sheet-cell-input {
  min-height: 28px;
  padding: 2px 4px;
  line-height: 1.2;
}

.sheet-cell-input--seq {
  font-variant-numeric: tabular-nums;
}

.sheet-cell-input--lg,
.cell-text--lg {
  font-size: 11pt;
  font-weight: 600;
  line-height: 1.25;
}

.cell-text {
  display: block;
  width: 100%;
}

.time-slots {
  display: flex;
  align-items: stretch;
  width: 100%;
  height: 100%;
  min-height: 26px;
}

.time-digit,
.time-sep {
  box-sizing: border-box;
  border: 0;
  background-image: repeating-linear-gradient(
    to bottom,
    #c8c8c8 0,
    #c8c8c8 1.2px,
    transparent 1.2px,
    transparent 5.8px
  );
  background-repeat: no-repeat;
  background-position: right center;
  background-size: 1px 100%;
}

.time-digit {
  flex: 1 1 0;
  min-width: 0;
}

.time-digit:last-child {
  background-image: none;
}

.time-sep {
  flex: 0 0 3.2mm;
  width: 3.2mm;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14pt;
  font-weight: 700;
  line-height: 1;
  color: #111;
}

.col-no { width: 3%; }
.col-name { width: 17%; }
.col-agency { width: 12%; }
.col-shift { width: 10%; }
.col-time { width: 16%; }
.col-break { width: 6%; }
.col-sign { width: 10%; }
.col-note { width: 10%; }

.sign-in-sheet__table th:nth-child(1),
.sign-in-sheet__table td:nth-child(1) { width: 3%; }
.sign-in-sheet__table th:nth-child(2),
.sign-in-sheet__table td:nth-child(2) { width: 17%; }
.sign-in-sheet__table th:nth-child(3),
.sign-in-sheet__table td:nth-child(3) { width: 12%; }
.sign-in-sheet__table th:nth-child(4),
.sign-in-sheet__table td:nth-child(4) { width: 10%; }
.sign-in-sheet__table th:nth-child(5),
.sign-in-sheet__table td:nth-child(5),
.sign-in-sheet__table th:nth-child(6),
.sign-in-sheet__table td:nth-child(6) { width: 16%; }
.sign-in-sheet__table th:nth-child(7),
.sign-in-sheet__table td:nth-child(7) { width: 6%; }
.sign-in-sheet__table th:nth-child(8),
.sign-in-sheet__table td:nth-child(8) { width: 10%; }
.sign-in-sheet__table th:nth-child(9),
.sign-in-sheet__table td:nth-child(9) { width: 10%; }

@media print {
  /* Natural document flow — avoid flex/100% height (causes mid-page white gap). */
  .sign-in-sheet,
  .sign-in-sheet--editable {
    display: block !important;
    width: 100% !important;
    max-width: none !important;
    height: auto !important;
    min-height: 0 !important;
    max-height: none !important;
    padding: 0 !important;
    page-break-inside: auto;
    break-inside: auto;
  }

  .sign-in-sheet--page-break {
    page-break-after: always;
    break-after: page;
  }

  .sign-in-sheet__edit-guide {
    display: none !important;
  }

  .sign-in-sheet__table-wrap {
    display: block !important;
    flex: none !important;
    height: auto !important;
    min-height: 0 !important;
    max-height: none !important;
  }

  .sign-in-sheet__table {
    display: table !important;
    width: 100% !important;
    flex: none !important;
    height: auto !important;
    page-break-before: avoid;
    break-before: avoid;
    page-break-inside: auto;
    break-inside: auto;
  }

  .sign-in-sheet__table tbody tr {
    height: auto !important;
  }

  .sign-in-sheet__supervisor {
    margin: 1.6mm 0 2mm !important;
    min-height: 9mm;
    padding: 2mm 0 2.2mm;
    font-size: 11pt;
    line-height: 1.45;
  }

  .sheet-cell-input {
    display: none !important;
  }

  .sign-in-sheet__punch-mark {
    width: 20mm;
    height: 20mm;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .sign-in-sheet__punch {
    margin: 0 22mm 0.4mm 0;
    font-size: 7.5pt;
    line-height: 1.2;
  }

  .sign-in-sheet__title {
    margin: 0 0 0.6mm;
    font-size: 15pt;
  }

  .sign-in-sheet--latin .sign-in-sheet__title {
    font-size: 13.5pt;
  }

  .sign-in-sheet__decl-title {
    margin: 0 0 0.5mm;
    font-size: 9.5pt;
  }

  .sign-in-sheet__decl {
    margin: 0 0 0.3mm;
    font-size: 8pt;
    line-height: 1.22;
  }

  .sign-in-sheet__meta {
    margin: 1mm 0 0.8mm;
    font-size: 10.5pt;
    gap: 3mm;
  }

  .sign-in-sheet__value {
    padding: 0 1mm 0.5mm;
  }

  .sign-in-sheet__table thead {
    display: table-row-group;
  }

  .sign-in-sheet__table th {
    height: 6.2mm;
    background: #e8eef8 !important;
    font-size: 8.5pt !important;
  }

  .sign-in-sheet__table th.th-time {
    background: #e8eef8 !important;
    font-size: 9.5pt !important;
  }

  .sign-in-sheet__table tbody td {
    height: auto !important;
    max-height: none !important;
    min-height: 9.4mm !important;
    background: transparent !important;
    overflow: visible !important;
  }

  .cell-name,
  .cell-agency {
    overflow: visible !important;
    vertical-align: middle;
  }

  .cell-text--name,
  .cell-text--agency {
    display: block;
    white-space: normal !important;
    overflow: visible !important;
    text-overflow: clip !important;
    word-break: break-word;
    overflow-wrap: anywhere;
    line-height: 1.15;
    font-size: 9.5pt;
    font-weight: 600;
  }

  .time-slots {
    min-height: 9.4mm;
  }

  .time-digit,
  .time-sep {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .time-sep {
    color: #111;
    font-weight: 700;
  }

  .sign-in-sheet__decl-title,
  .th-time,
  .sign-in-sheet__table th {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .cell-text--lg {
    font-size: 10pt;
    font-weight: 600;
  }

  /* Preview accent colors must not appear on paper */
  .th-editable,
  .cell-editable {
    background: transparent !important;
  }
}
</style>
