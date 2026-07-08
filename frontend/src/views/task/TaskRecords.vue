<template>
  <div class="task-records-container page-inner">
    <div class="image-compare-layout" :class="{ 'image-compare-layout--dock-open': previewDockOpen }">
      <div class="image-compare-layout__main">
    <PageShell :title="$t('tasks.recordTitle')" :subtitle="$t('tasks.recordSubtitle')">
      <template #extra>
        <a-button :loading="exporting" @click="openExportModal">
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

      <div ref="recordsTableAnchor" class="table-body-scroll-anchor">
      <a-table
        class="rich-table-header task-records-table"
        :columns="columns"
        :data-source="displayRecords"
        :loading="loading"
        :pagination="false"
        row-key="rowKey"
        :scroll="tableScroll"
      >
        <template #headerCell="{ column }">
          <TableSortableHeader
            :column="column"
            :title="column.title"
            compact
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
        <template #bodyCell="{ column, record, index, text }">
          <template v-if="column.key === 'serialNo'">
            <CopyableCell :text="String(index + 1)" />
          </template>
          <template v-else-if="column.key === 'taskStatus'">
            <CopyableCell :text="getStatusText(record.taskStatus)">
              <a-tag :color="getStatusColor(record.taskStatus)">{{ getStatusText(record.taskStatus) }}</a-tag>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'taskId'">
            <CopyableCell :text="record.taskId">
              <a-button type="link" size="small" @click="openTask(record.taskId)">{{ record.taskId }}</a-button>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'imageUrls'">
            <a-button type="link" size="small" @click="previewTaskImages(record)">{{ $t('tasks.imagePreview') }}</a-button>
          </template>
          <template v-else-if="column.key === 'signature'">
            <CopyableCell :text="translateSignatureMark(getDisplaySignature(record.signature, record), t)">
              <a-tag
                :color="getSignatureMarkColor(getDisplaySignature(record.signature, record))"
                class="signature-mark-tag"
              >
                {{ translateSignatureMark(getDisplaySignature(record.signature, record), t) }}
              </a-tag>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'pageNum'">
            <CopyableCell :text="record.pageNum || '-'" />
          </template>
          <template v-else-if="column.key === 'no'">
            <CopyableCell :text="record.no || '-'" />
          </template>
          <template v-else-if="column.key === 'workHours'">
            <CopyableCell :text="record.workHours || calculateWorkHours(record)" />
          </template>
          <template v-else-if="column.key === 'anomalyDescription'">
            <CopyableCell :text="record.anomalyDescription || '-'" />
          </template>
          <template v-else-if="column.key === 'smartMark'">
            <CopyableCell
              v-if="record.smartMark"
              :text="buildRecordMarkTags(record, { getDisplayMark, isAbsentRow, t }).map((tag) => tag.label).join(', ')"
            >
              <a-space wrap size="small" class="mark-tags">
                <a-tag
                  v-for="tag in buildRecordMarkTags(record, { getDisplayMark, isAbsentRow, t })"
                  :key="tag.key"
                  :color="tag.color"
                  class="mark-tag"
                >
                  {{ tag.label }}
                </a-tag>
              </a-space>
            </CopyableCell>
            <span v-else class="cell-muted">-</span>
          </template>
          <template v-else-if="column.key === 'country'">
            <CopyableCell :text="displayCountryField(record)">
              {{ displayCountryField(record) || '-' }}
            </CopyableCell>
          </template>
          <template v-else-if="isCopyableTableColumn(column)">
            <CopyableCell :text="resolveTableCellCopyText(column, record, text)" />
          </template>
        </template>
      </a-table>
      </div>

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
      </div>

      <ImageCompareDockShell
        v-if="previewDockOpen"
        v-model:open="previewDockOpen"
        v-model:index="previewCurrentIndex"
        :images="previewImagesList"
        :loading="previewFetching"
        :title="$t('tasks.imagePreview')"
        @fullscreen="openPreviewFullscreen"
      />
    </div>

    <ImagePreviewModal
      v-model:open="previewFullscreenOpen"
      v-model:index="previewCurrentIndex"
      :images="previewImagesList"
      :title="$t('tasks.imagePreview')"
    />

    <a-modal
      v-model:open="exportModalOpen"
      :title="$t('export.optionsTitle')"
      :confirm-loading="exporting"
      :ok-text="$t('export.startExport')"
      :cancel-text="$t('common.cancel')"
      @ok="confirmExport"
    >
      <p class="export-options-desc">{{ $t('export.imageLinksHint') }}</p>
      <a-checkbox v-model:checked="exportIncludeThumbnails">
        {{ $t('export.includeThumbnails') }}
      </a-checkbox>
      <p v-if="exportIncludeThumbnails" class="export-options-warn">
        {{ $t('export.includeThumbnailsHint') }}
      </p>
      <a-checkbox
        v-if="exportIncludeThumbnails"
        v-model:checked="exportEmbedFullResolution"
        class="export-options-sub"
      >
        {{ $t('export.embedFullResolution') }}
      </a-checkbox>
      <p v-if="exportIncludeThumbnails && exportEmbedFullResolution" class="export-options-warn">
        {{ $t('export.embedFullResolutionHint') }}
      </p>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'

