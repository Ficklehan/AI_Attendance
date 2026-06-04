<template>
  <a-drawer
    v-model:open="visible"
    :title="drawerTitle"
    placement="right"
    width="520"
    class="export-job-drawer"
    @close="handleClose"
  >
    <div class="export-toolbar">
      <div class="export-toolbar__left">
        <a-button size="small" :loading="loading" @click="fetchJobs(false)">
          {{ $t('export.refresh') }}
        </a-button>
        <a-button
          v-if="viewMode === 'recent' && hasDismissible"
          size="small"
          :loading="clearing"
          @click="handleClearAll"
        >
          {{ $t('export.clearAll') }}
        </a-button>
      </div>
      <div class="export-toolbar__right">
        <a-button
          v-if="viewMode === 'recent'"
          type="link"
          size="small"
          class="export-link-btn"
          @click="openHistory"
        >
          {{ $t('export.viewHistory') }}
        </a-button>
        <a-button
          v-else
          type="link"
          size="small"
          class="export-link-btn"
          @click="backToRecent"
        >
          {{ $t('export.backToRecent') }}
        </a-button>
      </div>
    </div>

    <p v-if="viewMode === 'recent' && hasActive" class="export-hint">{{ $t('export.processingHint') }}</p>
    <p v-else-if="viewMode === 'history'" class="export-hint export-hint--muted">{{ $t('export.historyHint') }}</p>

    <a-spin :spinning="loading && jobs.length === 0">
      <a-empty
        v-if="!loading && jobs.length === 0"
        :description="viewMode === 'history' ? $t('export.historyEmpty') : $t('export.empty')"
      >
        <a-button v-if="viewMode === 'recent'" type="link" @click="openHistory">
          {{ $t('export.viewHistory') }}
        </a-button>
      </a-empty>
      <div v-else class="export-list">
        <div v-for="job in jobs" :key="job.id" class="export-item">
          <div class="export-item__head">
            <span class="export-item__type">{{ typeLabel(job.exportType) }}</span>
            <a-tag :color="statusColor(job.status)" size="small">{{ statusLabel(job.status) }}</a-tag>
          </div>
          <div class="export-item__meta">
            <span>{{ formatTime(job.createdAt) }}</span>
            <span v-if="job.status === 'completed'">{{ $t('export.rowCount', { count: job.rowCount }) }}</span>
          </div>
          <div v-if="job.fileName" class="export-item__file">{{ job.fileName }}</div>
          <div v-if="job.status === 'failed' && job.errorMessage" class="export-item__error">
            {{ job.errorMessage }}
          </div>
          <div class="export-item__actions">
            <a-button
              v-if="job.downloadable"
              type="primary"
              size="small"
              :loading="downloadingId === job.id"
              @click="handleDownload(job)"
            >
              {{ $t('export.download') }}
            </a-button>
            <a-spin v-else-if="job.status === 'pending' || job.status === 'running'" size="small" />
          </div>
        </div>
      </div>
    </a-spin>

    <div v-if="total > jobs.length" class="export-more">
      <a-button block :loading="loadingMore" @click="loadMore">{{ $t('export.loadMore') }}</a-button>
    </div>
  </a-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  listExportJobsByScope,
  downloadExportJob,
  clearFinishedExports,
} from '@/api/export'
import { useExportCenter } from '@/composables/useExportCenter'
import { startAdaptivePoll } from '@/utils/adaptivePoll'

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open'])

const { t } = useI18n()
const { refreshExportSummary } = useExportCenter()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const viewMode = ref('recent')
const jobs = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const clearing = ref(false)
const downloadingId = ref('')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)
let stopExportPoll = null

const listScope = computed(() => (viewMode.value === 'history' ? 'all' : 'active'))

const drawerTitle = computed(() =>
  viewMode.value === 'history' ? t('export.historyTitle') : t('export.centerTitle'),
)

const hasActive = computed(() =>
  jobs.value.some((j) => j.status === 'pending' || j.status === 'running'),
)

