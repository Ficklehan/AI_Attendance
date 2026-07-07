<template>
  <div class="audit-container page-inner">
    <PageShell :title="$t('audit.title')" :subtitle="$t('audit.subtitle')" />

    <a-card class="audit-card surface-card" :bordered="false">
      <div class="filter-bar">
        <a-select 
          v-model:value="filterAction" 
          :placeholder="$t('audit.filterAction')" 
          allow-clear 
          class="action-select"
          @change="handleFilter"
        >
          <a-select-option value="USER_LOGIN">{{ $t('audit.actionLogin') }}</a-select-option>
          <a-select-option value="USER_REGISTER">{{ $t('audit.actionRegister') }}</a-select-option>
          <a-select-option value="TASK_CONFIRMED">{{ $t('audit.actionTaskConfirmed') }}</a-select-option>
          <a-select-option value="TASK_DELETED">{{ $t('audit.actionTaskDeleted') }}</a-select-option>
          <a-select-option value="CHANGE_PASSWORD">{{ $t('audit.actionChangePassword') }}</a-select-option>
          <a-select-option value="USER_DELETED">{{ $t('audit.actionUserDeleted') }}</a-select-option>
        </a-select>
        
        <a-date-picker
          v-model:value="dateRange"
          type="daterange"
          :placeholder="$t('audit.selectDateRange')"
          class="date-picker"
          @change="handleFilter"
        />
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
        :data-source="displayLogs" 
        :loading="loading" 
        :pagination="false"
        :scroll="{ x: scrollX }"
        class="audit-table rich-table-header"
      >
        <template #bodyCell="{ column, record, text }">
          <template v-if="column.key === 'action'">
            <CopyableCell :text="getActionText(record.action)">
              <span :class="['action-tag', getActionClass(record.action)]">
                {{ getActionText(record.action) }}
              </span>
            </CopyableCell>
          </template>
          <template v-else-if="column.key === 'details'">
            <CopyableCell :text="formatDetails(record)" />
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
          :show-total="(total) => $t('audit.total', { total })"
          class="pagination"
          @change="handleCurrentChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import request from '@/api/index'
import PageShell from '@/components/PageShell.vue'
import CopyableCell from '@/components/CopyableCell.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import { sumTableScrollX } from '@/utils/tableAutoColumns'
import { isCopyableTableColumn, resolveTableCellCopyText } from '@/utils/tableCopy'

const { t } = useI18n()

const logs = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterAction = ref('')
const dateRange = ref(null)

const baseColumns = computed(() => [
  { title: t('audit.username'), dataIndex: 'username', key: 'username', width: 120 },
  { title: t('audit.action'), dataIndex: 'action', key: 'action', width: 120 },
  { title: t('audit.targetType'), dataIndex: 'targetType', key: 'targetType', width: 100 },
  { title: t('audit.targetId'), dataIndex: 'targetId', key: 'targetId', width: 150, ellipsis: true },
  { title: t('audit.details'), dataIndex: 'details', key: 'details', ellipsis: true },
  { title: t('audit.createdAt'), dataIndex: 'createdAt', key: 'createdAt', width: 180 },
])
const { columns: sortedColumns, sortRows } = useTableColumnSort(baseColumns)
const {
  frozenColumns: columns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('audit-log', sortedColumns, { defaultFrozen: ['username'] })
const displayLogs = computed(() => sortRows(logs.value))
const scrollX = computed(() => sumTableScrollX(columns.value))

const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      current: currentPage.value,
      size: pageSize.value,
      action: filterAction.value,
    }
    
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0].format('YYYY-MM-DD')
      params.endDate = dateRange.value[1].format('YYYY-MM-DD')
    }
    
    const response = await request({
      url: '/audit',
      method: 'get',
      params,
    })
    
    logs.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error) {
    console.error(t('audit.loadingFailed'), error)
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  currentPage.value = 1
  loadLogs()
}

const handleSizeChange = (_current, size) => {
  pageSize.value = size
  currentPage.value = 1
  loadLogs()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadLogs()
}

const getActionText = (action) => {
  const textMap = {
    USER_LOGIN: t('audit.actionLogin'),
    USER_REGISTER: t('audit.actionRegister'),
    USER_LOGOUT: t('audit.actionLogout'),
    TASK_CONFIRMED: t('audit.actionTaskConfirmed'),
    TASK_DELETED: t('audit.actionTaskDeleted'),
    CHANGE_PASSWORD: t('audit.actionChangePassword'),
    USER_DELETED: t('audit.actionUserDeleted'),
    TASK_CANCELLED: t('audit.actionTaskCancelled'),
    TASK_SYNC_RETRY: t('audit.actionTaskSyncRetry'),
    RECORD_CALIBRATED: t('audit.actionRecordCalibrated'),
  }
  return textMap[action] || action
}

