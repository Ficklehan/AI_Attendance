<template>
  <div class="print-sheet-page">
    <PageShell :title="$t('printSheet.title')" :subtitle="$t('printSheet.subtitle')" inline-subtitle>
      <template #extra>
        <a-space wrap>
          <a-button @click="$router.push('/print-sheet/history')">
            {{ $t('printSheet.history') }}
          </a-button>
          <a-button @click="handlePasteClipboard">
            {{ $t('printSheet.pasteClipboard') }}
          </a-button>
          <a-button @click="handleAutoNumber">
            {{ $t('printSheet.autoNumber') }}
          </a-button>
          <a-button @click="handleSaveDraft">
            {{ $t('printSheet.saveDraft') }}
          </a-button>
          <a-button @click="handleClearDraft">
            {{ $t('printSheet.clearDraft') }}
          </a-button>
          <a-button @click="handleDownloadExcel">
            {{ $t('printSheet.downloadExcel') }}
          </a-button>
          <a-button type="primary" :loading="printing" @click="handlePrint">
            {{ $t('printSheet.print') }}
          </a-button>
        </a-space>
      </template>
    </PageShell>

    <a-card class="surface-card print-sheet-page__form" :bordered="false">
      <a-form layout="horizontal" class="print-sheet-form">
        <div class="print-sheet-form__grid">
          <a-form-item :label="$t('printSheet.country')" required>
            <a-select
              v-model:value="countryCode"
              :options="countryOptions"
              show-search
              option-filter-prop="label"
              :placeholder="$t('printSheet.countryPlaceholder')"
            />
          </a-form-item>
          <a-form-item :label="$t('printSheet.warehouse')" required>
            <div class="print-sheet-form__warehouse">
              <a-input
                v-model:value="warehouse"
                :placeholder="$t('printSheet.warehousePlaceholder')"
                allow-clear
                autocomplete="off"
                @focus="openWarehouseSuggest"
                @blur="closeWarehouseSuggest"
              />
              <ul
                v-show="warehouseSuggestOpen && filteredWarehouseOptions.length"
                class="print-sheet-form__warehouse-list"
                role="listbox"
              >
                <li v-for="item in filteredWarehouseOptions" :key="item" role="option">
                  <button type="button" @mousedown.prevent="pickWarehouse(item)">
                    {{ item }}
                  </button>
                </li>
              </ul>
            </div>
          </a-form-item>
          <a-form-item :label="$t('printSheet.date')" required>
            <a-date-picker
              v-model:value="workDate"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              class="print-sheet-form__date"
              :allow-clear="false"
              :placeholder="$t('printSheet.datePlaceholder')"
              @change="onDateChange"
            />
          </a-form-item>
          <a-form-item :label="$t('printSheet.sheetLanguage')" required>
            <a-select
              v-model:value="sheetLocale"
              :options="sheetLanguageOptions"
              :placeholder="$t('printSheet.sheetLanguagePlaceholder')"
              @change="onSheetLocaleChange"
            />
          </a-form-item>
        </div>
        <div class="print-sheet-form__toolbar">
          <span class="print-sheet-form__hint">{{ $t('printSheet.prefillHint') }}</span>
          <a-space>
            <a-button size="small" :disabled="currentPage <= 1" @click="currentPage -= 1">
              {{ $t('printSheet.prevPage') }}
            </a-button>
            <span class="print-sheet-form__page">
              {{ $t('printSheet.pageStatus', { page: currentPage, total: totalPages }) }}
            </span>
            <a-button size="small" :disabled="currentPage >= totalPages" @click="currentPage += 1">
              {{ $t('printSheet.nextPage') }}
            </a-button>
          </a-space>
        </div>
        <p v-if="countryMismatch" class="print-sheet-form__warn">
          {{ $t('printSheet.countryMismatchHint') }}
        </p>
      </a-form>
    </a-card>

    <div class="print-sheet-page__preview-wrap print-sheet-page__preview-wrap--screen">
      <AttendanceSignInSheet
        :sheet-lang="sheetLocale"
        :country-label="printedCountryLabel"
        :warehouse="warehouse.trim()"
        :printed-date="previewPrintedDate"
        :rows="currentPageRows"
        :page="currentPage"
        :total-pages="totalPages"
        editable
        :auto-number-title="$t('printSheet.autoNumberHint')"
        :edit-guide="$t('printSheet.editZoneHint')"
        @update:rows="onCurrentPageRowsUpdate"
        @paste-tsv="applyPaste"
        @auto-number="handleAutoNumber"
      />
    </div>

    <div class="print-sheet-page__print-only" aria-hidden="true">
      <AttendanceSignInSheet
        v-for="(pageRows, index) in printPages"
        :key="`print-${index}`"
        :sheet-lang="sheetLocale"
        :country-label="printedCountryLabel"
        :warehouse="warehouse.trim()"
        :printed-date="previewPrintedDate"
        :rows="pageRows"
        :page="index + 1"
        :total-pages="totalPages"
        :page-break-after="index < printPages.length - 1"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import PageShell from '@/components/PageShell.vue'
