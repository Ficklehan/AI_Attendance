<template>
  <div class="task-list-container page-inner">
    <PageShell :title="$t('tasks.title')" :subtitle="$t('tasks.subtitle')">
      <template #extra>
        <a-button :loading="exporting" @click="handleExport">
          <DownloadOutlined />
          {{ $t('export.startExport') }}
        </a-button>
        <a-button type="primary" @click="$router.push('/home')">
          <PlusOutlined />
          {{ $t('tasks.createNew') }}
        </a-button>
      </template>
    </PageShell>

    <div v-if="taskSummary" class="task-summary-bar surface-card">
      <div class="task-summary-bar__head">
        <span class="task-summary-bar__title">{{ $t('tasks.summaryTitle') }}</span>
        <a-tag v-if="taskSummary.allUsersScope" color="blue">{{ $t('tasks.summaryAllUsersHint') }}</a-tag>
      </div>
      <div class="task-summary-bar__metrics">
        <button
          type="button"
          class="task-summary-metric"
          :class="{ active: filterStatus === 'processing' }"
          @click="applySummaryFilter('processing')"
        >
          <span class="task-summary-metric__num">{{ taskSummary.processing }}</span>
          <span class="task-summary-metric__label">{{ $t('tasks.summaryProcessing') }}</span>
        </button>
        <button
          type="button"
          class="task-summary-metric highlight"
          :class="{ active: filterStatus === 'processed' }"
          @click="applySummaryFilter('processed')"
        >
          <span class="task-summary-metric__num">{{ taskSummary.review }}</span>
          <span class="task-summary-metric__label">{{ $t('tasks.summaryReview') }}</span>
        </button>
        <button
          type="button"
          class="task-summary-metric"
          :class="{ active: filterStatus === 'confirmed' }"
          @click="applySummaryFilter('confirmed')"
        >
          <span class="task-summary-metric__num">{{ taskSummary.confirmed }}</span>
          <span class="task-summary-metric__label">{{ $t('tasks.summaryConfirmed') }}</span>
        </button>
        <button
          type="button"
          class="task-summary-metric"
          :class="{ active: filterStatus === 'failed' }"
          @click="applySummaryFilter('failed')"
        >
          <span class="task-summary-metric__num">{{ taskSummary.failed }}</span>
          <span class="task-summary-metric__label">{{ $t('tasks.summaryFailed') }}</span>
        </button>
      </div>
      <a-button
        v-if="taskSummary.review > 0"
        type="primary"
        class="task-summary-review-btn"
        @click="goFirstReviewTask"
      >
        {{ $t('tasks.goReview', { count: taskSummary.review }) }}
      </a-button>
    </div>

    <a-card class="task-card surface-card" :bordered="false">
      <div class="filter-bar">
        <a-select 
          v-model:value="filterStatus" 
          :placeholder="$t('tasks.filterStatus')" 
          allow-clear 
          class="status-select"
          @change="handleFilter"
        >
          <a-select-option value="">{{ $t('tasks.allStatus') }}</a-select-option>
          <a-select-option value="processing">{{ $t('tasks.statusProcessing') }}</a-select-option>
          <a-select-option value="processed">{{ $t('tasks.statusProcessed') }}</a-select-option>
          <a-select-option value="confirmed">{{ $t('tasks.statusConfirmed') }}</a-select-option>
          <a-select-option value="failed">{{ $t('tasks.statusFailed') }}</a-select-option>
          <a-select-option value="cancelled">{{ $t('tasks.statusCancelled') }}</a-select-option>
        </a-select>

        <a-input
          v-model:value="keyword"
          :placeholder="$t('tasks.searchContent')"
          allow-clear
          class="search-input"
          @clear="handleFilter"
          @keyup.enter="handleFilter"
          :prefix-icon="SearchOutlined"
        />
        <a-tag v-if="searchFieldLabel" color="blue" closable @close="clearSearchField">
          {{ searchFieldLabel }}
        </a-tag>
        <a-button
          v-if="selectedRowKeys.length > 0"
          danger
          :loading="batchDeleting"
          class="batch-delete-btn"
          @click="handleBatchDelete"
        >
          <DeleteOutlined />
          {{ $t('tasks.batchDelete', { count: selectedRowKeys.length }) }}
        </a-button>
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
      
      <a-table 
        :columns="columns" 
        :data-source="displayTasks" 
        :loading="loading" 
        :pagination="false"
        :scroll="{ x: taskListScrollX }"
        :row-key="(record) => record.taskId"
        :row-selection="rowSelection"
        class="task-table rich-table-header"
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
                :title="column.title"
                :open="activeHeaderFilterField === column.searchField"
                :active="isHeaderFilterActive(column)"
                @openChange="(open) => handleHeaderPopoverOpen(column, open)"
                @reset="clearHeaderFilter"
                @apply="applyHeaderFilter"
              >
                <template #field>
                  <FieldFilterControl
                    v-model:model-value="headerFilterDraft"
                    :filter-type="column.filterType || 'text'"
                    :placeholder="getHeaderFilterPlaceholder(column)"
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
            <span class="cell-serial">{{ (currentPage - 1) * pageSize + index + 1 }}</span>
          </template>
          <template v-if="column.key === 'taskId'">
            <a-button type="link" size="small" class="task-id-link" @click="handleView(record)">
              {{ record.taskId }}
            </a-button>
          </template>
          <template v-if="column.key === 'status'">
            <div class="status-cell">
              <a-tag :color="getStatusColor(record.status)" class="status-tag">
                {{ getStatusText(record.status) }}
                <span v-if="record.status === 'processing' && record.progressRowCount">
                  · {{ record.progressRowCount }}
                </span>
              </a-tag>
              <a-tag
                v-if="record.status === 'confirmed' && record.syncStatus && record.syncStatus !== 'none'"
                :color="getSyncStatusColor(record.syncStatus)"
                class="sync-tag"
              >
                {{ getSyncStatusText(record.syncStatus) }}
              </a-tag>
            </div>
          </template>
          <template v-if="column.key === 'fileKey'">
            <div class="file-cell" @click="previewImages(record)">
              <FileImageOutlined class="file-icon" />
              <span class="file-name">{{ record.fileKey }}</span>
              <span v-if="getImageCount(record) > 1" class="image-count">({{ getImageCount(record) }} {{ $t('tasks.images') }})</span>
              <EyeOutlined class="preview-icon" />
            </div>
          </template>
          <template v-if="column.key === 'action'">
            <div class="action-buttons">
              <a-button type="text" size="small" @click="handleView(record)" class="view-btn">
                <EyeOutlined />
              </a-button>
              <a-button
                v-if="record.status !== 'confirmed'"
                type="text"
                danger
                size="small"
                @click="handleDelete(record)"
                class="delete-btn"
              >
                <DeleteOutlined />
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
      
      <div class="pagination-wrapper">
        <a-pagination
          v-model:current="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-size-options="[10, 20, 50, 100]"
          show-size-changer
          :show-total="(total) => $t('tasks.totalRecords', { total })"
          class="pagination"
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
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import { 
  PlusOutlined, 
  SearchOutlined, 
  UnorderedListOutlined,
  EyeOutlined,
  DeleteOutlined,
  FileImageOutlined,
  DownloadOutlined,
} from '@ant-design/icons-vue'
import { getTaskList, getTaskSummary, deleteTask } from '@/api/task'
import { createTaskListExport } from '@/api/export'
import { useExportCenter } from '@/composables/useExportCenter'
import { resolveTaskImageUrls } from '@/utils/imageUrl'
import PageShell from '@/components/PageShell.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import TableHeaderFilter from '@/components/TableHeaderFilter.vue'
import FieldFilterControl from '@/components/FieldFilterControl.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import { sumTableScrollX } from '@/utils/tableAutoColumns'
import {
  FILTER_TYPES,
  buildTaskStatusOptions,
  emptyFilterValue,
  getFilterOptions,
  isFilterActive,
  parseFilterValue,
  serializeFilterValue,
} from '@/utils/fieldFilterValue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const { openExportCenter, refreshExportSummary } = useExportCenter()