const getActionClass = (action) => {
  const classMap = {
    USER_LOGIN: 'action-tag--login',
    USER_REGISTER: 'action-tag--register',
    USER_LOGOUT: 'action-tag--logout',
    USER_DELETED: 'action-tag--danger',
    TASK_CONFIRMED: 'action-tag--success',
    TASK_DELETED: 'action-tag--danger',
    TASK_CANCELLED: 'action-tag--warning',
    TASK_SYNC_RETRY: 'action-tag--info',
    RECORD_CALIBRATED: 'action-tag--info',
    CHANGE_PASSWORD: 'action-tag--warning',
  }
  return classMap[action] || 'action-tag--default'
}

const formatDetails = (record) => {
  if (!record?.details) return '—'
  if (record.action === 'TASK_DELETED') {
    try {
      const data = typeof record.details === 'string' ? JSON.parse(record.details) : record.details
      const parts = []
      if (data.reason) {
        parts.push(`${t('audit.deleteReason')}: ${data.reason}`)
      }
      if (data.taskStatus) {
        parts.push(`${t('audit.deleteTaskStatus')}: ${data.taskStatus}`)
      }
      if (data.recordCount != null) {
        parts.push(`${t('audit.deleteRecordCount')}: ${data.recordCount}`)
      }
      if (data.fileKey) {
        parts.push(`${t('tasks.fileName')}: ${data.fileKey}`)
      }
      return parts.length ? parts.join(' · ') : record.details
    } catch {
      return record.details
    }
  }
  return record.details
}

onMounted(() => {
  loadLogs()
})
</script>

<style lang="scss" scoped>
.audit-container {
  .audit-card {
    border-radius: $radius-xl;
    border: none;
    box-shadow: $shadow-card;
    overflow: hidden;
    
    .card-header {
      padding: 20px 24px 16px;
      border-bottom: 1px solid $border;
      
      .header-left {
        display: flex;
        flex-direction: column;
        
        .card-title {
          display: flex;
          align-items: center;
          gap: $space-2;
          font-size: $font-size-xl;
          font-weight: $font-weight-extrabold;
          color: $text-strong;
          margin: 0 0 $space-1;
          letter-spacing: -0.02em;
          
          svg {
            color: $primary;
          }
        }
        
        .card-desc {
          margin: 0;
          font-size: 13px;
          color: $text-secondary;
        }
      }
    }
    
    .filter-bar {
      display: flex;
      gap: 12px;
      padding: 16px 24px;
      background: $bg-muted;
      
      .action-select {
        width: 140px;
        
        :deep(.ant-select-selector) {
          border-radius: $radius-md;
          border-color: $border;
        }
      }
      
      .date-picker {
        width: 280px;
        
        :deep(.ant-picker) {
          border-radius: $radius-md;
          border-color: $border;
        }
      }
    }
    
    .audit-table {
      padding: 0 24px;
      
      :deep(.ant-table) {
        border-radius: $radius-lg;
        overflow: hidden;
      }
      
      :deep(.ant-table-thead > tr > th) {
        background: $bg-muted;
        border-bottom: 1px solid $border;
        font-weight: $font-weight-semibold;
        font-size: 13px;
        color: $text-primary;
        padding: 12px 16px;
      }
      
      :deep(.ant-table-tbody > tr) {
        transition: all $duration-base $ease-smooth;
        
        &:hover {
          background: $bg-muted;
        }
      }
      
      :deep(.ant-table-tbody > tr > td) {
        padding: 12px 16px;
        font-size: 13px;
        color: $text-strong;
        border-bottom: 1px solid $bg-muted;
      }
      
      .action-tag {
        display: inline-block;
        border-radius: $radius-sm;
        font-size: $font-size-sm;
        font-weight: $font-weight-medium;
        padding: 2px 10px;
        line-height: 1.5;
        white-space: nowrap;
      }

      .action-tag--login {
        background: #dbeafe;
        color: #1e40af;
      }

      .action-tag--register,
      .action-tag--success {
        background: #dcfce7;
        color: #166534;
      }

      .action-tag--logout,
      .action-tag--info {
        background: #e0e7ff;
        color: #3730a3;
      }

      .action-tag--warning {
        background: #fef3c7;
        color: #92400e;
      }

      .action-tag--danger {
        background: #fee2e2;
        color: #991b1b;
      }

      .action-tag--default {
        background: #f3f4f6;
        color: #374151;
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
  }
}
</style>