import AttendanceSignInSheet from '@/components/AttendanceSignInSheet.vue'
import { useCountryStore } from '@/stores/country'
import { useAuthStore } from '@/stores/auth'
import { buildCountrySelectOption, translateCountryName } from '@/utils/countryLabels'
import { buildLanguageSelectOptions } from '@/constants/languageOptions'
import { getMyDataScope } from '@/api/dataScope'
import { createPrintSheetJob } from '@/api/printSheet'
import {
  autoNumberRows,
  chunkRows,
  clearSheetDraft,
  clearReprintPayload,
  downloadSheetExcel,
  ensureMinSheetRows,
  formatSheetDateMdY,
  loadLastPrintPrefs,
  loadLastWarehouse,
  loadRecentWarehouses,
  loadReprintPayload,
  loadSheetDraft,
  mergeWarehouseSuggestions,
  normalizeSheetRows,
  parseClipboardGrid,
  applyClipboardGridAt,
  rememberLastPrintPrefs,
  resolvePrintIsoDate,
  resolveSheetLocale,
  resolveSheetPageCount,
  saveSheetDraft,
  SHEET_BLANK_ROWS,
  todayIsoDate,
  trimTrailingSheetRows,
} from '@/utils/attendanceSheetPrint'

const PRINT_BODY_CLASS = 'printing-sign-in-sheet'

const { t, locale } = useI18n()
const countryStore = useCountryStore()
const authStore = useAuthStore()

const countryCode = ref(undefined)
const warehouse = ref('')
const workDate = ref(todayIsoDate())
const dateTouched = ref(false)
const scopedWarehouses = ref([])
const recentWarehouses = ref([])
const warehouseSuggestOpen = ref(false)
const sheetLocale = ref(resolveSheetLocale(locale.value))
/** Once true, UI locale changes no longer overwrite paper language */
const sheetLocalePinned = ref(false)
const sheetLanguageOptions = buildLanguageSelectOptions()
const sheetRows = ref(ensureMinSheetRows([], SHEET_BLANK_ROWS))
const currentPage = ref(1)
const printing = ref(false)
const undoStack = ref([])
const redoStack = ref([])
const MAX_UNDO = 40

function cloneSheetRows(rows) {
  return (Array.isArray(rows) ? rows : []).map((row) => ({
    seq: row?.seq === 0 || row?.seq ? String(row.seq) : '',
    name: String(row?.name || ''),
    agency: String(row?.agency || ''),
    shift: String(row?.shift || ''),
  }))
}

function pushUndoSnapshot() {
  undoStack.value.push(cloneSheetRows(sheetRows.value))
  if (undoStack.value.length > MAX_UNDO) {
    undoStack.value.splice(0, undoStack.value.length - MAX_UNDO)
  }
  redoStack.value = []
}

