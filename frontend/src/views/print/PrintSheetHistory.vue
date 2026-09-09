<template>
  <div class="print-history-page">
    <PageShell :title="$t('printSheet.historyTitle')" :subtitle="$t('printSheet.historySubtitle')">
      <template #extra>
        <a-button type="primary" @click="$router.push('/print-sheet')">
          {{ $t('printSheet.title') }}
        </a-button>
      </template>
    </PageShell>

    <a-card class="surface-card" :bordered="false">
      <a-form layout="inline" class="print-history-filters">
        <a-form-item :label="$t('printSheet.country')">
          <a-select
            v-model:value="filters.countryCode"
            allow-clear
            show-search
            option-filter-prop="label"
            style="min-width: 180px"
            :options="countryOptions"
            :placeholder="$t('printSheet.countryPlaceholder')"
          />
        </a-form-item>
        <a-form-item :label="$t('printSheet.historyDateRange')">
          <a-range-picker
            v-model:value="filters.range"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="loading" @click="loadDays">
              {{ $t('printSheet.historySearch') }}
            </a-button>
            <a-button @click="resetFilters">{{ $t('printSheet.historyReset') }}</a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <a-table
        row-key="rowKey"
        :columns="columns"
        :data-source="days"
        :loading="loading"
        :pagination="{ pageSize: 20 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'country'">
            {{ countryLabel(record.countryCode) }}
          </template>
          <template v-else-if="column.key === 'warehouses'">
            {{ (record.warehouses || []).join('、') || '—' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" @click="openDetail(record)">
              {{ $t('printSheet.historyViewPersons') }}
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:open="detailOpen"
      width="860"
      :title="detailTitle"
      destroy-on-close
    >
      <a-spin :spinning="detailLoading">
        <a-collapse v-if="groupedPersons.length" :bordered="false" :default-active-key="defaultOpenKeys">
          <a-collapse-panel
            v-for="group in groupedPersons"
            :key="group.key"
          >
            <template #header>
              <div class="print-history-group-header">
                <span>{{ group.header }}</span>
                <a-button
                  type="link"
                  size="small"
                  @click.stop="reprintGroup(group)"
                >
                  {{ $t('printSheet.reprint') }}
                </a-button>
              </div>
            </template>
            <a-table
              size="small"
              row-key="id"
              :pagination="false"
              :columns="personColumns"
              :data-source="group.persons"
            />
          </a-collapse-panel>
        </a-collapse>
        <a-empty v-else :description="$t('printSheet.historyEmptyPersons')" />
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import PageShell from '@/components/PageShell.vue'
import { useCountryStore } from '@/stores/country'
import { buildCountrySelectOption, translateCountryName } from '@/utils/countryLabels'
import { getPrintSheetDayDetail, listPrintSheetDays } from '@/api/printSheet'
import { saveReprintPayload } from '@/utils/attendanceSheetPrint'

const { t, locale } = useI18n()
const router = useRouter()
const countryStore = useCountryStore()

const loading = ref(false)
const days = ref([])
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const filters = reactive({
  countryCode: undefined,
  range: [],
})

const countryOptions = computed(() => {
  void locale.value
  return (countryStore.options || [])
    .filter((item) => item.code && item.code !== 'default')
    .map((item) => buildCountrySelectOption(item))
})

const columns = computed(() => [
  { title: t('printSheet.country'), key: 'country', width: 140 },
  { title: t('printSheet.date'), dataIndex: 'workDate', key: 'workDate', width: 140 },
  { title: t('printSheet.historyPersonCount'), dataIndex: 'personCount', key: 'personCount', width: 100 },
  { title: t('printSheet.historyJobCount'), dataIndex: 'jobCount', key: 'jobCount', width: 100 },
  { title: t('printSheet.warehouse'), key: 'warehouses' },
  { title: t('printSheet.historyAction'), key: 'action', width: 120 },
])

const personColumns = computed(() => [
  { title: t('printSheet.colSeq'), dataIndex: 'seqNo', key: 'seqNo', width: 80 },
  { title: t('printSheet.colName'), dataIndex: 'personName', key: 'personName' },
  { title: t('printSheet.colAgency'), dataIndex: 'agencyName', key: 'agencyName' },
  { title: t('printSheet.colShift'), dataIndex: 'shiftName', key: 'shiftName', width: 120 },
  { title: t('printSheet.warehouse'), dataIndex: 'warehouse', key: 'warehouse', width: 120 },
  { title: t('printSheet.historyPrintedAt'), dataIndex: 'printedAt', key: 'printedAt', width: 180 },
])

const detailTitle = computed(() => {
  if (!detail.value) return t('printSheet.historyDetailTitle')
  return t('printSheet.historyDetailTitleWithDay', {
    country: countryLabel(detail.value.countryCode),
    date: detail.value.workDate,
  })
})

const groupedPersons = computed(() => {
  const persons = detail.value?.persons || []
  const map = new Map()
  for (const person of persons) {
    const warehouse = person.warehouse || '—'
    const printedAt = person.printedAt || ''
    const jobId = person.jobId
    const key = jobId != null ? `job-${jobId}` : `${warehouse}@@${printedAt}`
    if (!map.has(key)) {
      map.set(key, {
        key,
        jobId,
        warehouse,
        printedAt,
        header: t('printSheet.historyGroupHeader', {
          warehouse,
          time: formatPrintedAt(printedAt),
          count: 0,
        }),
        persons: [],
      })
    }
    const group = map.get(key)
    group.persons.push(person)
    group.header = t('printSheet.historyGroupHeader', {
      warehouse: group.warehouse,
      time: formatPrintedAt(group.printedAt),
      count: group.persons.length,
    })
  }
  return Array.from(map.values()).sort((a, b) => {
    const ta = String(a.printedAt || '')
    const tb = String(b.printedAt || '')
    if (ta !== tb) return tb.localeCompare(ta)
    return Number(b.jobId || 0) - Number(a.jobId || 0)
  })
})

const defaultOpenKeys = computed(() => groupedPersons.value.map((item) => item.key))

function formatPrintedAt(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

function countryLabel(code) {
  if (!code) return '—'
  const found = (countryStore.options || []).find((item) => item.code === code)
  return translateCountryName(code, found?.name || code, locale.value)
}

function resetFilters() {
  filters.countryCode = undefined
  filters.range = []
  loadDays()
}

async function loadDays() {
  loading.value = true
  try {
    const params = {}
    if (filters.countryCode) params.countryCode = filters.countryCode
    if (Array.isArray(filters.range) && filters.range.length === 2) {
      params.from = filters.range[0]
      params.to = filters.range[1]
    }
    const res = await listPrintSheetDays(params)
    const list = Array.isArray(res?.data) ? res.data : []
    days.value = list.map((item) => ({
      ...item,
      rowKey: `${item.countryCode}-${item.workDate}`,
    }))
  } catch (error) {
    console.error(error)
    message.error(t('printSheet.historyLoadFailed'))
  } finally {
    loading.value = false
  }
}

async function openDetail(record) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getPrintSheetDayDetail({
      countryCode: record.countryCode,
      workDate: record.workDate,
    })
    detail.value = res?.data || null
  } catch (error) {
    console.error(error)
    message.error(t('printSheet.historyLoadFailed'))
  } finally {
    detailLoading.value = false
  }
}

function reprintGroup(group) {
  if (!detail.value) return
  const jobs = Array.isArray(detail.value.jobs) ? detail.value.jobs : []
  const job = jobs.find((item) => item.id === group.jobId) || {}
  const rows = (group.persons || []).map((person) => ({
    seq: person.seqNo === 0 || person.seqNo ? String(person.seqNo) : '',
    name: String(person.personName || ''),
    agency: String(person.agencyName || ''),
    shift: String(person.shiftName || ''),
  }))
  if (!rows.length) {
    message.warning(t('printSheet.historyEmptyPersons'))
    return
  }
  const saved = saveReprintPayload({
    countryCode: detail.value.countryCode || job.countryCode,
    workDate: detail.value.workDate || job.workDate,
    warehouse: group.warehouse !== '—' ? group.warehouse : (job.warehouse || ''),
    sheetLocale: job.sheetLocale || '',
    rows,
  })
  if (!saved) {
    message.error(t('printSheet.reprintFailed'))
    return
  }
  detailOpen.value = false
  router.push('/print-sheet')
}

onMounted(async () => {
  try {
    await countryStore.hydrate()
  } catch (error) {
    console.error(error)
  }
  await loadDays()
})
</script>

<style lang="scss" scoped>
.print-history-filters {
  margin-bottom: 16px;
}

.print-history-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding-right: 8px;
}
</style>
