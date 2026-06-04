<template>
  <div class="task-records-container page-inner">
    <PageShell :title="$t('tasks.recordTitle')" :subtitle="$t('tasks.recordSubtitle')">
      <template #extra>
        <a-button :loading="exporting" @click="handleExport">
          {{ $t('export.startExport') }}
        </a-button>
        <a-button @click="$router.push('/tasks')">{{ $t('taskEdit.backToList') }}</a-button>
      </template>
    </PageShell>

    <a-card class="surface-card" :bordered="false">
      <div class="filter-bar">
        <a-select v-model:value="filterStatus" class="status-select" :placeholder="$t('tasks.filterStatus')" allow-clear @change="handleFilter">
          <a-select-option value="">{{ $t('tasks.allStatus') }}</a-select-option>
          <a-select-option value="processing">{{ $t('tasks.statusProcessing') }}</a-select-option>
          <a-select-option value="processed">{{ $t('tasks.statusProcessed') }}</a-select-option>
          <a-select-option value="confirmed">{{ $t('tasks.statusConfirmed') }}</a-select-option>
          <a-select-option value="failed">{{ $t('tasks.statusFailed') }}</a-select-option>
          <a-select-option value="cancelled">{{ $t('tasks.statusCancelled') }}</a-select-option>
        </a-select>
        <a-input v-model:value="quickKeyword" class="search-input" :placeholder="$t('tasks.searchContent')" allow-clear @keyup.enter="handleFilter" />
        <a-button @click="toggleAdvancedSearch">
          {{ showAdvancedSearch ? $t('tasks.hideAdvancedSearch') : $t('tasks.advancedSearch') }}
        </a-button>
        <a-button type="primary" @click="handleFilter">{{ $t('common.search') }}</a-button>
      </div>
      <div v-if="showAdvancedSearch" class="advanced-panel">
        <div class="advanced-title">{{ $t('tasks.advancedSearch') }}</div>
        <div class="advanced-grid">
          <div v-for="def in searchableFieldDefs" :key="def.field" class="advanced-item">
            <label class="advanced-label">{{ def.label }}</label>
            <a-input v-model:value="advancedFilters[def.field]" :placeholder="$t('tasks.searchContent')" allow-clear @keyup.enter="handleFilter" />
          </div>
        </div>
      </div>

      <a-table
        class="rich-table-header"
        :columns="columns"
        :data-source="displayRecords"
        :loading="loading"
        :pagination="false"
        row-key="rowKey"
        :scroll="{ x: scrollX }"
      >
        <template #headerCell="{ column }">
          <TableSortableHeader
            :column="column"
            :title="column.title"
            @sort="onSorterToggle"
          >
            <template #extra>
              <TableHeaderFilter
                v-if="column.searchField"
                :title="getFieldLabel(column.searchField)"
                :open="activeHeaderFilterField === column.searchField"
                :active="Boolean((advancedFilters[column.searchField] || '').trim())"
                v-model:keyword="headerFilterKeyword"
                @openChange="(open) => handleHeaderPopoverOpen(column.searchField, open)"
                @reset="clearHeaderFilter"
                @apply="applyHeaderFilter"
              />
            </template>
          </TableSortableHeader>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskStatus'">
            <a-tag :color="getStatusColor(record.taskStatus)">{{ getStatusText(record.taskStatus) }}</a-tag>
          </template>
          <template v-if="column.key === 'taskId'">
            <a-button type="link" size="small" @click="openTask(record.taskId)">{{ record.taskId }}</a-button>
          </template>
          <template v-if="column.key === 'imageUrls'">
            <a-button type="link" size="small" @click="previewImages(record)">{{ $t('tasks.imagePreview') }}</a-button>
          </template>
        </template>
      </a-table>

      <div class="pagination-wrapper">
        <a-pagination
          v-model:current="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-size-options="[20, 50, 100]"
          show-size-changer
          :show-total="(v) => $t('tasks.totalRecords', { total: v })"
          @change="handleCurrentChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>

    <ImagePreviewModal
      v-model:open="previewVisible"
      :images="previewImagesList"
      :initial-index="previewCurrentIndex"
      :title="$t('tasks.imagePreview')"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'

import TableHeaderFilter from '@/components/TableHeaderFilter.vue'
import { getEmployeeRecordList } from '@/api/task'
import { createEmployeeRecordsExport } from '@/api/export'
import { useExportCenter } from '@/composables/useExportCenter'
import { resolveTaskImageUrls } from '@/utils/imageUrl'
import PageShell from '@/components/PageShell.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useAutoSizedColumns } from '@/composables/useAutoSizedColumns'

const { t } = useI18n()
const router = useRouter()
const { openExportCenter, refreshExportSummary } = useExportCenter()