function undoSheetRows() {
  if (!undoStack.value.length) {
    message.info(t('printSheet.undoEmpty'))
    return false
  }
  redoStack.value.push(cloneSheetRows(sheetRows.value))
  sheetRows.value = ensureMinSheetRows(undoStack.value.pop(), SHEET_BLANK_ROWS)
  return true
}

function redoSheetRows() {
  if (!redoStack.value.length) {
    message.info(t('printSheet.redoEmpty'))
    return false
  }
  undoStack.value.push(cloneSheetRows(sheetRows.value))
  sheetRows.value = ensureMinSheetRows(redoStack.value.pop(), SHEET_BLANK_ROWS)
  return true
}

function onSheetHistoryKeydown(event) {
  const key = String(event.key || '').toLowerCase()
  const mod = event.metaKey || event.ctrlKey
  if (!mod) return

  const isUndo = key === 'z' && !event.shiftKey
  const isRedo = (key === 'z' && event.shiftKey) || key === 'y'
  if (!isUndo && !isRedo) return

  // Only intercept when we have history for sheet bulk edits (paste etc.)
  if (isUndo && !undoStack.value.length) return
  if (isRedo && !redoStack.value.length) return

  event.preventDefault()
  if (isUndo) undoSheetRows()
  else redoSheetRows()
}

const countryOptions = computed(() => {
  void locale.value
  return (countryStore.options || [])
    .filter((item) => item.code && item.code !== 'default')
    .map((item) => buildCountrySelectOption(item))
})

const warehouseSuggestions = computed(() =>
  mergeWarehouseSuggestions(recentWarehouses.value, scopedWarehouses.value),
)

const filteredWarehouseOptions = computed(() => {
  const q = warehouse.value.trim().toLowerCase()
  const all = warehouseSuggestions.value
  if (!q) return all.slice(0, 20)
  return all.filter((item) => item.toLowerCase().includes(q)).slice(0, 20)
})

const printedCountryLabel = computed(() => {
  void locale.value
  if (!countryCode.value) return ''
  const found = (countryStore.options || []).find((item) => item.code === countryCode.value)
  return translateCountryName(countryCode.value, found?.name || countryCode.value, sheetLocale.value)
})

const previewIsoDate = computed(() => resolvePrintIsoDate(workDate.value, dateTouched.value))
const previewPrintedDate = computed(() => formatSheetDateMdY(previewIsoDate.value))

const countryMismatch = computed(() => {
  const selected = countryCode.value
  const working = countryStore.workingCountry
  if (!selected || !working || working === 'default') return false
  return selected !== working
})

const filledRows = computed(() => normalizeSheetRows(sheetRows.value))

const totalPages = computed(() => resolveSheetPageCount(sheetRows.value, SHEET_BLANK_ROWS))

const printPages = computed(() => chunkRows(sheetRows.value, SHEET_BLANK_ROWS))

const currentPageRows = computed(() => {
  const pageIndex = Math.min(Math.max(currentPage.value, 1), totalPages.value) - 1
  return printPages.value[pageIndex] || ensureMinSheetRows([], SHEET_BLANK_ROWS)
})

watch(totalPages, (total) => {
  if (currentPage.value > total) currentPage.value = total
})

function applyDefaultCountry() {
  const working = countryStore.workingCountry
  if (working && working !== 'default') {
    countryCode.value = working
    return
  }
  const first = countryOptions.value[0]?.value
  countryCode.value = first || undefined
}

function isCountryOptionAvailable(code) {
  const value = String(code || '').trim()
  if (!value || value === 'default') return false
  return countryOptions.value.some((item) => item.value === value)
}

