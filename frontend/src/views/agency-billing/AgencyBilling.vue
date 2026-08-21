<template>
  <div class="agency-billing">
    <PageShell :title="$t('clockai.agencyBills.title')" :subtitle="$t('clockai.agencyBills.subtitle')">
      <template #extra>
        <a-button :loading="exporting" @click="handleExport">
          {{ $t('export.startExport') }}
        </a-button>
      </template>
    </PageShell>

    <a-card class="surface-card" :bordered="false">
      <div class="filter-bar">
        <a-space wrap align="center">
          <a-button @click="shiftWeek(-1)">{{ $t('clockai.agencyBills.prevWeek') }}</a-button>
          <a-range-picker
            v-model:value="dateRange"
            value-format="YYYY-MM-DD"
            :allow-clear="false"
            :disabled-date="disabledBillingDateForPicker"
            class="date-range-picker"
            @open-change="onPickerOpenChange"
            @calendar-change="onBillingCalendarChange"
          />
          <a-button @click="shiftWeek(1)">{{ $t('clockai.agencyBills.nextWeek') }}</a-button>
          <span class="range-limit-hint">
            {{ $t('clockai.agencyBills.rangeLimitHint', { max: MAX_BILLING_RANGE_DAYS }) }}
          </span>
        </a-space>
        <a-select
          v-model:value="regionCodes"
          class="region-select"
          mode="multiple"
          :options="regionSelectOptions"
          :placeholder="$t('employees.regionFilterPlaceholder')"
          allow-clear
          :max-tag-count="2"
          show-search
          option-filter-prop="label"
        />
        <a-button type="primary" :loading="loading" @click="loadSummary">
          {{ $t('common.search') }}
        </a-button>
      </div>

      <a-table
        :columns="summaryColumns"
        :data-source="blocks"
        :loading="loading"
        row-key="rowKey"
        class="rich-table-header"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="openDetail(record)">
              {{ $t('clockai.agencyBills.viewReleve') }}
            </a-button>
          </template>
          <template v-else-if="column.key === 'agencyLabel'">
            {{ displayAgency(record) }}
          </template>
          <template v-else-if="column.key === 'warehouseLabel'">
            {{ displayWarehouse(record) }}
          </template>
          <template v-else-if="column.key === 'countryLabel'">
            {{ displayCountry(record) }}
          </template>
          <template v-else-if="column.key === 'totalHours'">
            {{ formatHours(record.totalHours) }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:open="detailOpen"
      :title="detailTitle"
      width="min(96vw, 1100px)"
      class="agency-detail-drawer"
    >
      <div v-if="detailLoading" class="detail-loading">
        <a-spin />
      </div>
      <template v-else-if="detail">
        <div class="releve-header">
          <div><strong>{{ $t('taskEdit.warehouse') }}</strong> {{ displayWarehouse(detail) || '—' }}</div>
          <div><strong>{{ $t('clockai.agencyBills.period') }}</strong> {{ detail.startDate }} ~ {{ detail.endDate }}</div>
          <div><strong>{{ $t('taskEdit.agency') }}</strong> {{ displayAgency(detail) || '—' }}</div>
          <div><strong>{{ $t('employees.colRegion') }}</strong> {{ displayCountry(detail) || '—' }}</div>
        </div>
        <div class="releve-summary">
          {{ $t('clockai.agencyBills.footerSummary', {
            count: detail.totalHeadcount,
            hours: formatHours(detail.totalHours),
          }) }}
        </div>
        <div class="expand-hint">{{ $t('clockai.agencyBills.expandHint') }}</div>
        <a-table
          :columns="detailColumns"
          :data-source="detail.rows"
          row-key="employeeKey"
          size="small"
          class="rich-table-header releve-table"
          :pagination="detailPagination"
          :scroll="{ x: detailScrollX }"
          v-model:expanded-row-keys="expandedRowKeys"
          expand-row-by-click
          @expand="handleExpand"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key?.startsWith('day-')">
              <span v-if="record.cells?.[column.dayKey]?.present" class="cell-present">
                <template v-if="record.cells[column.dayKey].workHours != null">
                  {{ formatHours(record.cells[column.dayKey].workHours) }}
                </template>
                <template v-else>✓</template>
              </span>
              <span v-else class="cell-empty">—</span>
            </template>
            <template v-else-if="column.key === 'totalHours'">
              {{ formatHours(record.totalHours) }}
            </template>
          </template>
          <template #expandedRowRender="{ record }">
            <div class="line-items">
              <table class="line-items-table">
                <thead>
                  <tr>
                    <th>{{ $t('clockai.agencyBills.colDate') }}</th>
                    <th>{{ $t('clockai.agencyBills.colShift') }}</th>
                    <th>{{ $t('clockai.agencyBills.colArrival') }}</th>
                    <th>{{ $t('clockai.agencyBills.colDeparture') }}</th>
                    <th>{{ $t('clockai.agencyBills.colBreak') }}</th>
                    <th>{{ $t('clockai.agencyBills.hours') }}</th>
                    <th>{{ $t('clockai.agencyBills.colObservations') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(line, idx) in record.lines || []" :key="idx">
                    <td>{{ line.workDate }}</td>
                    <td>{{ line.shift || '—' }}</td>
                    <td>{{ line.arrival || '—' }}</td>
                    <td>{{ line.departure || '—' }}</td>
                    <td>{{ line.pauseMinutes || '—' }}</td>
                    <td>{{ line.workHours != null ? formatHours(line.workHours) : '—' }}</td>
                    <td>{{ line.observations || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </a-table>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import isoWeek from 'dayjs/plugin/isoWeek'
import PageShell from '@/components/PageShell.vue'
import { getAgencyBillingSummary, getAgencyBillingDetail } from '@/api/agencyBilling'
import { createAgencyBillingExport } from '@/api/export'
import { useExportCenter } from '@/composables/useExportCenter'
import { useCountryStore } from '@/stores/country'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { normalizeCountryCode } from '@/utils/countryCatalog'
import { formatBillingBlock, formatBillingDetail, formatBillingCountry, formatBillingText } from '@/utils/billingLabels'
import {
  MAX_BILLING_RANGE_DAYS,
  clampBillingDateRange,
  createBillingDisabledDate,
  isBillingDateRangeValid,
} from '@/utils/agencyBillingDateRange'
import { showErrorMessage } from '@/utils/translateError'

dayjs.extend(isoWeek)

const { t, locale } = useI18n()
const countryStore = useCountryStore()
const { openExportCenter, refreshExportSummary } = useExportCenter()

const loading = ref(false)
const exporting = ref(false)
const blocks = ref([])
const rangeMeta = ref({ startDate: '', endDate: '' })
const regionCodes = ref(buildInitialRegionCodes())
const dateRange = ref(currentWeekRange())
const calendarDraft = ref(null)

function getBillingBounds() {
  if (calendarDraft.value) {
    return {
      start: calendarDraft.value[0] || null,
      end: calendarDraft.value[1] || null,
    }
  }
  return {
    start: dateRange.value[0] || null,
    end: dateRange.value[1] || null,
  }
}

const disabledBillingDateForPicker = createBillingDisabledDate(getBillingBounds)

function onBillingCalendarChange(values) {
  calendarDraft.value = values ?? null
}

function onPickerOpenChange(open) {
  if (!open) {
    calendarDraft.value = null
  }
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const activeBlock = ref(null)
const expandedRowKeys = ref([])

function handleExpand(expanded, record) {
  if (expanded) {
    expandedRowKeys.value = [...expandedRowKeys.value, record.employeeKey]
  } else {
    expandedRowKeys.value = expandedRowKeys.value.filter((k) => k !== record.employeeKey)
  }
}

const regionSelectOptions = computed(() => {
  void locale.value
  return countryStore.selectOptions
})

const summaryColumns = computed(() => [
  { title: t('taskEdit.agency'), dataIndex: 'agencyLabel', key: 'agencyLabel', ellipsis: true },
  { title: t('taskEdit.warehouse'), dataIndex: 'warehouseLabel', key: 'warehouseLabel', width: 120 },
  { title: t('employees.colRegion'), dataIndex: 'countryLabel', key: 'countryLabel', width: 120 },
  { title: t('clockai.agencyBills.headcount'), dataIndex: 'headcount', key: 'headcount', width: 90, align: 'center' },
  { title: t('clockai.agencyBills.attendanceDays'), dataIndex: 'attendanceDays', key: 'attendanceDays', width: 100, align: 'center' },
  { title: t('clockai.agencyBills.totalHours'), dataIndex: 'totalHours', key: 'totalHours', width: 110, align: 'right' },
  { title: '', key: 'action', width: 120 },
])

const detailTitle = computed(() => {
  if (!activeBlock.value) return t('clockai.agencyBills.detailTitle')
  return `${displayAgency(activeBlock.value)} · ${displayWarehouse(activeBlock.value) || '—'}`
})

function displayAgency(record) {
  return formatBillingText(record?.agencyLabel, record?.agencyKey)
}

function displayWarehouse(record) {
  return formatBillingText(record?.warehouseLabel, record?.warehouseKey)
}

function displayCountry(record) {
  return formatBillingCountry(record?.countryLabel, record?.countryKey, countryStore.options)
}

const detailColumns = computed(() => {
  const days = detail.value?.days || []
  const base = [
    { title: t('employees.colEmpNo'), dataIndex: 'empNo', key: 'empNo', fixed: 'left', width: 100 },
    { title: t('employees.colName'), dataIndex: 'displayName', key: 'displayName', fixed: 'left', width: 140, ellipsis: true },
  ]
  const dayCols = days.map((day) => ({
    title: formatDayHeader(day),
    key: `day-${day}`,
    dayKey: day,
    width: 72,
    align: 'center',
  }))
  return [
    ...base,
    ...dayCols,
    { title: t('clockai.agencyBills.hours'), dataIndex: 'totalHours', key: 'totalHours', width: 88, align: 'right', fixed: 'right' },
  ]
})

const detailScrollX = computed(() => 340 + (detail.value?.days?.length || 0) * 72 + 176)

const detailPagination = computed(() => ({
  pageSize: 50,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100', '200'],
  showTotal: (total) => t('clockai.agencyBills.detailTotal', { total }),
}))

function buildInitialRegionCodes() {
  const cached = getCachedWorkingCountry()
  if (cached && cached !== 'default') return [normalizeCountryCode(cached)]
  return []
}

function currentWeekRange() {
  const start = dayjs().startOf('isoWeek')
  const end = dayjs().endOf('isoWeek')
  return [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

function shiftWeek(delta) {
  const start = dayjs(dateRange.value[0]).add(delta, 'week')
  const end = dayjs(dateRange.value[1]).add(delta, 'week')
  dateRange.value = clampBillingDateRange([
    start.format('YYYY-MM-DD'),
    end.format('YYYY-MM-DD'),
  ])
  loadSummary()
}

function ensureValidDateRange() {
  if (!dateRange.value?.[0] || !dateRange.value?.[1]) {
    message.warning(t('clockai.agencyBills.dateRequired'))
    return false
  }
  const clamped = clampBillingDateRange(dateRange.value)
  if (clamped[0] !== dateRange.value[0] || clamped[1] !== dateRange.value[1]) {
    message.warning(t('clockai.agencyBills.rangeTooLong', { max: MAX_BILLING_RANGE_DAYS }))
    dateRange.value = clamped
  }
  if (!isBillingDateRangeValid(dateRange.value)) {
    message.warning(t('clockai.agencyBills.rangeTooLong', { max: MAX_BILLING_RANGE_DAYS }))
    return false
  }
  return true
}

function formatDayHeader(day) {
  const d = dayjs(day)
  const labels = ['Lu', 'Ma', 'Me', 'Je', 'Ve', 'Sa', 'Di']
  return `${labels[d.isoWeekday() - 1]}\n${day.slice(5)}`
}

function formatHours(value) {
  const n = Number(value)
  if (Number.isNaN(n)) return '—'
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function buildQueryParams(extra = {}) {
  return {
    startDate: dateRange.value[0],
    endDate: dateRange.value[1],
    regionCodes: regionCodes.value?.length ? regionCodes.value.join(',') : undefined,
    ...extra,
  }
}

async function loadSummary() {
  if (!ensureValidDateRange()) return
  loading.value = true
  try {
    const res = await getAgencyBillingSummary(buildQueryParams())
    const data = res.data || {}
    rangeMeta.value = { startDate: data.startDate, endDate: data.endDate }
    blocks.value = (data.blocks || []).map((block, index) => ({
      ...formatBillingBlock(block, countryStore.options),
      rowKey: `${block.agencyKey}-${block.warehouseKey}-${block.countryKey}-${index}`,
    }))
  } catch (e) {
    showErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function openDetail(block) {
  activeBlock.value = block
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  expandedRowKeys.value = []
  try {
    const res = await getAgencyBillingDetail(buildQueryParams({
      agencyKey: block.agencyKey,
      warehouseKey: block.warehouseKey || '',
      countryKey: block.countryKey || '',
    }))
    detail.value = formatBillingDetail(res.data || null, countryStore.options)
  } catch (e) {
    showErrorMessage(e)
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleExport() {
  if (!ensureValidDateRange()) return
  exporting.value = true
  try {
    await createAgencyBillingExport(buildQueryParams())
    message.success(t('export.queued'))
    refreshExportSummary()
    openExportCenter()
  } catch (e) {
    showErrorMessage(e)
  } finally {
    exporting.value = false
  }
}

watch(dateRange, (val) => {
  if (!val?.[0] || !val?.[1]) return
  const clamped = clampBillingDateRange(val)
  if (clamped[0] !== val[0] || clamped[1] !== val[1]) {
    message.warning(t('clockai.agencyBills.rangeTooLong', { max: MAX_BILLING_RANGE_DAYS }))
    dateRange.value = clamped
  }
}, { deep: true })

watch(detailOpen, (open) => {
  if (!open) {
    detail.value = null
    activeBlock.value = null
  }
})

onMounted(async () => {
  try {
    await countryStore.hydrate()
  } catch {
    /* ignore */
  }
  loadSummary()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.date-range-picker {
  min-width: 260px;
}
.range-limit-hint {
  color: var(--text-secondary, #999);
  font-size: 12px;
}
.region-select {
  min-width: 220px;
  max-width: 360px;
}
.releve-header {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin-bottom: 12px;
  font-size: 13px;
}
.releve-summary {
  margin-bottom: 8px;
  color: var(--text-secondary, #666);
  font-size: 13px;
}
.expand-hint {
  margin-bottom: 12px;
  color: var(--text-secondary, #999);
  font-size: 12px;
}
.cell-present {
  color: #389e0d;
  font-weight: 500;
}
.cell-empty {
  color: #bbb;
}
.detail-loading {
  display: flex;
  justify-content: center;
  padding: 48px;
}
.line-items {
  padding: 8px 0 8px 24px;
}
.line-items-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.line-items-table th,
.line-items-table td {
  border: 1px solid #f0f0f0;
  padding: 6px 8px;
  text-align: left;
}
.line-items-table th {
  background: #fafafa;
}
</style>
