<template>
  <div class="task-list-container page-inner">
    <div class="image-compare-layout" :class="{ 'image-compare-layout--dock-open': previewDockOpen }">
      <div class="image-compare-layout__main">
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

      <a-alert
        v-if="loadError"
        type="error"
        show-icon
        class="task-load-error"
        :message="$t('tasks.loadError')"
        :description="loadError"
      >
        <template #action>
          <a-button size="small" @click="loadTasks()">{{ $t('common.retry') }}</a-button>
        </template>
      </a-alert>
      
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
        <template #bodyCell="{ column, record, index, text }">
          <template v-if="column.key === 'serialNo'">
            <CopyableCell :text="String((currentPage - 1) * pageSize + index + 1)" />
          </template>
          <template v-else-if="column.key === 'taskId'">
            <CopyableCell :text="record.taskId">
              <a-button type="link" size="small" class="task-id-link" @click="handleView(record)">
                {{ record.taskId }}
              </a-button>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'status'">
            <CopyableCell :text="getStatusCopyText(record)">
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
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'fileKey'">
            <CopyableCell :text="record.fileKey" block>
              <div class="file-cell">
                <FileImageOutlined class="file-icon file-cell__preview" @click="previewTaskImages(record)" />
                <span class="file-name">{{ record.fileKey }}</span>
                <span v-if="getImageCount(record) > 1" class="image-count">({{ getImageCount(record) }} {{ $t('tasks.images') }})</span>
                <EyeOutlined class="preview-icon file-cell__preview" @click="previewTaskImages(record)" />
              </div>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="table-action-cell table-action-cell--icons table-action-cell--icons-2">
              <span class="table-action-cell__slot">
                <a-button type="text" size="small" @click="handleView(record)" class="view-btn">
                  <EyeOutlined />
                </a-button>
              </span>
              <span class="table-action-cell__slot">
                <a-button
                  v-if="canDeleteRecord(record)"
                  type="text"
                  danger
                  size="small"
                  @click="handleDelete(record)"
                  class="delete-btn"
                >
                  <DeleteOutlined />
                </a-button>
              </span>
            </div>
          </template>
          <template v-else-if="isCopyableTableColumn(column)">
            <CopyableCell :text="resolveTableCellCopyText(column, record, text)" />
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

    <TaskDeleteModal
      v-model:open="deleteModalOpen"
      :task-id="deleteModalTaskId"
      :confirmed="deleteModalConfirmed"
      :batch-count="deleteModalBatchCount"
      :submitting="deleteSubmitting"
      @submit="handleDeleteSubmit"
    />
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
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
import { useTaskImagePreview } from '@/composables/useTaskImagePreview'
import { warmupOcrWorker } from '@/utils/imageAutoOrient'
import PageShell from '@/components/PageShell.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import ImageCompareDockShell from '@/components/ImageCompareDockShell.vue'
import TaskDeleteModal from '@/components/TaskDeleteModal.vue'
import CopyableCell from '@/components/CopyableCell.vue'
import { useAuthStore } from '@/stores/auth'
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
import { isCopyableTableColumn, resolveTableCellCopyText } from '@/utils/tableCopy'

const BATCH_SELECTABLE_STATUSES = new Set(['processed', 'failed'])

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const authStore = useAuthStore()
const { openExportCenter, refreshExportSummary } = useExportCenter()

const tasks = ref([])
const taskSummary = ref(null)
const exporting = ref(false)
const loading = ref(false)
const loadError = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterStatus = ref('')
const keyword = ref('')
const searchField = ref('')
const activeHeaderFilterField = ref('')
const activeHeaderFilterColumn = ref(null)
const headerFilterDraft = ref('')
const {
  previewImagesList,
  previewCurrentIndex,
  previewFetching,
  previewDockOpen,
  previewFullscreenOpen,
  openPreviewFullscreen,
  previewTaskImages,
} = useTaskImagePreview()
const selectedRowKeys = ref([])
const batchDeleting = ref(false)
const deleteModalOpen = ref(false)
const deleteModalTaskId = ref('')
const deleteModalConfirmed = ref(false)
const deleteModalBatchCount = ref(1)
const deleteSubmitting = ref(false)
const pendingDeleteTaskIds = ref([])

const canDeleteConfirmedTask = computed(() => authStore.canDeleteConfirmedTask)

const canDeleteRecord = (record) => {
  if (!record) return false
  if (record.status === 'confirmed') {
    return canDeleteConfirmedTask.value
  }
  return true
}

