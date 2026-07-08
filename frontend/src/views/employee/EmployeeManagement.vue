<template>
  <div class="employee-mgmt page-inner">
    <PageShell :title="$t('employees.title')" :subtitle="$t('employees.subtitle')">
      <template #extra>
        <a-button v-if="canManageRoles(authStore)" :loading="backfilling" @click="handleBackfill">
          {{ $t('employees.backfill') }}
        </a-button>
      </template>
    </PageShell>

    <a-card class="surface-card" :bordered="false">
      <a-tabs v-model:active-key="activeTab">
        <a-tab-pane key="list" :tab="$t('employees.tabList')">
          <div class="filter-bar">
            <a-input
              v-model:value="keyword"
              class="search-input"
              :placeholder="$t('employees.searchPlaceholder')"
              allow-clear
              @press-enter="loadList"
            />
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
              @change="loadList"
            />
            <a-button type="primary" @click="loadList">{{ $t('common.search') }}</a-button>
          </div>
          <a-table
            :columns="listColumns"
            :data-source="employees"
            :loading="listLoading"
            row-key="id"
            class="rich-table-header"
            :pagination="listPagination"
            @change="handleListTableChange"
          >
            <template #emptyText>
              <a-empty :description="$t('employees.emptyList')" />
            </template>
            <template #bodyCell="{ column, record, text }">
              <template v-if="column.key === 'regionCode'">
                <CopyableCell :text="formatEmployeeRegion(record.regionCode)" />
              </template>
              <template v-else-if="isCopyableTableColumn(column)">
                <CopyableCell :text="resolveTableCellCopyText(column, record, text)" />
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="weekly" :tab="$t('employees.tabWeekly')">
          <div class="filter-bar weekly-bar">
            <a-space wrap :size="12">
              <a-button @click="shiftWeek(-1)">{{ $t('employees.prevWeek') }}</a-button>
              <a-date-picker
                v-model:value="weekValue"
                class="week-picker"
                picker="week"
                :allow-clear="false"
                :placeholder="$t('employees.selectWeek')"
                @change="loadWeekly"
              />
              <a-button @click="shiftWeek(1)">{{ $t('employees.nextWeek') }}</a-button>
              <span v-if="weeklyRangeLabel" class="week-range">{{ weeklyRangeLabel }}</span>
            </a-space>
            <a-space wrap :size="12">
              <a-input
                v-model:value="weeklyKeyword"
                class="search-input"
                :placeholder="$t('employees.searchPlaceholder')"
                allow-clear
                @press-enter="loadWeekly"
              />
              <a-select
                v-model:value="weeklyRegionCodes"
                class="region-select"
                mode="multiple"
                :options="regionSelectOptions"
                :placeholder="$t('employees.regionFilterPlaceholder')"
                allow-clear
                :max-tag-count="2"
                show-search
                option-filter-prop="label"
                @change="loadWeekly"
              />
              <a-button type="primary" @click="loadWeekly">{{ $t('common.search') }}</a-button>
            </a-space>
          </div>
          <a-table
            :columns="weeklyColumns"
            :data-source="weeklyRows"
            :loading="weeklyLoading"
            row-key="employeeId"
            class="rich-table-header"
            :pagination="false"
            :scroll="{ x: weeklyScrollX }"
          >
            <template #emptyText>
              <a-empty :description="$t('employees.emptyWeekly')" />
            </template>
            <template #bodyCell="{ column, record, text }">
              <template v-if="column.key?.startsWith('day-')">
                <CopyableCell
                  v-if="record.cells?.[column.dayKey]?.present"
                  :text="record.cells[column.dayKey].workHours != null ? formatHours(record.cells[column.dayKey].workHours) : '✓'"
                >
                  <span class="weekly-cell weekly-cell--present">
                    <template v-if="record.cells[column.dayKey].workHours != null">
                      {{ formatHours(record.cells[column.dayKey].workHours) }}
                    </template>
                    <template v-else>✓</template>
                  </span>
                </CopyableCell>
                <span v-else class="weekly-cell weekly-cell--empty">—</span>
              </template>
              <template v-else-if="isCopyableTableColumn(column)">
                <CopyableCell :text="resolveTableCellCopyText(column, record, text)" />
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import PageShell from '@/components/PageShell.vue'
import CopyableCell from '@/components/CopyableCell.vue'
import { getEmployeeList, getWeeklyAttendance, backfillEmployees } from '@/api/employee'
import { getMyDataScope } from '@/api/dataScope'
import { useAuthStore } from '@/stores/auth'
import { canManageRoles } from '@/utils/permissions'
import { useCountryStore } from '@/stores/country'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { resolveCountryDisplayLabel } from '@/utils/countryLabels'
import { normalizeCountryCode } from '@/utils/countryCatalog'
import { isCopyableTableColumn, resolveTableCellCopyText } from '@/utils/tableCopy'