const tasks = ref([])
const taskSummary = ref(null)
const exporting = ref(false)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterStatus = ref('')
const keyword = ref('')
const searchField = ref('')
const activeHeaderFilterField = ref('')
const activeHeaderFilterColumn = ref(null)
const headerFilterDraft = ref('')
const previewVisible = ref(false)
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)
const selectedRowKeys = ref([])
const batchDeleting = ref(false)

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys) => {
    selectedRowKeys.value = keys
  },
  getCheckboxProps: (record) => ({
    disabled: record.status === 'confirmed',
  }),
}))

const baseColumns = computed(() => [
  {
    title: t('common.serialNumber'),
    key: 'serialNo',
    width: 56,
    autoWidth: false,
    align: 'center',
    sorter: false,
  },
  { title: t('tasks.taskId'), dataIndex: 'taskId', key: 'taskId', width: 150, ellipsis: true, align: 'left', searchField: 'taskId' },
  { title: t('tasks.fileName'), dataIndex: 'fileKey', key: 'fileKey', width: 260, ellipsis: true, align: 'left', searchField: 'fileKey' },
  { title: t('tasks.operator'), dataIndex: 'userName', key: 'userName', width: 110, ellipsis: true, align: 'left', searchField: 'userName' },
  { title: t('tasks.status'), dataIndex: 'status', key: 'status', width: 130, align: 'left', searchField: 'status', filterType: FILTER_TYPES.STATUS },
  { title: t('tasks.createTime'), dataIndex: 'createdAt', key: 'createdAt', width: 170, align: 'left', searchField: 'createdAt', filterType: FILTER_TYPES.DATETIME },
  { title: t('tasks.operation'), key: 'action', width: 100, align: 'center', fixed: 'right' },
])
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, {
  skipKeys: ['serialNo', 'action'],
  customHeader: true,
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
} = useColumnFreeze('task-list', sortedColumns, { defaultFrozen: ['serialNo', 'taskId'] })
const taskListScrollX = computed(() => sumTableScrollX(columns.value))
const displayTasks = computed(() => sortRows(tasks.value))

