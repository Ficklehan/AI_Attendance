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
        <TableColumnSettings
          :columns="configurableColumns"
          :hidden-keys="hiddenKeys"
          :frozen-keys="frozenKeys"
          @update:hidden-keys="setHiddenKeys"
          @update:frozen-keys="setFrozenKeys"
          @show-all="showAllColumns"
          @clear-freeze="clearFrozenKeys"
        />
      </div>
      <div v-if="showAdvancedSearch" class="advanced-panel">
        <div class="advanced-title">{{ $t('tasks.advancedSearch') }}</div>
        <div class="advanced-grid">
          <div v-for="def in searchableFieldDefs" :key="def.field" class="advanced-item">
            <label class="advanced-label">{{ def.label }}</label>
            <FieldFilterControl
              v-model:model-value="advancedFilters[def.field]"
              :filter-type="def.filterType"
              :placeholder="filterPlaceholder(def)"
              :options="getFilterOptions(def)"
              class="advanced-control"
              @submit="handleFilter"
            />
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
                :active="isHeaderFilterActive(column.searchField)"
                @openChange="(open) => handleHeaderPopoverOpen(column, open)"
                @reset="clearHeaderFilter"
                @apply="applyHeaderFilter"
              >
                <template #field>
                  <FieldFilterControl
                    v-model:model-value="headerFilterDraft"
                    :filter-type="column.filterType || 'text'"
                    :placeholder="getFilterPlaceholder(column.searchField)"
                    :options="getHeaderFilterOptions(column)"
                    class="table-header-filter-panel__input"
                    @submit="applyHeaderFilter"
                  />
                </template>
              </TableHeaderFilter>
            </template>
          </TableSortableHeader>
        </template>
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'serialNo'">
            <span class="cell-serial">{{ index + 1 }}</span>
          </template>
          <template v-if="column.key === 'taskStatus'">
            <a-tag :color="getStatusColor(record.taskStatus)">{{ getStatusText(record.taskStatus) }}</a-tag>
          </template>
          <template v-if="column.key === 'taskId'">
            <a-button type="link" size="small" @click="openTask(record.taskId)">{{ record.taskId }}</a-button>
          </template>
          <template v-if="column.key === 'imageUrls'">
            <a-button type="link" size="small" @click="previewImages(record)">{{ $t('tasks.imagePreview') }}</a-button>
          </template>
          <template v-if="column.key === 'signature'">
            <a-tag
              :color="getSignatureMarkColor(getDisplaySignature(record.signature, record))"
              class="signature-mark-tag"
            >
              {{ translateSignatureMark(getDisplaySignature(record.signature, record), t) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'workHours'">
            <span>{{ calculateWorkHours(record) }}</span>
          </template>
          <template v-if="column.key === 'smartMark'">
            <a-space v-if="record.smartMark" wrap size="small" class="mark-tags">
              <a-tag
                v-for="tag in buildRecordMarkTags(record, { getDisplayMark, isAbsentRow, t })"
                :key="tag.key"
                :color="tag.color"
                class="mark-tag"
              >
                {{ tag.label }}
              </a-tag>
            </a-space>
            <span v-else class="cell-muted">-</span>
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
import FieldFilterControl from '@/components/FieldFilterControl.vue'
import { getEmployeeRecordList } from '@/api/task'
import { createEmployeeRecordsExport } from '@/api/export'
import { useExportCenter } from '@/composables/useExportCenter'
import { resolveTaskImageUrls } from '@/utils/imageUrl'
import PageShell from '@/components/PageShell.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useAutoSizedColumns } from '@/composables/useAutoSizedColumns'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import {
  buildEmployeeRecordFieldDefs,
  buildEmptyAdvancedFilters,
  filterPlaceholder as resolveFilterPlaceholder,
  findFieldDef,
} from '@/constants/employeeRecordFields'
import {
  buildFilterConditions,
  emptyFilterValue,
  getFilterOptions as getFilterOptionsUtil,
  isFilterActive,
} from '@/utils/fieldFilterValue'
import {
  buildRecordMarkTags,
  markContains,
  stripSignatureMarksFromSmartMark,
  withInferredNightShiftMark,
  getDisplaySignature,
  translateSignatureMark,
  getSignatureMarkColor,
} from '@/utils/recognitionLabels'
import { calculateWorkHours } from '@/utils/workHours'
import { isAbsentRow } from '@/utils/recordDisplay'
import { compareTableValues } from '@/utils/tableSort'

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
const headerFilterDraft = ref('')
const fieldDefs = computed(() => buildEmployeeRecordFieldDefs(t))
const advancedFilters = ref({})
const previewVisible = ref(false)
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)