/** Restore country / warehouse / paper language from last print for this user. */
function applyLastPrintPrefs() {
  const prefs = loadLastPrintPrefs(authStore.userInfo?.id)
  const legacyWarehouse = loadLastWarehouse()

  if (prefs?.countryCode && isCountryOptionAvailable(prefs.countryCode)) {
    countryCode.value = prefs.countryCode
  } else {
    applyDefaultCountry()
  }

  const nextWarehouse = prefs?.warehouse || legacyWarehouse
  if (nextWarehouse) warehouse.value = nextWarehouse

  if (prefs?.sheetLocale) {
    sheetLocale.value = resolveSheetLocale(prefs.sheetLocale)
    sheetLocalePinned.value = true
  }
}

function onSheetLocaleChange() {
  sheetLocalePinned.value = true
}

function onDateChange() {
  dateTouched.value = true
}

function openWarehouseSuggest() {
  recentWarehouses.value = loadRecentWarehouses()
  warehouseSuggestOpen.value = true
}

function closeWarehouseSuggest() {
  warehouseSuggestOpen.value = false
}

function pickWarehouse(item) {
  warehouse.value = item
  warehouseSuggestOpen.value = false
}

function rebuildRowsFromPages(pages) {
  const flat = []
  for (const page of pages) {
    for (const row of page) flat.push({ ...row })
  }
  sheetRows.value = trimTrailingSheetRows(flat, SHEET_BLANK_ROWS)
}

function onCurrentPageRowsUpdate(pageRows) {
  const pages = printPages.value.map((page) => page.map((row) => ({ ...row })))
  const pageIndex = Math.min(Math.max(currentPage.value, 1), pages.length) - 1
  pages[pageIndex] = ensureMinSheetRows(pageRows, SHEET_BLANK_ROWS)
  rebuildRowsFromPages(pages)
}

function applyPaste(payload, fallbackStart = {}) {
  const text = typeof payload === 'string' ? payload : payload?.text
  const startRowInPage = typeof payload === 'object' && payload
    ? Number(payload.startRow) || 0
    : Number(fallbackStart.startRow) || 0
  const startCol = typeof payload === 'object' && payload
    ? Number(payload.startCol) || 0
    : Number(fallbackStart.startCol) || 0

  const grid = parseClipboardGrid(text)
  if (!grid.length) {
    message.warning(t('printSheet.pasteEmpty'))
    return
  }

  const pageOffset = (Math.min(Math.max(currentPage.value, 1), totalPages.value) - 1) * SHEET_BLANK_ROWS
  const absoluteRow = pageOffset + Math.max(0, startRowInPage)
  const { rows: nextRows, pastedCount } = applyClipboardGridAt(
    sheetRows.value,
    grid,
    absoluteRow,
    startCol,
  )
  if (!pastedCount) {
    message.warning(t('printSheet.pasteEmpty'))
    return
  }
  pushUndoSnapshot()
  sheetRows.value = trimTrailingSheetRows(nextRows, SHEET_BLANK_ROWS)
  currentPage.value = Math.floor(absoluteRow / SHEET_BLANK_ROWS) + 1
  message.success(t('printSheet.pasteDone', { count: pastedCount }))
}

async function handlePasteClipboard() {
  try {
    if (!navigator.clipboard?.readText) {
      message.warning(t('printSheet.pasteUnsupported'))
      return
    }
    const text = await navigator.clipboard.readText()
    // Toolbar paste: start at current page first row / 序号 when no focused cell
    applyPaste({ text, startRow: 0, startCol: 0 })
  } catch {
    message.warning(t('printSheet.pasteUnsupported'))
  }
}

function handleAutoNumber() {
  pushUndoSnapshot()
  const numbered = autoNumberRows(ensureMinSheetRows(sheetRows.value, SHEET_BLANK_ROWS))
  sheetRows.value = numbered
  message.success(t('printSheet.autoNumberDone'))
}

