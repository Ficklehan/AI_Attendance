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
        </a-select>
        
        <a-date-picker
          v-model:value="dateRange"
          type="daterange"
          :placeholder="$t('audit.selectDateRange')"
          class="date-picker"
          @change="handleFilter"
        />
      </div>
      
      <a-table 
        :columns="columns" 
        :data-source="displayLogs" 
        :loading="loading" 
        :pagination="false"
        class="audit-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-tag :color="getActionColor(record.action)" class="action-tag">
              {{ getActionText(record.action) }}
            </a-tag>
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
import { useTableColumnSort } from '@/composables/useTableColumnSort'

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
const { columns, sortRows } = useTableColumnSort(baseColumns)
const displayLogs = computed(() => sortRows(logs.value))

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

const handleSizeChange = (val) => {
  pageSize.value = val
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
  }
  return textMap[action] || action
}

const getActionColor = (action) => {
  const colorMap = {
    USER_LOGIN: 'primary',
    USER_REGISTER: 'success',
    USER_LOGOUT: 'info',
    TASK_CONFIRMED: 'success',
    TASK_DELETED: 'error',
    CHANGE_PASSWORD: 'warning',
  }
  return colorMap[action] || 'default'
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
        border-radius: $radius-sm;
        font-size: $font-size-sm;
        font-weight: $font-weight-medium;
        padding: 2px 10px;
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