const baseColumns = computed(() => [
  {
    title: t('common.serialNumber'),
    key: 'serialNo',
    width: 56,
    autoWidth: false,
    align: 'center',
    sorter: false,
  },
  ...fieldDefs.value.map((def) => ({
    title: def.label,
    dataIndex: def.dataIndex,
    key: def.key,
    ...(def.filterable === false
      ? {}
      : { searchField: def.field, filterType: def.filterType }),
    ellipsis: def.ellipsis,
    align: 'left',
    ...(def.key === 'workHours'
      ? { sorter: (a, b) => compareTableValues(calculateWorkHours(a), calculateWorkHours(b)) }
      : {}),
  })),
  {
    title: t('tasks.imagePreview'),
    dataIndex: 'fileKey',
    key: 'imageUrls',
    autoWidth: false,
    width: 108,
    align: 'center',
    fixed: 'right',
    sorter: (a, b) => String(a?.fileKey || '').localeCompare(String(b?.fileKey || ''), undefined, { numeric: true }),
  },
])
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })
const displayRecords = computed(() => sortRows(records.value))
const { columns: sizedColumns, scrollX } = useAutoSizedColumns(sortedColumns, displayRecords, { defaultMax: 360 })
const {
  frozenColumns: columns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('task-records', sizedColumns, { defaultFrozen: ['serialNo', 'taskId'] })

const searchableFieldDefs = computed(() => {
  const hidden = new Set(hiddenKeys.value)
  return fieldDefs.value.filter((def) => def.filterable !== false && !hidden.has(def.key))
})

const buildQueryFilters = () => {
  const filters = buildFilterConditions(searchableFieldDefs.value, advancedFilters.value)
  if ((quickKeyword.value || '').trim()) {
    filters.unshift({ field: '', keyword: quickKeyword.value.trim(), filterType: 'text' })
  }
  return filters
}

const buildExportFilters = () => JSON.stringify(buildQueryFilters())

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
    const res = await getEmployeeRecordList({
      current: currentPage.value,
      size: pageSize.value,
      status: filterStatus.value,
      filters: JSON.stringify(buildQueryFilters()),
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
  const hit = searchableFieldDefs.value.find((def) => def.field === field)
  return hit ? hit.label : field
}

const getFilterPlaceholder = (field) => {
  const hit = searchableFieldDefs.value.find((def) => def.field === field)
  return hit ? resolveFilterPlaceholder(hit, t) : t('tasks.searchContent')
}

const filterPlaceholder = (def) => resolveFilterPlaceholder(def, t)

const getFilterOptions = (def) => getFilterOptionsUtil(def, t)

const getHeaderFilterOptions = (column) => {
  const def = findFieldDef(fieldDefs.value, column.searchField)
  return getFilterOptions(def || { filterType: column.filterType })
}

const isHeaderFilterActive = (field) => {
  const def = findFieldDef(fieldDefs.value, field)
  if (!def) return false
  return isFilterActive(def.filterType, advancedFilters.value[field])
}

const getDisplayMark = (record) => {
  const raw = stripSignatureMarksFromSmartMark(String(record?.smartMark || record?.SmartMark || '').trim())
  return withInferredNightShiftMark(raw, record)
}

const handleHeaderPopoverOpen = (column, open) => {
  const field = column?.searchField
  if (!field) return
  const def = findFieldDef(fieldDefs.value, field)
  const filterType = def?.filterType || column?.filterType || 'text'
  if (open) {
    activeHeaderFilterField.value = field
    const stored = advancedFilters.value[field]
    if (stored !== undefined && stored !== null && isFilterActive(filterType, stored)) {
      headerFilterDraft.value = Array.isArray(stored)
        ? [...stored]
        : (typeof stored === 'object' ? { ...stored } : stored)
    } else {
      headerFilterDraft.value = emptyFilterValue(filterType)
    }
  } else if (activeHeaderFilterField.value === field) {
    activeHeaderFilterField.value = ''
  }
}

const applyHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  const def = findFieldDef(fieldDefs.value, activeHeaderFilterField.value)
  const filterType = def?.filterType || 'text'
  advancedFilters.value[activeHeaderFilterField.value] = isFilterActive(filterType, headerFilterDraft.value)
    ? headerFilterDraft.value
    : emptyFilterValue(filterType)
  activeHeaderFilterField.value = ''
  handleFilter()
}

const clearHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  const def = findFieldDef(fieldDefs.value, activeHeaderFilterField.value)
  const filterType = def?.filterType || 'text'
  headerFilterDraft.value = emptyFilterValue(filterType)
  advancedFilters.value[activeHeaderFilterField.value] = emptyFilterValue(filterType)
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
  advancedFilters.value = buildEmptyAdvancedFilters(fieldDefs.value)
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

.advanced-control {
  width: 100%;
}

.mark-tags {
  max-width: 280px;
}

.cell-muted {
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