import TableHeaderFilter from '@/components/TableHeaderFilter.vue'
import FieldFilterControl from '@/components/FieldFilterControl.vue'
import { getEmployeeRecordList } from '@/api/task'
import { createEmployeeRecordsExport } from '@/api/export'
import { useExportCenter } from '@/composables/useExportCenter'
import { warmupOcrWorker } from '@/utils/imageAutoOrient'
import { useTaskImagePreview } from '@/composables/useTaskImagePreview'
import PageShell from '@/components/PageShell.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import ImageCompareDockShell from '@/components/ImageCompareDockShell.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import CopyableCell from '@/components/CopyableCell.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useAutoSizedColumns } from '@/composables/useAutoSizedColumns'
import { useTableBodyScrollY } from '@/composables/useTableBodyScrollY'
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
import { isCopyableTableColumn, resolveTableCellCopyText } from '@/utils/tableCopy'
import { formatPaysFieldDisplay } from '@/utils/countryLabels'
import { useCountryStore } from '@/stores/country'
import { withColumnDensity } from '@/utils/tableColumnDensity'

const { t } = useI18n()
const router = useRouter()
const countryStore = useCountryStore()
const { openExportCenter, refreshExportSummary } = useExportCenter()

const displayCountryField = (record) => {
  const raw = record?.country
  if (raw == null || raw === '') return ''
  return formatPaysFieldDisplay(raw, countryStore.options)
}

const loading = ref(false)
const exporting = ref(false)
const exportModalOpen = ref(false)
const exportIncludeThumbnails = ref(false)
const exportEmbedFullResolution = ref(false)
const records = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const quickKeyword = ref('')
const showAdvancedSearch = ref(false)
const recordsTableAnchor = ref(null)
const activeHeaderFilterField = ref('')
const headerFilterDraft = ref('')
const fieldDefs = computed(() => buildEmployeeRecordFieldDefs(t))
const advancedFilters = ref({})
const {
  previewImagesList,
  previewCurrentIndex,
  previewFetching,
  previewDockOpen,
  previewFullscreenOpen,
  openPreviewFullscreen,
  previewTaskImages,
} = useTaskImagePreview()