const loading = ref(false)
const exporting = ref(false)
const records = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const quickKeyword = ref('')
const showAdvancedSearch = ref(false)
const activeHeaderFilterField = ref('')
const headerFilterKeyword = ref('')
const advancedFilters = ref({
  taskId: '',
  fileKey: '',
  userName: '',
  status: '',
  createdAt: '',
  NO: '',
  NOM_PRENOM: '',
  Pays: '',
  Entrepot: '',
  Date: '',
  AGENCE_INTERIMAIRE: '',
  HORAIRES_DU_TRAVAIL: '',
  ARRIVEE: '',
  DEPAR: '',
  PAUSE: '',
  SIGNATURE: '',
  Observations: '',
  PAGE_NUM: '',
})
const previewVisible = ref(false)
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)

const searchableFieldDefs = [
  { field: 'taskId', label: t('tasks.taskId') },
  { field: 'fileKey', label: t('tasks.fileName') },
  { field: 'userName', label: t('tasks.operator') },
  { field: 'status', label: t('tasks.status') },
  { field: 'createdAt', label: t('tasks.createTime') },
  { field: 'PAGE_NUM', label: t('taskEdit.pageNumber') },
  { field: 'NO', label: t('taskEdit.workerNumber') },
  { field: 'NOM_PRENOM', label: t('taskEdit.name') },
  { field: 'Pays', label: t('taskEdit.countryField') },
  { field: 'Entrepot', label: t('taskEdit.warehouse') },
  { field: 'Date', label: t('taskEdit.date') },
  { field: 'AGENCE_INTERIMAIRE', label: t('taskEdit.agency') },
  { field: 'HORAIRES_DU_TRAVAIL', label: t('taskEdit.shift') },
  { field: 'ARRIVEE', label: t('taskEdit.arrival') },
  { field: 'DEPAR', label: t('taskEdit.departure') },
  { field: 'PAUSE', label: t('taskEdit.breakTime') },
  { field: 'SIGNATURE', label: t('taskEdit.signature') },
  { field: 'Observations', label: t('taskEdit.observations') },
]

const baseColumns = computed(() => [
  { title: t('tasks.taskId'), dataIndex: 'taskId', key: 'taskId', fixed: 'left', searchField: 'taskId' },
  { title: t('tasks.operator'), dataIndex: 'userName', key: 'userName', searchField: 'userName' },
  { title: t('tasks.status'), dataIndex: 'taskStatus', key: 'taskStatus', searchField: 'status' },
  { title: t('tasks.createTime'), dataIndex: 'createdAt', key: 'createdAt', searchField: 'createdAt' },
  { title: t('taskEdit.pageNumber'), dataIndex: 'pageNum', key: 'pageNum', searchField: 'PAGE_NUM' },
  { title: t('taskEdit.workerNumber'), dataIndex: 'no', key: 'no', searchField: 'NO' },
  { title: t('taskEdit.name'), dataIndex: 'name', key: 'name', ellipsis: false, searchField: 'NOM_PRENOM' },
  { title: t('taskEdit.countryField'), dataIndex: 'country', key: 'country', searchField: 'Pays' },
  { title: t('taskEdit.warehouse'), dataIndex: 'warehouse', key: 'warehouse', searchField: 'Entrepot' },
  { title: t('taskEdit.date'), dataIndex: 'date', key: 'date', searchField: 'Date' },
  { title: t('taskEdit.agency'), dataIndex: 'agency', key: 'agency', searchField: 'AGENCE_INTERIMAIRE' },
  { title: t('taskEdit.shift'), dataIndex: 'shift', key: 'shift', searchField: 'HORAIRES_DU_TRAVAIL' },
  { title: t('taskEdit.arrival'), dataIndex: 'arrival', key: 'arrival', searchField: 'ARRIVEE' },
  { title: t('taskEdit.departure'), dataIndex: 'departure', key: 'departure', searchField: 'DEPAR' },
  { title: t('taskEdit.breakTime'), dataIndex: 'pauseMinutes', key: 'pauseMinutes', searchField: 'PAUSE' },
  { title: t('taskEdit.signature'), dataIndex: 'signature', key: 'signature', searchField: 'SIGNATURE' },
  { title: t('taskEdit.observations'), dataIndex: 'observations', key: 'observations', searchField: 'Observations' },
  { title: t('tasks.fileName'), dataIndex: 'fileKey', key: 'fileKey', searchField: 'fileKey' },
  {
    title: t('tasks.imagePreview'),
    dataIndex: 'fileKey',
    key: 'imageUrls',
    autoWidth: false,
    width: 108,
    fixed: 'right',
    sorter: (a, b) => String(a?.fileKey || '').localeCompare(String(b?.fileKey || ''), undefined, { numeric: true }),
  },
])
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })
const displayRecords = computed(() => sortRows(records.value))
const { columns, scrollX } = useAutoSizedColumns(sortedColumns, displayRecords, { defaultMax: 360 })