function handleSaveDraft() {
  const saved = saveSheetDraft({
    countryCode: countryCode.value,
    warehouse: warehouse.value,
    workDate: workDate.value,
    sheetLocale: sheetLocale.value,
    dateTouched: dateTouched.value,
    rows: sheetRows.value,
  })
  if (!saved) {
    message.error(t('printSheet.draftSaveFailed'))
    return
  }
  message.success(t('printSheet.draftSaved'))
}

function handleClearDraft() {
  pushUndoSnapshot()
  clearSheetDraft()
  sheetRows.value = ensureMinSheetRows([], SHEET_BLANK_ROWS)
  currentPage.value = 1
  message.success(t('printSheet.draftCleared'))
}

function prepareSheetPayload() {
  const nextCountry = countryCode.value
  const nextWarehouse = warehouse.value.trim()
  if (!nextCountry || nextCountry === 'default') {
    message.warning(t('printSheet.countryRequired'))
    return null
  }
  if (!nextWarehouse) {
    message.warning(t('printSheet.warehouseRequired'))
    return null
  }
  if (!dateTouched.value) {
    workDate.value = todayIsoDate()
  }
  if (!workDate.value) {
    message.warning(t('printSheet.dateRequired'))
    return null
  }
  rememberLastPrintPrefs(authStore.userInfo?.id, {
    countryCode: nextCountry,
    warehouse: nextWarehouse,
    sheetLocale: sheetLocale.value,
  })
  return {
    countryCode: nextCountry,
    sheetLocale: sheetLocale.value,
    countryLabel: printedCountryLabel.value,
    warehouse: nextWarehouse,
    workDate: workDate.value,
    printedDate: formatSheetDateMdY(workDate.value),
    pageCount: totalPages.value,
    rows: filledRows.value,
  }
}

async function handlePrint() {
  const payload = prepareSheetPayload()
  if (!payload) return

  printing.value = true
  try {
    if (payload.rows.length) {
      await createPrintSheetJob({
        countryCode: payload.countryCode,
        workDate: payload.workDate,
        warehouse: payload.warehouse,
        sheetLocale: payload.sheetLocale,
        pageCount: payload.pageCount,
        rows: payload.rows.map((row) => ({
          seqNo: row.seq === '' || row.seq == null ? null : Number(row.seq) || null,
          personName: row.name,
          agencyName: row.agency,
          shiftName: row.shift,
        })),
      })
    }
    window.print()
  } catch (error) {
    console.error('归档打印人员失败:', error)
    message.error(t('printSheet.archiveFailed'))
  } finally {
    printing.value = false
  }
}

function handleDownloadExcel() {
  const payload = prepareSheetPayload()
  if (!payload) return
  downloadSheetExcel()
  message.success(t('printSheet.downloadExcelDone'))
}

function restoreDraft() {
  const draft = loadSheetDraft()
  if (!draft) return
  if (draft.countryCode) countryCode.value = draft.countryCode
  if (draft.warehouse) warehouse.value = draft.warehouse
  if (draft.workDate) {
    workDate.value = draft.workDate
    dateTouched.value = Boolean(draft.dateTouched)
  }
  if (draft.sheetLocale) {
    sheetLocale.value = resolveSheetLocale(draft.sheetLocale)
    sheetLocalePinned.value = true
  }
  if (Array.isArray(draft.rows) && draft.rows.length) {
    sheetRows.value = ensureMinSheetRows(draft.rows, SHEET_BLANK_ROWS)
  }
}

function restoreReprint() {
  const payload = loadReprintPayload()
  if (!payload) return false
  clearReprintPayload()
  if (payload.countryCode) countryCode.value = payload.countryCode
  if (payload.warehouse) warehouse.value = payload.warehouse
  if (payload.workDate) {
    workDate.value = payload.workDate
    dateTouched.value = true
  }
  if (payload.sheetLocale) {
    sheetLocale.value = resolveSheetLocale(payload.sheetLocale)
    sheetLocalePinned.value = true
  }
  if (Array.isArray(payload.rows) && payload.rows.length) {
    sheetRows.value = ensureMinSheetRows(payload.rows, SHEET_BLANK_ROWS)
  }
  currentPage.value = 1
  message.success(t('printSheet.reprintLoaded'))
  return true
}

