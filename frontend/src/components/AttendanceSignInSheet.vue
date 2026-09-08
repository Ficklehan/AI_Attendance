<template>
  <div class="sign-in-sheet" :class="{ 'sign-in-sheet--latin': !cjk }">
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
        <span>{{ copy.supervisorPage }}</span>
        <span>{{ copy.supervisorDate }}</span>
      </span>
    </p>

    <table class="sign-in-sheet__table">
      <colgroup>
        <col class="col-no" style="width: 3%">
        <col class="col-name" style="width: 15%">
        <col class="col-agency" style="width: 12%">
        <col class="col-shift" style="width: 12%">
        <col class="col-time" style="width: 16%">
        <col class="col-time" style="width: 16%">
        <col class="col-break" style="width: 6%">
        <col class="col-sign" style="width: 10%">
        <col class="col-note" style="width: 10%">
      </colgroup>
      <thead>
        <tr>
          <th>{{ copy.columns[0] }}</th>
          <th>{{ copy.columns[1] }}</th>
          <th>{{ copy.columns[2] }}</th>
          <th>{{ copy.columns[3] }}</th>
          <th class="th-time">{{ copy.columns[4] }}</th>
          <th class="th-time">{{ copy.columns[5] }}</th>
          <th>{{ copy.columns[6] }}</th>
          <th>{{ copy.columns[7] }}</th>
          <th>{{ copy.columns[8] }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rowCount" :key="row">
          <td class="cell-no">{{ row }}</td>
          <td></td>
          <td></td>
          <td></td>
          <td class="cell-time">
            <div class="time-slots">
              <span class="time-digit"></span>
              <span class="time-digit"></span>
              <span class="time-sep">:</span>
              <span class="time-digit"></span>
              <span class="time-digit"></span>
            </div>
          </td>
          <td class="cell-time">
            <div class="time-slots">
              <span class="time-digit"></span>
              <span class="time-digit"></span>
              <span class="time-sep">:</span>
              <span class="time-digit"></span>
              <span class="time-digit"></span>
            </div>
          </td>
          <td></td>
          <td></td>
          <td></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getSheetCopy, isCjkSheet, SHEET_BLANK_ROWS } from '@/utils/attendanceSheetPrint'

const props = defineProps({
  sheetLang: { type: String, default: 'zh-CN' },
  countryLabel: { type: String, default: '' },
  warehouse: { type: String, default: '' },
  printedDate: { type: String, default: '' },
  rowCount: { type: Number, default: SHEET_BLANK_ROWS },
})

const copy = computed(() => getSheetCopy(props.sheetLang))
const cjk = computed(() => isCjkSheet(props.sheetLang))
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
  display: flex;
  flex-direction: column;
  min-height: 0;
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

.sign-in-sheet__table {
  width: 100%;
  flex: 1 1 auto;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 8.5pt;
  box-sizing: border-box;
  outline: 1px solid #111;
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
  background: #c5d8f5;
  font-size: 9.5pt;
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
.col-name { width: 15%; }
.col-agency { width: 12%; }
.col-shift { width: 12%; }
.col-time { width: 16%; }
.col-break { width: 6%; }
.col-sign { width: 10%; }
.col-note { width: 10%; }

.sign-in-sheet__table th:nth-child(1),
.sign-in-sheet__table td:nth-child(1) { width: 3%; }
.sign-in-sheet__table th:nth-child(2),
.sign-in-sheet__table td:nth-child(2) { width: 15%; }
.sign-in-sheet__table th:nth-child(3),
.sign-in-sheet__table td:nth-child(3) { width: 12%; }
.sign-in-sheet__table th:nth-child(4),
.sign-in-sheet__table td:nth-child(4) { width: 12%; }
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
  /* A4 landscape 210mm − 10mm page margin ≈ 200mm.
     Header block ≈ 42mm (含主管签字行 9mm), 表头 6.2mm,
     15 行 × 9.4mm ≈ 141mm, 合计 ≈ 189mm，留约 11mm 防溢出。 */
  .sign-in-sheet {
    display: block;
    width: 100%;
    max-width: none;
    height: auto;
    padding: 0;
    page-break-inside: auto;
    break-inside: auto;
    page-break-after: avoid;
    break-after: avoid;
  }

  .sign-in-sheet__table {
    flex: none;
    height: auto;
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

  .sign-in-sheet__supervisor {
    margin: 1.6mm 0 2mm;
    min-height: 9mm;
    padding: 2mm 0 2.2mm;
    font-size: 11pt;
    line-height: 1.45;
  }

  .sign-in-sheet__table {
    page-break-before: avoid;
    break-before: avoid;
    page-break-inside: auto;
    break-inside: auto;
  }

  .sign-in-sheet__table thead {
    display: table-row-group;
  }

  .sign-in-sheet__table th {
    height: 6.2mm;
  }

  .sign-in-sheet__table tbody td {
    height: 9.4mm;
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
  .th-time {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
}
</style>