const buildExportFilters = () => {
  const advancedConditions = searchableFieldDefs
    .map((def) => ({ field: def.field, keyword: (advancedFilters.value[def.field] || '').trim() }))
    .filter((item) => item.keyword)
  const filters = []
  if ((quickKeyword.value || '').trim()) {
    filters.push({ field: '', keyword: quickKeyword.value.trim() })
  }
  filters.push(...advancedConditions)
  return JSON.stringify(filters)
}

const handleExport = async () => {
  exporting.value = true
  try {
    await createEmployeeRecordsExport({
      status: filterStatus.value,
      filters: buildExportFilters(),
    })
    message.success(t('export.queued'))
    await refreshExportSummary()
    openExportCenter()
  } catch (e) {
    console.error(e)
  } finally {
    exporting.value = false
  }
}

const loadRecords = async () => {
  loading.value = true
  try {
    const advancedConditions = searchableFieldDefs
      .map((def) => ({ field: def.field, keyword: (advancedFilters.value[def.field] || '').trim() }))
      .filter((item) => item.keyword)
    const filters = []
    if ((quickKeyword.value || '').trim()) {
      filters.push({ field: '', keyword: quickKeyword.value.trim() })
    }
    filters.push(...advancedConditions)
    const res = await getEmployeeRecordList({
      current: currentPage.value,
      size: pageSize.value,
      status: filterStatus.value,
      filters: JSON.stringify(filters),
    })
    const list = res.data?.records || []
    records.value = list.map((item, idx) => ({
      ...item,
      rowKey: `${item.taskId}-${idx}-${item.no || ''}-${item.name || ''}`,
    }))
    total.value = res.data?.total || 0
  } catch (e) {
    message.error(t('messages.systemError'))
  } finally {
    loading.value = false
  }
}

const toggleAdvancedSearch = () => {
  showAdvancedSearch.value = !showAdvancedSearch.value
}

const getFieldLabel = (field) => {
  const hit = searchableFieldDefs.find((def) => def.field === field)
  return hit ? hit.label : field
}

const handleHeaderPopoverOpen = (field, open) => {
  if (open) {
    activeHeaderFilterField.value = field
    headerFilterKeyword.value = advancedFilters.value[field] || ''
  } else if (activeHeaderFilterField.value === field) {
    activeHeaderFilterField.value = ''
  }
}

const applyHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  advancedFilters.value[activeHeaderFilterField.value] = (headerFilterKeyword.value || '').trim()
  activeHeaderFilterField.value = ''
  handleFilter()
}

const clearHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  headerFilterKeyword.value = ''
  advancedFilters.value[activeHeaderFilterField.value] = ''
  activeHeaderFilterField.value = ''
  handleFilter()
}

const openTask = (taskId) => {
  router.push(`/tasks/${taskId}`)
}

const previewImages = async (record) => {
  const urls = await resolveTaskImageUrls(record.imageUrls, record.fileKey)
  if (!urls.length) {
    message.warning(t('tasks.noImages'))
    return
  }
  previewImagesList.value = urls
  previewCurrentIndex.value = 0
  previewVisible.value = true
}

const getStatusText = (status) => {
  const map = {
    processing: t('tasks.statusProcessing'),
    processed: t('tasks.statusProcessed'),
    confirmed: t('tasks.statusConfirmed'),
    failed: t('tasks.statusFailed'),
    cancelled: t('tasks.statusCancelled'),
  }
  return map[status] || status
}

const getStatusColor = (status) => {
  const map = {
    processing: 'orange',
    processed: 'blue',
    confirmed: 'green',
    failed: 'red',
    cancelled: 'default',
  }
  return map[status] || 'default'
}

const handleFilter = () => {
  currentPage.value = 1
  loadRecords()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  loadRecords()
}

const handleSizeChange = (_, size) => {
  pageSize.value = size
  currentPage.value = 1
  loadRecords()
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.advanced-panel {
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid $border;
  border-radius: $radius-lg;
  background: $primary-lighter;
}

.advanced-title {
  font-weight: $font-weight-semibold;
  margin-bottom: $space-3;
  color: $text-strong;
  letter-spacing: -0.02em;
}

.advanced-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 10px 12px;
}

.advanced-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.advanced-label {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.status-select {
  width: 150px;
}

.search-field-select {
  width: 170px;
}

.search-input {
  width: 260px;
}

@media (max-width: 1440px) {
  .advanced-grid {
    grid-template-columns: repeat(3, minmax(180px, 1fr));
  }
}

@media (max-width: 1080px) {
  .advanced-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 760px) {
  .advanced-grid {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .status-select,
  .search-input {
    width: 100%;
  }
}

.condition-row {
  gap: 8px;
  align-items: center;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

</style>