const baseColumns = computed(() => [
  {
    title: t('common.serialNumber'),
    key: 'serialNo',
    width: 56,
    autoWidth: false,
    align: 'center',
    sorter: false,
  },
  ...fieldDefs.value.map((def) => withColumnDensity({
    title: def.label,
    dataIndex: def.dataIndex,
    key: def.key,
    density: def.density,
    ...(def.filterable === false
      ? {}
      : { searchField: def.field, filterType: def.filterType }),
    ellipsis: def.ellipsis,
    align: 'left',
    ...(def.key === 'workHours'
      ? {
          sorter: (a, b) => compareTableValues(
            a.workHours || calculateWorkHours(a),
            b.workHours || calculateWorkHours(b),
          ),
        }
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
const { tableScroll, measure: measureRecordsTableScroll } = useTableBodyScrollY(
  recordsTableAnchor,
  scrollX,
  {
    enabled: computed(() => displayRecords.value.length > 0),
    reservedBottom: 56,
  },
)

watch(showAdvancedSearch, () => {
  measureRecordsTableScroll()
})
const {
  frozenColumns: columns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('task-records', sizedColumns, { defaultFrozen: ['serialNo', 'taskId', 'pageNum', 'no'] })

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

const openExportModal = () => {
  exportIncludeThumbnails.value = false
  exportEmbedFullResolution.value = false
  exportModalOpen.value = true
}

const confirmExport = async () => {
  exporting.value = true
  try {
    await createEmployeeRecordsExport({
      status: filterStatus.value,
      filters: buildExportFilters(),
      includeThumbnails: exportIncludeThumbnails.value,
      embedFullResolution: exportIncludeThumbnails.value && exportEmbedFullResolution.value,
    })
    message.success(t('export.queued'))
    exportModalOpen.value = false
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

const countryFilterOptions = computed(() =>
  (countryStore.selectOptions || []).filter((item) => item.value && item.value !== 'default'),
)

const getFilterOptions = (def) => getFilterOptionsUtil(def, t, countryFilterOptions.value)

const getHeaderFilterOptions = (column) => {
  const def = findFieldDef(fieldDefs.value, column.searchField)
  return getFilterOptions(def || { filterType: column.filterType, isCountry: column.searchField === 'Pays' })
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
  warmupOcrWorker()
  countryStore.hydrate()
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

.task-records-table {
  :deep(.ant-table-thead > tr > th) {
    font-size: 11px;
    vertical-align: middle !important;
    text-align: left !important;
    padding: 6px 8px !important;
  }

  :deep(.ant-table-thead > tr > th .ant-table-column-sorters),
  :deep(.ant-table-thead > tr > th .ant-table-column-title) {
    min-height: 30px;
    align-items: center;
    justify-content: flex-start;
  }

  :deep(.ant-table-thead > tr > th.ant-table-cell-align-center),
  :deep(.ant-table-thead > tr > th.ant-table-cell-align-right) {
    text-align: left !important;
  }

  :deep(.col-density-compact) {
    padding: 4px 6px !important;
    font-size: 12px;
    line-height: 1.25;
    font-variant-numeric: tabular-nums;
  }

  :deep(th.col-density-compact) {
    overflow: visible !important;
    padding: 5px 6px !important;
    vertical-align: middle !important;

    .ant-table-column-sorters,
    .ant-table-column-title {
      min-height: 30px;
      overflow: visible;
    }
  }

  :deep(td.col-density-compact) {
    overflow: visible;

    .copyable-cell {
      width: 100%;
      max-width: 100%;
    }

    .copyable-cell__content {
      overflow: visible;
      text-overflow: clip;
      white-space: nowrap;
      max-width: none;
    }

    .copyable-cell__btn {
      width: 18px;
      height: 18px;
      font-size: 11px;
    }
  }

  :deep(td.col-density-compact.ant-table-cell-align-center) {
    .copyable-cell {
      justify-content: center;
    }
  }
}

.export-options-desc {
  margin-bottom: 12px;
  color: rgba(0, 0, 0, 0.65);
  line-height: 1.5;
}

.export-options-warn {
  margin-top: 10px;
  margin-bottom: 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  line-height: 1.5;
}

.export-options-sub {
  margin-top: 10px;
  margin-left: 24px;
}

</style>
