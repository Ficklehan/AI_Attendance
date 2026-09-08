<template>
  <div class="print-sheet-page">
    <PageShell :title="$t('printSheet.title')" :subtitle="$t('printSheet.subtitle')" inline-subtitle>
      <template #extra>
        <a-space>
          <a-button @click="handleDownloadExcel">
            {{ $t('printSheet.downloadExcel') }}
          </a-button>
          <a-button type="primary" @click="handlePrint">
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
            />
          </a-form-item>
        </div>
        <p v-if="countryMismatch" class="print-sheet-form__warn">
          {{ $t('printSheet.countryMismatchHint') }}
        </p>
      </a-form>
    </a-card>

    <div class="print-sheet-page__preview-wrap">
      <AttendanceSignInSheet
        :sheet-lang="sheetLocale"
        :country-label="printedCountryLabel"
        :warehouse="warehouse.trim()"
        :printed-date="previewPrintedDate"
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
import { buildCountrySelectOption, translateCountryName } from '@/utils/countryLabels'
import { buildLanguageSelectOptions } from '@/constants/languageOptions'
import { getMyDataScope } from '@/api/dataScope'
import {
  downloadSheetExcel,
  formatSheetDateMdY,
  loadLastWarehouse,
  loadRecentWarehouses,
  mergeWarehouseSuggestions,
  rememberWarehouse,
  resolvePrintIsoDate,
  resolveSheetLocale,
  SHEET_BLANK_ROWS,
  todayIsoDate,
} from '@/utils/attendanceSheetPrint'

const PRINT_BODY_CLASS = 'printing-sign-in-sheet'

const { t, locale } = useI18n()
const countryStore = useCountryStore()

const countryCode = ref(undefined)
const warehouse = ref('')
const workDate = ref(todayIsoDate())
const dateTouched = ref(false)
const scopedWarehouses = ref([])
const recentWarehouses = ref([])
const warehouseSuggestOpen = ref(false)
const sheetLocale = ref(resolveSheetLocale(locale.value))
const sheetLanguageOptions = buildLanguageSelectOptions()

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

function applyDefaultCountry() {
  const working = countryStore.workingCountry
  if (working && working !== 'default') {
    countryCode.value = working
    return
  }
  const first = countryOptions.value[0]?.value
  countryCode.value = first || undefined
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
  rememberWarehouse(nextWarehouse)
  return {
    sheetLocale: sheetLocale.value,
    countryLabel: printedCountryLabel.value,
    warehouse: nextWarehouse,
    printedDate: formatSheetDateMdY(workDate.value),
    rowCount: SHEET_BLANK_ROWS,
  }
}

function handlePrint() {
  if (!prepareSheetPayload()) return
  window.print()
}

function handleDownloadExcel() {
  const payload = prepareSheetPayload()
  if (!payload) return
  downloadSheetExcel()
  message.success(t('printSheet.downloadExcelDone'))
}

onMounted(async () => {
  document.body.classList.add(PRINT_BODY_CLASS)
  try {
    await countryStore.hydrate()
  } catch (error) {
    console.error('加载工作地区失败:', error)
  }
  applyDefaultCountry()
  warehouse.value = loadLastWarehouse()
  recentWarehouses.value = loadRecentWarehouses()
  try {
    const res = await getMyDataScope({ silentError: true })
    scopedWarehouses.value = Array.isArray(res?.data?.warehouses) ? res.data.warehouses : []
  } catch {
    scopedWarehouses.value = []
  }
})

onUnmounted(() => {
  document.body.classList.remove(PRINT_BODY_CLASS)
})

watch(
  () => countryStore.workingCountry,
  () => {
    if (!countryCode.value || countryCode.value === 'default') applyDefaultCountry()
  },
)

watch(locale, (next) => {
  sheetLocale.value = resolveSheetLocale(next)
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
  body.printing-sign-in-sheet .ant-popover,
  body.printing-sign-in-sheet .ant-message,
  body.printing-sign-in-sheet .ant-notification {
    display: none !important;
  }

  body.printing-sign-in-sheet .layout-container,
  body.printing-sign-in-sheet .main-content,
  body.printing-sign-in-sheet .ant-layout,
  body.printing-sign-in-sheet .ant-layout-content,
  body.printing-sign-in-sheet .print-sheet-page,
  body.printing-sign-in-sheet .print-sheet-page__preview-wrap {
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