onMounted(async () => {
  document.body.classList.add(PRINT_BODY_CLASS)
  window.addEventListener('keydown', onSheetHistoryKeydown)
  try {
    await countryStore.hydrate()
  } catch (error) {
    console.error('加载工作地区失败:', error)
  }
  applyLastPrintPrefs()
  recentWarehouses.value = loadRecentWarehouses()
  if (!restoreReprint()) {
    restoreDraft()
  }
  try {
    const res = await getMyDataScope({ silentError: true })
    scopedWarehouses.value = Array.isArray(res?.data?.warehouses) ? res.data.warehouses : []
  } catch {
    scopedWarehouses.value = []
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onSheetHistoryKeydown)
  document.body.classList.remove(PRINT_BODY_CLASS)
})

watch(
  () => countryStore.workingCountry,
  () => {
    if (!countryCode.value || countryCode.value === 'default') applyDefaultCountry()
  },
)

watch(locale, (next, prev) => {
  if (sheetLocalePinned.value) return
  const prevResolved = resolveSheetLocale(prev)
  if (sheetLocale.value === prevResolved || !prev) {
    sheetLocale.value = resolveSheetLocale(next)
  }
})
</script>

<style lang="scss" scoped>
.print-sheet-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - #{$header-height} - 16px);
  padding: 0;
}