const { t, locale } = useI18n()
const authStore = useAuthStore()
const countryStore = useCountryStore()

const dataScope = ref({
  allUsers: true,
  workRegions: [],
})

function allowedRegionCodes() {
  if (dataScope.value.allUsers) return null
  return (dataScope.value.workRegions || [])
    .map((code) => normalizeCountryCode(code))
    .filter(Boolean)
}

function buildInitialRegionCodes() {
  const allowed = allowedRegionCodes()
  const cached = getCachedWorkingCountry()
  const cachedNorm = cached && cached !== 'default' ? normalizeCountryCode(cached) : null
  if (allowed && allowed.length > 0) {
    if (cachedNorm && allowed.includes(cachedNorm)) return [cachedNorm]
    if (allowed.length === 1) return [allowed[0]]
    return []
  }
  if (cachedNorm) return [cachedNorm]
  return []
}

const activeTab = ref('list')
const keyword = ref('')
const regionCodes = ref(buildInitialRegionCodes())
const employees = ref([])
const listLoading = ref(false)
const backfilling = ref(false)

const listPagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: true,
})

const isoWeek = ref(currentIsoWeek())
const weeklyKeyword = ref('')
const weeklyRegionCodes = ref(buildInitialRegionCodes())
const weeklyDays = ref([])
const weeklyRangeLabel = ref('')
const weeklyRows = ref([])
const weeklyLoading = ref(false)

// 周选择器 <-> ISO 周字符串（沿用现有 ISO 周解析，保证与后端一致）
const weekValue = computed({
  get() {
    const days = parseIsoWeek(isoWeek.value)
    return days?.length ? dayjs(`${days[0]}T12:00:00`) : null
  },
  set(val) {
    if (!val) return
    isoWeek.value = currentIsoWeek(val.toDate())
  },
})

const regionSelectOptions = computed(() => {
  void locale.value
  const all = countryStore.selectOptions || []
  const allowed = allowedRegionCodes()
  if (!allowed) return all
  const allowedSet = new Set(allowed)
  return all.filter((item) => {
    const code = normalizeCountryCode(item.value || item.code)
    return allowedSet.has(code)
  })
})

const listColumns = computed(() => [
  { title: t('employees.colEmpNo'), dataIndex: 'empNo', key: 'empNo', width: 120 },
  { title: t('employees.colName'), dataIndex: 'displayName', key: 'displayName', ellipsis: true },
  { title: t('employees.colRegion'), dataIndex: 'regionCode', key: 'regionCode', width: 140 },
  { title: t('employees.colAgency'), dataIndex: 'agencyKey', key: 'agencyKey', ellipsis: true },
  { title: t('employees.colFirstCreated'), dataIndex: 'firstCreatedAt', key: 'firstCreatedAt', width: 170 },
  { title: t('employees.colLastAttendance'), dataIndex: 'lastAttendanceDate', key: 'lastAttendanceDate', width: 130 },
])

const weeklyColumns = computed(() => {
  const base = [
    { title: t('employees.colEmpNo'), dataIndex: 'empNo', key: 'empNo', fixed: 'left', width: 110 },
    { title: t('employees.colName'), dataIndex: 'displayName', key: 'displayName', fixed: 'left', width: 140, ellipsis: true },
  ]
  const dayCols = (weeklyDays.value || []).map((day) => ({
    title: formatDayHeader(day),
    key: `day-${day}`,
    dayKey: day,
    width: 88,
    align: 'center',
  }))
  return [...base, ...dayCols]
})

const weeklyScrollX = computed(() => 260 + (weeklyDays.value?.length || 0) * 88)

function formatEmployeeRegion(regionCode) {
  const raw = String(regionCode || '').trim()
  if (!raw) return ''
  const code = raw.toUpperCase() === 'DEFAULT' ? 'default' : normalizeCountryCode(raw)
  return resolveCountryDisplayLabel(code, countryStore.options)
}

function currentIsoWeek(date = new Date()) {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  const week = Math.ceil((((d - yearStart) / 86400000) + 1) / 7)
  const year = d.getUTCFullYear()
  return `${year}-W${String(week).padStart(2, '0')}`
}