const searchFieldLabel = computed(() => {
  if (!searchField.value) return ''
  const dict = {
    taskId: t('tasks.taskId'),
    fileKey: t('tasks.fileName'),
    userName: t('tasks.operator'),
    createdAt: t('tasks.createTime'),
  }
  return dict[searchField.value] || ''
})

const getHeaderFilterPlaceholder = (column) => {
  if (column.filterType === FILTER_TYPES.STATUS) return t('tasks.filterStatus')
  if (column.filterType === FILTER_TYPES.DATETIME) return t('tasks.filterDateTimeRange')
  return t('tasks.searchContent')
}

const getHeaderFilterOptions = (column) => {
  if (column.filterType === FILTER_TYPES.STATUS) return buildTaskStatusOptions(t)
  return getFilterOptions(column, t)
}

const isHeaderFilterActive = (column) => {
  if (column.filterType === FILTER_TYPES.STATUS) return !!filterStatus.value
  if (searchField.value !== column.searchField) return false
  const filterType = column.filterType || FILTER_TYPES.TEXT
  return isFilterActive(filterType, parseFilterValue(filterType, keyword.value))
}

const handleExport = async () => {
  exporting.value = true
  try {
    await createTaskListExport({
      status: filterStatus.value,
      keyword: keyword.value,
      searchField: searchField.value,
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

const loadTaskSummary = async () => {
  try {
    const response = await getTaskSummary()
    taskSummary.value = response.data || null
  } catch (error) {
    console.error('加载任务汇总失败:', error)
  }
}

let processingPollTimer = null

const hasProcessingTasks = computed(() => tasks.value.some((task) => task.status === 'processing'))

const stopProcessingPoll = () => {
  if (processingPollTimer) {
    clearInterval(processingPollTimer)
    processingPollTimer = null
  }
}

const startProcessingPoll = () => {
  if (processingPollTimer) return
  processingPollTimer = setInterval(() => {
    if (hasProcessingTasks.value) {
      loadTasks({ silent: true })
      loadTaskSummary()
    } else {
      stopProcessingPoll()
    }
  }, 5000)
}

const syncProcessingPoll = () => {
  if (hasProcessingTasks.value) {
    startProcessingPoll()
  } else {
    stopProcessingPoll()
  }
}

const loadTasks = async (options = {}) => {
  const { silent = false } = options
  if (!silent) loading.value = true
  try {
    const [listRes] = await Promise.all([
      getTaskList({
        current: currentPage.value,
        size: pageSize.value,
        status: filterStatus.value,
        keyword: keyword.value,
        searchField: searchField.value,
      }),
      loadTaskSummary(),
    ])
    tasks.value = listRes.data.records || []
    total.value = listRes.data.total || 0
    syncProcessingPoll()
  } catch (error) {
    if (!silent) {
      console.error('加载任务列表失败:', error)
    }
  } finally {
    if (!silent) loading.value = false
  }
}

const applySummaryFilter = (status) => {
  filterStatus.value = status
  currentPage.value = 1
  loadTasks()
}

const goFirstReviewTask = async () => {
  filterStatus.value = 'processed'
  currentPage.value = 1
  loading.value = true
  try {
    const response = await getTaskList({
      current: 1,
      size: 1,
      status: 'processed',
      keyword: keyword.value,
      searchField: searchField.value,
    })
    const first = (response.data.records || [])[0]
    if (first?.taskId) {
      router.push(`/tasks/${first.taskId}`)
      return
    }
    message.info(t('tasks.emptyTitle'))
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const getImageCount = (record) => {
  if (!record.imageUrls) return 0
  try {
    const urls = typeof record.imageUrls === 'string' ? JSON.parse(record.imageUrls) : record.imageUrls
    return urls.length || 1
  } catch {
    return 1
  }
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

const handleBatchDelete = () => {
  const keys = selectedRowKeys.value.filter((id) => {
    const row = tasks.value.find((item) => item.taskId === id)
    return row && row.status !== 'confirmed'
  })
  if (!keys.length) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  Modal.confirm({
    title: t('tasks.batchDelete', { count: keys.length }),
    content: t('tasks.batchDeleteConfirm', { count: keys.length }),
    okText: t('common.delete'),
    cancelText: t('common.cancel'),
    okType: 'danger',
    onOk: async () => {
      batchDeleting.value = true
      try {
        await Promise.all(keys.map((id) => deleteTask(id)))
        message.success(t('tasks.batchDeleteSuccess', { count: keys.length }))
        selectedRowKeys.value = []
        loadTasks()
      } catch (error) {
        message.error(t('messages.systemError'))
        console.error(error)
      } finally {
        batchDeleting.value = false
      }
    },
  })
}

const handleFilter = () => {
  currentPage.value = 1
  loadTasks()
}

const clearSearchField = () => {
  searchField.value = ''
  handleFilter()
}

const handleHeaderPopoverOpen = (column, open) => {
  const field = column.searchField
  const filterType = column.filterType || FILTER_TYPES.TEXT
  if (open) {
    activeHeaderFilterField.value = field
    activeHeaderFilterColumn.value = column
    if (column.filterType === FILTER_TYPES.STATUS) {
      headerFilterDraft.value = filterStatus.value || ''
    } else if (searchField.value === field && keyword.value) {
      headerFilterDraft.value = parseFilterValue(filterType, keyword.value)
    } else {
      headerFilterDraft.value = emptyFilterValue(filterType)
    }
  } else if (activeHeaderFilterField.value === field) {
    activeHeaderFilterField.value = ''
    activeHeaderFilterColumn.value = null
  }
}

const applyHeaderFilter = () => {
  const column = activeHeaderFilterColumn.value
  if (!column?.searchField) return
  const filterType = column.filterType || FILTER_TYPES.TEXT
  if (column.filterType === FILTER_TYPES.STATUS) {
    filterStatus.value = String(headerFilterDraft.value || '').trim()
    activeHeaderFilterField.value = ''
    activeHeaderFilterColumn.value = null
    handleFilter()
    return
  }
  searchField.value = column.searchField
  keyword.value = serializeFilterValue(filterType, headerFilterDraft.value)
  activeHeaderFilterField.value = ''
  activeHeaderFilterColumn.value = null
  handleFilter()
}

const clearHeaderFilter = () => {
  const column = activeHeaderFilterColumn.value
  if (!column?.searchField) return
  const filterType = column.filterType || FILTER_TYPES.TEXT
  if (column.filterType === FILTER_TYPES.STATUS) {
    filterStatus.value = ''
  } else if (searchField.value === column.searchField) {
    searchField.value = ''
    keyword.value = ''
  }
  headerFilterDraft.value = emptyFilterValue(filterType)
  activeHeaderFilterField.value = ''
  activeHeaderFilterColumn.value = null
  handleFilter()
}

const handleSizeChange = (_current, size) => {
  pageSize.value = size
  currentPage.value = 1
  loadTasks()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadTasks()
}

const handleView = (record) => {
  router.push(`/tasks/${record.taskId}`)
}

const handleDelete = async (record) => {
  if (record.status === 'confirmed') {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  try {
    await Modal.confirm({
      title: t('common.delete'),
      content: t('tasks.deleteConfirm'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        await deleteTask(record.taskId)
        message.success(t('tasks.deleteSuccess'))
        selectedRowKeys.value = selectedRowKeys.value.filter((id) => id !== record.taskId)
        loadTasks()
      },
    })
  } catch (error) {
    console.error(t('messages.systemError'), error)
  }
}

const getStatusColor = (status) => {
  const colorMap = {
    processing: 'orange',
    processed: 'blue',
    confirmed: 'green',
    failed: 'red',
    cancelled: 'default',
  }
  return colorMap[status] || 'default'
}

const getStatusText = (status) => {
  const textMap = {
    processing: t('tasks.statusProcessing'),
    processed: t('tasks.statusProcessed'),
    confirmed: t('tasks.statusConfirmed'),
    failed: t('tasks.statusFailed'),
    cancelled: t('tasks.statusCancelled'),
  }
  return textMap[status] || status
}

const getSyncStatusColor = (syncStatus) => {
  const map = {
    pending: 'processing',
    synced: 'success',
    sync_failed: 'error',
  }
  return map[syncStatus] || 'default'
}

const getSyncStatusText = (syncStatus) => {
  const map = {
    pending: t('taskEdit.syncPending'),
    synced: t('taskEdit.syncSynced'),
    sync_failed: t('taskEdit.syncFailed'),
  }
  return map[syncStatus] || syncStatus
}

const applyRouteQuery = () => {
  const status = typeof route.query.status === 'string' ? route.query.status : ''
  if (status) {
    filterStatus.value = status
    currentPage.value = 1
  }
}

onMounted(() => {
  applyRouteQuery()
  loadTasks()
})

onBeforeUnmount(() => {
  stopProcessingPoll()
})

watch(() => route.query, () => {
  if (route.path === '/tasks') {
    applyRouteQuery()
    loadTasks()
  }
}, { deep: true })

watch(() => route.path, (newPath, oldPath) => {
  // 当路径变化时，如果从详情页返回任务列表，重新加载数据
  if (newPath === '/tasks' && oldPath?.startsWith('/tasks/')) {
    applyRouteQuery()
    loadTasks()
  }
})
</script>

<style lang="scss" scoped>
.task-list-container {
  .task-summary-bar {
    margin-bottom: $space-4;
    padding: $space-4 $space-5;
    border-radius: $radius-xl;
    border: 1px solid rgba($border, 0.6);
  }

  .task-summary-bar__head {
    display: flex;
    align-items: center;
    gap: $space-3;
    margin-bottom: $space-4;
  }

  .task-summary-bar__title {
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  .task-summary-bar__metrics {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: $space-3;
    margin-bottom: $space-4;

    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  .task-summary-metric {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: $space-3;
    border-radius: $radius-lg;
    border: 1px solid transparent;
    background: $bg-muted;
    cursor: pointer;
    transition: border-color $duration-base $ease-smooth, background $duration-base $ease-smooth;

    &:hover,
    &.active {
      border-color: rgba($primary, 0.35);
      background: $primary-light;
    }

    &.highlight .task-summary-metric__num {
      color: $primary;
    }
  }

  .task-summary-metric__num {
    font-size: $font-size-2xl;
    font-weight: $font-weight-bold;
    font-variant-numeric: tabular-nums;
    color: $text-strong;
  }

  .task-summary-metric__label {
    font-size: $font-size-xs;
    color: $text-tertiary;
  }

  .task-summary-review-btn {
    width: 100%;
  }

  .task-card {
    border-radius: $radius-xl;
    border: none;
    box-shadow: $shadow-card;
    overflow: visible;
    
    .filter-bar {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 0 0 16px;
      flex-wrap: wrap;
      
      .status-select {
        width: 140px;
        
        :deep(.ant-select-selector) {
          border-radius: $radius-md;
          border-color: $border;
        }
      }
      
      .search-input {
        width: 260px;
        
        :deep(.ant-input) {
          border-radius: $radius-md;
          border-color: $border;
        }
      }

      .batch-delete-btn {
        margin-left: auto;
      }
    }
    
    .task-table {
      padding: 0 24px;
      overflow: visible;
      
      :deep(.ant-table) {
        border-radius: $radius-lg;
        overflow: hidden;
      }
      
      
      :deep(.ant-table-tbody > tr) {
        transition: all $duration-base $ease-smooth;
        
        &:hover {
          background: $bg-muted;
        }
      }
      
      :deep(.ant-table-tbody > tr > td) {
        color: $text-strong;
        border-bottom: 1px solid $bg-muted;
      }
      
      .task-id-link {
        padding: 0;
        height: auto;
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 13px;
        font-weight: $font-weight-medium;
      }

      .status-cell {
        gap: 6px;
      }

      .status-tag,
      .sync-tag {
        border-radius: $radius-sm;
        font-size: $font-size-sm;
        font-weight: $font-weight-medium;
        padding: 2px 10px;
        margin: 0;
      }
      
      .action-buttons {
        gap: 8px;

        .view-btn {
          color: $primary;
          
          &:hover {
            color: $primary;
            background: rgba($primary, 0.1);
            border-radius: $radius-sm;
          }
        }
        
        .delete-btn {
          color: $danger;
          
          &:hover {
            color: $danger;
            background: rgba($danger, 0.1);
            border-radius: $radius-sm;
          }
        }
      }

    }
    
    .pagination-wrapper {
      padding: 20px 24px;
      border-top: 1px solid $border;
      
      .pagination {
        display: flex;
        justify-content: flex-end;
        
        :deep(.ant-pagination-item-active) {
          background: $primary;
          border-color: $primary;
        }
      }
    }
    
    .search-field-select {
      width: 120px;
      
      :deep(.ant-select-selector) {
        border-radius: $radius-md;
        border-color: $border;
      }
    }
    
    .file-cell {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      color: $primary;
      
      &:hover {
        color: $primary;
        text-decoration: underline;
      }
      
      .file-icon {
        font-size: $font-size-md;
      }
      
      .file-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      
      .image-count {
        font-size: $font-size-sm;
        color: $text-secondary;
      }
      
      .preview-icon {
        font-size: $font-size-sm;
        opacity: 0.7;
      }
    }
  }

}
</style>