.print-sheet-page :deep(.page-shell) {
  margin-bottom: 8px;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.print-sheet-page :deep(.page-shell__title) {
  font-size: 18px;
  line-height: 1.2;
}

.print-sheet-page :deep(.page-shell__inline-subtitle) {
  font-size: 12px;
  line-height: 1.2;
}

.print-sheet-page__form {
  margin-bottom: 8px;
  flex-shrink: 0;

  :deep(.ant-card-body) {
    padding: 12px 16px !important;
  }
}

.print-sheet-form {
  :deep(.ant-form-item) {
    margin-bottom: 0;
  }

  :deep(.ant-form-item-row) {
    display: flex;
    flex-flow: row nowrap;
    align-items: center;
    column-gap: 8px;
    row-gap: 0;
  }

  :deep(.ant-form-item-label) {
    flex: 0 0 auto;
    max-width: none;
    padding: 0 !important;

    > label {
      height: 32px;
      line-height: 32px;
      font-size: 13px;
      white-space: nowrap;
    }
  }

  :deep(.ant-form-item-control) {
    flex: 1 1 auto;
    min-width: 0;
  }

  :deep(.ant-form-item-control-input) {
    min-height: 32px;
  }

  :deep(.ant-select),
  :deep(.ant-select-selector),
  :deep(.ant-input),
  :deep(.ant-input-affix-wrapper),
  :deep(.ant-picker) {
    width: 100%;
    min-width: 0;
    border-radius: $radius-sm !important;
  }

  :deep(.ant-select-selector),
  :deep(.ant-input-affix-wrapper),
  :deep(.ant-picker) {
    height: 32px !important;
  }
}

.print-sheet-form__grid {
  display: grid;
  grid-template-columns: minmax(200px, 1.1fr) minmax(200px, 1.2fr) minmax(180px, 1fr) minmax(220px, 1.15fr);
  gap: 0 16px;
}

.print-sheet-form__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.print-sheet-form__hint {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.print-sheet-form__page {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.print-sheet-form__date {
  width: 100%;
}

.print-sheet-form__warehouse {
  position: relative;
  width: 100%;
}

.print-sheet-form__warehouse-list {
  position: absolute;
  z-index: 20;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  margin: 0;
  padding: 4px 0;
  max-height: 220px;
  overflow: auto;
  list-style: none;
  background: #fff;
  border: 1px solid $border;
  border-radius: $radius-sm;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

.print-sheet-form__warehouse-list button {
  display: block;
  width: 100%;
  padding: 5px 12px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 13px;
  line-height: 22px;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: #f5f5f5;
  }
}

.print-sheet-form__warn {
  margin: 4px 0 0;
  font-size: 12px;
  color: $warning-dark;
}

.print-sheet-page__preview-wrap {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid $border;
  border-radius: 10px;
  overflow: hidden;
  padding: 8px 12px 10px;
}

.print-sheet-page__preview-wrap :deep(.sign-in-sheet) {
  flex: 1 1 auto;
  width: 100%;
  max-width: none;
  height: 100%;
}

.print-sheet-page__print-only {
  display: none;
}

.print-sheet-page__print-only :deep(.sign-in-sheet) {
  height: auto !important;
  min-height: 0 !important;
  flex: none !important;
}

@media (max-width: 1100px) {
  .print-sheet-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .print-sheet-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style lang="scss">
@media screen {
  body.printing-sign-in-sheet .main-content {
    padding-top: 8px;
    padding-bottom: 8px;
    display: flex;
    flex-direction: column;
    min-height: calc(100vh - 56px);
  }

  body.printing-sign-in-sheet .main-content > * {
    flex: 1 1 auto;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
}

@media print {
  @page {
    size: A4 landscape;
    margin: 5mm;
  }

  html,
  body,
  body.printing-sign-in-sheet #app {
    height: auto !important;
    min-height: 0 !important;
  }

  body.printing-sign-in-sheet {
    background: #fff !important;
    overflow: visible !important;
  }

  body.printing-sign-in-sheet .header,
  body.printing-sign-in-sheet .ant-layout-header,
  body.printing-sign-in-sheet .page-shell,
  body.printing-sign-in-sheet .print-sheet-page__form,
  body.printing-sign-in-sheet .print-sheet-page__preview-wrap--screen,
  body.printing-sign-in-sheet .ant-popover,
  body.printing-sign-in-sheet .ant-message,
  body.printing-sign-in-sheet .ant-notification {
    display: none !important;
  }

  body.printing-sign-in-sheet .print-sheet-page__print-only {
    display: block !important;
  }

  body.printing-sign-in-sheet .print-sheet-page__print-only .sign-in-sheet,
  body.printing-sign-in-sheet .print-sheet-page__print-only .sign-in-sheet__table-wrap,
  body.printing-sign-in-sheet .print-sheet-page__print-only .sign-in-sheet__table {
    display: revert !important;
    height: auto !important;
    min-height: 0 !important;
    max-height: none !important;
    flex: none !important;
  }

  body.printing-sign-in-sheet .print-sheet-page__print-only .sign-in-sheet {
    display: block !important;
  }

  body.printing-sign-in-sheet .print-sheet-page__print-only .sign-in-sheet__table-wrap {
    display: block !important;
  }

  body.printing-sign-in-sheet .print-sheet-page__print-only .sign-in-sheet__table {
    display: table !important;
  }

  body.printing-sign-in-sheet .layout-container,
  body.printing-sign-in-sheet .main-content,
  body.printing-sign-in-sheet .ant-layout,
  body.printing-sign-in-sheet .ant-layout-content,
  body.printing-sign-in-sheet .print-sheet-page,
  body.printing-sign-in-sheet .print-sheet-page__print-only {
    display: block !important;
    padding: 0 !important;
    margin: 0 !important;
    background: #fff !important;
    border: 0 !important;
    border-radius: 0 !important;
    box-shadow: none !important;
    overflow: visible !important;
    width: 100% !important;
    max-width: none !important;
    min-height: 0 !important;
    height: auto !important;
    flex: none !important;
  }
}
</style>