function parseIsoWeek(iso) {
  const match = /^(\d{4})-W(\d{2})$/i.exec(String(iso || '').trim())
  if (!match) return null
  const year = Number(match[1])
  const week = Number(match[2])
  const simple = new Date(Date.UTC(year, 0, 1 + (week - 1) * 7))
  const dow = simple.getUTCDay()
  const monday = new Date(simple)
  if (dow <= 4) {
    monday.setUTCDate(simple.getUTCDate() - simple.getUTCDay() + 1)
  } else {
    monday.setUTCDate(simple.getUTCDate() + 8 - simple.getUTCDay())
  }
  const days = []
  for (let i = 0; i < 7; i += 1) {
    const d = new Date(monday)
    d.setUTCDate(monday.getUTCDate() + i)
    days.push(d.toISOString().slice(0, 10))
  }
  return days
}

function shiftWeek(delta) {
  const days = parseIsoWeek(isoWeek.value)
  if (!days?.length) return
  const pivot = new Date(`${days[0]}T12:00:00Z`)
  pivot.setUTCDate(pivot.getUTCDate() + delta * 7)
  isoWeek.value = currentIsoWeek(pivot)
  loadWeekly()
}

function formatDayHeader(day) {
  if (!day) return ''
  const d = new Date(`${day}T12:00:00Z`)
  const weekday = t(`employees.weekdays.${d.getUTCDay()}`)
  return `${day.slice(5)}\n${weekday}`
}

function formatHours(value) {
  const n = Number(value)
  if (Number.isNaN(n)) return '✓'
  return n.toFixed(1)
}

async function loadList() {
  listLoading.value = true
  try {
    const res = await getEmployeeList({
      page: listPagination.current,
      size: listPagination.pageSize,
      keyword: keyword.value || undefined,
      regionCodes: regionCodes.value,
    })
    const page = res.data || {}
    employees.value = page.records || []
    listPagination.total = page.total || 0
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    listLoading.value = false
  }
}

async function loadWeekly() {
  weeklyLoading.value = true
  try {
    const res = await getWeeklyAttendance({
      isoWeek: isoWeek.value || undefined,
      keyword: weeklyKeyword.value || undefined,
      regionCodes: weeklyRegionCodes.value,
    })
    const data = res.data || {}
    weeklyDays.value = data.days || []
    weeklyRangeLabel.value = data.startDate && data.endDate
      ? `${data.startDate} ~ ${data.endDate}`
      : ''
    isoWeek.value = data.isoWeek || isoWeek.value
    weeklyRows.value = (data.rows || []).map((row) => ({
      ...row,
      cells: row.cells || {},
    }))
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    weeklyLoading.value = false
  }
}

function handleListTableChange(pag) {
  listPagination.current = pag.current
  listPagination.pageSize = pag.pageSize
  loadList()
}

async function handleBackfill() {
  Modal.confirm({
    title: t('employees.backfillConfirmTitle'),
    content: t('employees.backfillConfirmContent'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: runBackfill,
  })
}

async function runBackfill() {
  backfilling.value = true
  try {
    const res = await backfillEmployees()
    const data = res.data || {}
    const skipped = data.recordsSkipped || 0
    const msgKey = skipped > 0 ? 'employees.backfillDoneSkipped' : 'employees.backfillDone'
    message.success(t(msgKey, {
      tasks: data.tasksUpdated || 0,
      records: data.recordsAssigned || 0,
      skipped,
    }))
    await loadList()
  } catch (e) {
    message.error(e.message || t('common.error'))
  } finally {
    backfilling.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'weekly' && weeklyRows.value.length === 0) {
    loadWeekly()
  }
})

onMounted(async () => {
  try {
    await countryStore.hydrate()
  } catch {
    /* ignore */
  }
  try {
    const res = await getMyDataScope()
    const scope = res?.data || {}
    dataScope.value = {
      allUsers: scope.allUsers === true,
      workRegions: Array.isArray(scope.workRegions) ? scope.workRegions : [],
    }
    regionCodes.value = buildInitialRegionCodes()
    weeklyRegionCodes.value = buildInitialRegionCodes()
  } catch {
    /* ignore */
  }
  loadList()
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
.search-input {
  width: 260px;
}
.region-select {
  min-width: 220px;
  max-width: 360px;
}
.week-picker {
  width: 180px;
}
.week-range {
  color: var(--text-secondary, #666);
  font-size: 13px;
}
.weekly-bar {
  justify-content: space-between;
}
.weekly-cell--present {
  color: #389e0d;
  font-weight: 500;
}
.weekly-cell--empty {
  color: #bbb;
}
</style>