const isBatchSelectable = (record) => record && BATCH_SELECTABLE_STATUSES.has(record.status)

const pruneSelectedRowKeys = () => {
  selectedRowKeys.value = selectedRowKeys.value.filter((id) => {
    const row = tasks.value.find((item) => item.taskId === id)
    return row && isBatchSelectable(row)
  })
}

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys) => {
    selectedRowKeys.value = keys.filter((id) => {
      const row = tasks.value.find((item) => item.taskId === id)
      return row && isBatchSelectable(row)
    })
  },
  getCheckboxProps: (record) => ({
    disabled: !isBatchSelectable(record),
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
  if (!silent) loadError.value = ''
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
    pruneSelectedRowKeys()
    syncProcessingPoll()
  } catch (error) {
    if (!silent) {
      console.error('加载任务列表失败:', error)
      loadError.value = error?.message || t('common.error')
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

const handleBatchDelete = () => {
  const keys = selectedRowKeys.value.filter((id) => {
    const row = tasks.value.find((item) => item.taskId === id)
    return row && canDeleteRecord(row)
  })
  if (!keys.length) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  const hasConfirmed = keys.some((id) => tasks.value.find((item) => item.taskId === id)?.status === 'confirmed')
  pendingDeleteTaskIds.value = keys
  deleteModalTaskId.value = keys.length === 1 ? keys[0] : ''
  deleteModalConfirmed.value = hasConfirmed
  deleteModalBatchCount.value = keys.length
  deleteModalOpen.value = true
}

const handleDeleteSubmit = async (reason) => {
  const ids = pendingDeleteTaskIds.value.length
    ? [...pendingDeleteTaskIds.value]
    : (deleteModalTaskId.value ? [deleteModalTaskId.value] : [])
  if (!ids.length) return

  deleteSubmitting.value = true
  batchDeleting.value = ids.length > 1
  try {
    await Promise.all(ids.map((id) => {
      const row = tasks.value.find((item) => item.taskId === id)
      const needsReason = row?.status === 'confirmed'
      return deleteTask(id, needsReason ? reason : undefined)
    }))
    message.success(
      ids.length > 1
        ? t('tasks.batchDeleteSuccess', { count: ids.length })
        : t('tasks.deleteSuccess')
    )
    selectedRowKeys.value = selectedRowKeys.value.filter((id) => !ids.includes(id))
    deleteModalOpen.value = false
    pendingDeleteTaskIds.value = []
    loadTasks()
  } catch (error) {
    message.error(t('messages.systemError'))
    console.error(error)
  } finally {
    deleteSubmitting.value = false
    batchDeleting.value = false
  }
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

const handleDelete = (record) => {
  if (!canDeleteRecord(record)) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  pendingDeleteTaskIds.value = [record.taskId]
  deleteModalTaskId.value = record.taskId
  deleteModalConfirmed.value = record.status === 'confirmed'
  deleteModalBatchCount.value = 1
  deleteModalOpen.value = true
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
    pending: t('tasks.syncPendingShort'),
    synced: t('tasks.syncSyncedShort'),
    sync_failed: t('tasks.syncFailedShort'),
  }
  return map[syncStatus] || syncStatus
}

const getStatusCopyText = (record) => {
  const parts = [getStatusText(record.status)]
  if (record.status === 'processing' && record.progressRowCount) {
    parts.push(String(record.progressRowCount))
  }
  if (record.status === 'confirmed' && record.syncStatus && record.syncStatus !== 'none') {
    parts.push(getSyncStatusText(record.syncStatus))
  }
  return parts.join(' · ')
}

const applyRouteQuery = () => {
  const status = typeof route.query.status === 'string' ? route.query.status : ''
  if (status) {
    filterStatus.value = status
    currentPage.value = 1
  }
}

onMounted(async () => {
  warmupOcrWorker()
  if (!authStore.userInfo?.permissions) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      /* ignore */
    }
  }
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
.task-load-error {
  margin-bottom: 12px;
}

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
      
      .table-action-cell {
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
      color: $text-strong;
      min-width: 0;
      flex: 1;

      &__preview {
        cursor: pointer;
        color: $primary;
        flex-shrink: 0;

        &:hover {
          color: $primary;
        }
      }
      
      .file-icon {
        font-size: $font-size-md;
      }
      
      .file-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        user-select: text;
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