const hasDismissible = computed(() =>
  jobs.value.some((j) => j.status === 'completed' || j.status === 'failed'),
)

const typeLabel = (type) => {
  if (type === 'task_list') return t('export.typeTaskList')
  if (type === 'employee_records') return t('export.typeEmployeeRecords')
  return type
}

const statusLabel = (status) => {
  const map = {
    pending: t('export.statusPending'),
    running: t('export.statusRunning'),
    completed: t('export.statusCompleted'),
    failed: t('export.statusFailed'),
  }
  return map[status] || status
}

const statusColor = (status) => {
  const map = {
    pending: 'default',
    running: 'processing',
    completed: 'success',
    failed: 'error',
  }
  return map[status] || 'default'
}

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const fetchJobs = async (append = false) => {
  if (append) {
    loadingMore.value = true
  } else {
    loading.value = true
  }
  try {
    const page = append ? currentPage.value + 1 : 1
    const res = await listExportJobsByScope({ current: page, size: pageSize }, listScope.value)
    const records = res.data?.records || []
    total.value = res.data?.total || 0
    currentPage.value = page
    jobs.value = append ? [...jobs.value, ...records] : records
    await refreshExportSummary()
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const loadMore = () => fetchJobs(true)

const openHistory = () => {
  viewMode.value = 'history'
  fetchJobs(false)
}

const backToRecent = () => {
  viewMode.value = 'recent'
  fetchJobs(false)
}

const handleClearAll = () => {
  Modal.confirm({
    title: t('export.clearConfirmTitle'),
    content: t('export.clearConfirmContent'),
    okText: t('export.clearAll'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      clearing.value = true
      try {
        await clearFinishedExports()
        message.success(t('export.clearSuccess'))
        await fetchJobs(false)
      } catch (e) {
        message.error(t('export.clearFailed'))
        console.error(e)
      } finally {
        clearing.value = false
      }
    },
  })
}

const handleDownload = async (job) => {
  downloadingId.value = job.id
  try {
    await downloadExportJob(job.id, job.fileName)
    message.success(t('export.downloadStarted'))
  } catch (e) {
    message.error(t('export.downloadFailed'))
  } finally {
    downloadingId.value = ''
  }
}

const startPolling = () => {
  stopPolling()
  stopExportPoll = startAdaptivePoll(
    () => visible.value && hasActive.value,
    () => fetchJobs(false),
    { intervalMs: 3000, maxIntervalMs: 12000 },
  )
}

const stopPolling = () => {
  if (stopExportPoll) {
    stopExportPoll()
    stopExportPoll = null
  }
}

const handleClose = () => {
  stopPolling()
  viewMode.value = 'recent'
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      viewMode.value = 'recent'
      fetchJobs(false)
      startPolling()
    } else {
      stopPolling()
    }
  },
)

watch(hasActive, (active) => {
  if (active && visible.value) {
    startPolling()
  }
})

onBeforeUnmount(stopPolling)

defineExpose({ refresh: () => fetchJobs(false) })
</script>

<style scoped lang="scss">
.export-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}

.export-toolbar__left,
.export-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.export-link-btn {
  padding-left: 4px;
  padding-right: 4px;
}

.export-hint {
  font-size: 12px;
  color: $text-secondary;
  margin: 0 0 12px;
}

.export-hint--muted {
  color: $text-tertiary;
}

.export-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.export-item {
  border: 1px solid $border;
  border-radius: $radius-lg;
  padding: 12px 14px;
  background: $bg-surface;
}

.export-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.export-item__type {
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.export-item__meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: $text-secondary;
  margin-bottom: 4px;
}

.export-item__file {
  font-size: 12px;
  color: $text-tertiary;
  word-break: break-all;
  margin-bottom: 6px;
}

.export-item__error {
  font-size: 12px;
  color: $danger;
  margin-bottom: 8px;
  word-break: break-word;
}

.export-item__actions {
  display: flex;
  align-items: center;
  min-height: 28px;
}

.export-more {
  margin-top: 16px;
}
</style>
