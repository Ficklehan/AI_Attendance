<template>
  <div class="task-edit-container page-inner">
    <PageShell :title="$t('taskEdit.title')" :subtitle="taskId">
      <template #extra>
        <a-space wrap>
          <a-tag v-if="task?.status === 'cancelled'" color="default">{{ $t('tasks.statusCancelled') }}</a-tag>
          <span class="record-count">{{ $t('tasks.totalRecords', { total: records.length }) }}</span>
          <a-button @click="$router.back()">
            <template #icon><UndoOutlined /></template>
            {{ $t('common.back') }}
          </a-button>
          <a-button
            v-if="canDeleteTask"
            danger
            :loading="deleting"
            @click="handleDeleteTask"
          >
            <template #icon><DeleteOutlined /></template>
            {{ $t('common.delete') }}
          </a-button>
          <a-button
            v-if="canDeleteTask"
            :loading="deleting"
            @click="handleReupload"
          >
            <template #icon><UploadOutlined /></template>
            {{ $t('taskEdit.reupload') }}
          </a-button>
          <a-button
            @click="handleExportExcel"
            :disabled="records.length === 0"
          >
            <template #icon><DownloadOutlined /></template>
            {{ $t('taskEdit.exportExcel') }}
          </a-button>
        </a-space>
      </template>
    </PageShell>

    <a-card :loading="loading" :bordered="false" class="edit-card surface-card">

      <a-alert
        v-if="task && task.status === 'confirmed' && task.syncStatus && task.syncStatus !== 'none'"
        :type="syncAlertType"
        show-icon
        class="sync-alert"
      >
        <template #message>{{ syncAlertMessage }}</template>
        <template v-if="task.syncError && task.syncStatus === 'sync_failed'" #description>
          {{ task.syncError }}
        </template>
        <template v-if="canRetrySync" #action>
          <a-button size="small" :loading="retryingSync" @click="handleRetrySync">
            {{ $t('taskEdit.syncRetry') }}
          </a-button>
        </template>
      </a-alert>
      
      <StatOverview
        v-if="records.length > 0"
        :items="statItems"
      />

      <div v-if="previewImagesList.length > 0" class="task-image-files">
        <div class="task-image-files__head">
          <FileImageOutlined />
          <span class="task-image-files__title">
            {{ $t('taskEdit.originalImage') }}
            <em>({{ previewImagesList.length }}{{ $t('tasks.images') }})</em>
          </span>
        </div>
        <ul class="task-image-files__list">
          <li
            v-for="(url, idx) in previewImagesList"
            :key="`${url}-${idx}`"
            class="task-image-files__item"
          >
            <button type="button" class="task-image-files__link" @click="openImagePreview(idx)">
              <FileImageOutlined class="task-image-files__icon" />
              <span class="task-image-files__name">{{ getFileName(url) }}</span>
              <EyeOutlined class="task-image-files__view" />
            </button>
          </li>
        </ul>
      </div>
      
      <a-tabs v-model:active-key="activeTab" class="edit-tabs">
        <a-tab-pane key="edit" :tab="$t('taskEdit.editData')">
          <div class="duplicate-scope-bar">
            <span class="duplicate-scope-label">{{ $t('taskEdit.duplicateScopeLabel') }}</span>
            <a-radio-group
              v-model:value="duplicateScope"
              size="small"
              @change="handleDuplicateScopeChange"
            >
              <a-radio-button value="confirmed_only">{{ $t('taskEdit.duplicateScopeConfirmedOnly') }}</a-radio-button>
              <a-radio-button value="confirmed_and_processing">{{ $t('taskEdit.duplicateScopeConfirmedAndProcessing') }}</a-radio-button>
            </a-radio-group>
          </div>
          <div v-if="anomalyAlerts.length > 0" class="anomaly-hint">
            <div class="anomaly-summary">
              <ExclamationCircleOutlined class="anomaly-icon" />
              <span class="anomaly-text">{{ $t('home.anomalyAlert', { count: anomalyAlerts.length }) }}</span>
              <a-button type="link" size="small" @click="showAnomalyDetail = !showAnomalyDetail" class="anomaly-toggle">
                {{ showAnomalyDetail ? $t('home.collapse') : $t('home.expand') }}
              </a-button>
            </div>
            <div v-if="showAnomalyDetail" class="anomaly-detail-list">
              <div v-for="(alert, idx) in anomalyAlerts" :key="idx" class="anomaly-item">
                <span class="anomaly-index">{{ alert.index + 1 }}</span>
                <span class="anomaly-name">{{ alert.name }}</span>
                <span class="anomaly-reasons">
                  <TruncatedTag
                    v-for="(reason, rIdx) in alert.reasons"
                    :key="rIdx"
                    :text="reason"
                    :color="getAnomalyTagColor(reason)"
                    size="small"
                  />
                </span>
              </div>
            </div>
          </div>

          <a-table 
            :columns="columns" 
            :data-source="tableRecords" 
            :pagination="false"
            :scroll="{ x: scrollX }"
            :row-key="getRowKey"
            :row-class-name="getRowClassName"
            :expanded-row-keys="expandedDuplicateRowKeys"
            :expand-icon="() => null"
            :row-expandable="(record) => !!getDuplicateMeta(record)"
            @expand="handleTableExpand"
            size="small"
            class="edit-table rich-table-header"
          >
            <template #headerCell="{ column }">
              <TableSortableHeader
                v-if="!isAuxHeaderColumn(column)"
                :column="column"
                :title="formatHeaderTitle(column.title)"
                @sort="onSorterToggle"
              >
                <template #extra>
                  <TableHeaderFilter
                    v-if="column.searchField"
                    :title="formatHeaderTitle(column.title)"
                    :open="activeHeaderFilterField === column.searchField"
                    :active="Boolean((headerFilters[column.searchField] || '').trim())"
                    v-model:keyword="headerFilterKeyword"
                    @openChange="(open) => handleHeaderPopoverOpen(column.searchField, open)"
                    @reset="clearHeaderFilter"
                    @apply="applyHeaderFilter"
                  />
                </template>
              </TableSortableHeader>
              <span v-else></span>
            </template>
            <template #expandedRowRender="{ record }">
              <div v-if="getDuplicateMeta(record)" class="duplicate-expanded">
                <div class="duplicate-expanded-title">{{ $t('taskEdit.duplicateDetailTitle') }}</div>
                <div class="duplicate-expanded-list">
                  <table class="duplicate-expanded-table">
                    <thead>
                      <tr>
                        <th>{{ $t('taskEdit.sourceTask') }}</th>
                        <th>{{ $t('taskEdit.pageNumber') }}</th>
                        <th>{{ $t('taskEdit.workerNumber') }}</th>
                        <th>{{ $t('taskEdit.name') }}</th>
                        <th>{{ $t('taskEdit.countryField') }}</th>
                        <th>{{ $t('taskEdit.warehouse') }}</th>
                        <th>{{ $t('taskEdit.date') }}</th>
                        <th>{{ $t('taskEdit.agency') }}</th>
                        <th>{{ $t('taskEdit.shift') }}</th>
                        <th>{{ $t('taskEdit.arrival') }}</th>
                        <th>{{ $t('taskEdit.departure') }}</th>
                        <th>{{ $t('taskEdit.breakTime') }}</th>
                        <th>{{ $t('taskEdit.signature') }}</th>
                        <th>{{ $t('taskEdit.observations') }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr
                        v-for="item in getDuplicateMeta(record).members"
                        :key="item.rowKey"
                      >
                        <td>{{ item.sourceTaskId || taskId }}</td>
                        <td>{{ item.pageNum || item.PAGE_NUM || '-' }}</td>
                        <td>{{ item.NO || '-' }}</td>
                        <td>{{ item.displayName || '-' }}</td>
                        <td>{{ item.Pays || '-' }}</td>
                        <td>{{ item.Entrepot || '-' }}</td>
                        <td>{{ item.Date || '-' }}</td>
                        <td>{{ item.AGENCE_INTERIMAIRE || '-' }}</td>
                        <td>{{ formatDuplicateCell(item.HORAIRES_DU_TRAVAIL) }}</td>
                        <td>{{ formatDuplicateCell(item.ARRIVEE) }}</td>
                        <td>{{ formatDuplicateCell(item.DEPAR) }}</td>
                        <td>{{ formatDuplicateCell(item.PAUSE, 'pause') }}</td>
                        <td>{{ formatDuplicateCell(item.SIGNATURE) }}</td>
                        <td>{{ formatDuplicateCell(item.Observations) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </template>
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'anomalyReasons'">
                <div v-if="getRecordAnomalyReasons(record).length > 0" class="inline-anomaly-tags">
                  <TruncatedTag
                    v-for="(reason, reasonIdx) in getRecordAnomalyReasons(record)"
                    :key="reasonIdx"
                    :text="reason"
                    :color="getAnomalyTagColor(reason)"
                    size="small"
                  />
                </div>
                <span v-else class="cell-muted">-</span>
              </template>
              <template v-if="column.key === 'PAGE_NUM'">
                <a-input
                  v-if="isRecordEditable(record)"
                  v-model:value="record.PAGE_NUM"
                  size="small"
                  :bordered="false"
                />
                <span v-else class="cell-text">{{ record.PAGE_NUM || record.pageNum || '-' }}</span>
              </template>
              <template v-if="column.key === 'NO'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.NO" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.NO }}</span>
              </template>
              <template v-if="column.key === 'Pays'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Pays" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.Pays }}</span>
              </template>
              <template v-if="column.key === 'Entrepot'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Entrepot" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.Entrepot }}</span>
              </template>
              <template v-if="column.key === 'NOM_PRENOM'">
                <div class="name-cell">
                  <a-input
                    v-if="isRecordEditable(record)"
                    v-model:value="record.NOM_PRENOM"
                    size="small"
                    :class="{ 'required-empty': isRequiredFieldEmpty(record, 'NOM_PRENOM') }"
                    :bordered="false"
                    @change="markNameManuallyEdited(record)"
                  />
                  <span v-else class="cell-text">{{ record.NOM_PRENOM }}</span>
                  <div v-if="getDuplicateMeta(record)" class="duplicate-tools">
                    <a-tag color="gold" size="small">{{ $t('taskEdit.duplicateTag') }}</a-tag>
                    <a-button type="link" size="small" class="duplicate-link" @click="toggleDuplicateExpand(record)">
                      {{ expandedDuplicateRowKeys.includes(record._rowKey) ? $t('taskEdit.collapseDetail') : $t('taskEdit.expandDetail') }}
                    </a-button>
                    <a-button
                      v-if="!record._duplicateConfirmedUnique"
                      type="link"
                      size="small"
                      class="duplicate-link"
                      @click="confirmNotDuplicate(record)"
                    >
                      {{ $t('taskEdit.notDuplicate') }}
                    </a-button>
                  </div>
                </div>
              </template>
              <template v-if="column.key === 'AGENCE_INTERIMAIRE'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.AGENCE_INTERIMAIRE" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.AGENCE_INTERIMAIRE }}</span>
              </template>
              <template v-if="column.key === 'HORAIRES_DU_TRAVAIL'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.HORAIRES_DU_TRAVAIL" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.HORAIRES_DU_TRAVAIL }}</span>
              </template>
              <template v-if="column.key === 'Date'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Date" size="small" :class="{ 'required-empty': isRequiredFieldEmpty(record, 'Date') }" :bordered="false" />
                <span v-else class="cell-text">{{ record.Date }}</span>
              </template>
              <template v-if="column.key === 'ARRIVEE'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.ARRIVEE" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.ARRIVEE }}</span>
              </template>
              <template v-if="column.key === 'DEPAR'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.DEPAR" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.DEPAR }}</span>
              </template>
              <template v-if="column.key === 'PAUSE'">
                <a-input-number v-if="isRecordEditable(record)" v-model:value="record.PAUSE" size="small" :bordered="false" :controls="false" :formatter="formatPauseInput" :parser="parsePauseInput" style="width: 100%" />
                <span v-else class="cell-text">{{ formatPauseDisplay(record.PAUSE) }}</span>
              </template>
              <template v-if="column.key === 'SIGNATURE'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.SIGNATURE" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.SIGNATURE }}</span>
              </template>
              <template v-if="column.key === 'Observations'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Observations" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.Observations }}</span>
              </template>
              <template v-if="column.key === 'SmartMark'">
                <div class="mark-tags-cell">
                  <template v-for="tag in getRecordMarkTags(record)" :key="tag.key">
                    <a-popover
                      v-if="tag.showCalibrationHistory"
                      placement="bottomLeft"
                      trigger="click"
                      overlay-class-name="calibration-history-popover"
                    >
                      <template #title>{{ $t('calibration.historyTitle') }}</template>
                      <template #content>
                        <div class="calibration-popover-list">
                          <div
                            v-for="(entry, hIdx) in getCalibrationHistoryReversed(record)"
                            :key="hIdx"
                            class="calibration-popover-item"
                          >
                            <div class="calibration-popover-meta">
                              <span>{{ entry.byName || entry.by }}</span>
                              <span>{{ formatCalibrationTime(entry.at) }}</span>
                            </div>
                            <div v-if="entry.reason" class="calibration-popover-reason">{{ entry.reason }}</div>
                            <ul class="calibration-popover-changes">
                              <li
                                v-for="line in formatCalibrationHistoryChanges(entry)"
                                :key="line.field"
                              >
                                {{ line.label }}: <span class="from">{{ line.from }}</span> → <span class="to">{{ line.to }}</span>
                              </li>
                            </ul>
                          </div>
                        </div>
                      </template>
                      <a-tag :color="tag.color" size="small" class="mark-tag calibration-tag">
                        {{ tag.label }}
                      </a-tag>
                    </a-popover>
                    <a-tag v-else :color="tag.color" size="small" class="mark-tag">{{ tag.label }}</a-tag>
                  </template>
                </div>
              </template>
              <template v-if="column.key === 'workHours'">
                <span class="work-hours">{{ calculateWorkHours(record) }}</span>
              </template>
              <template v-if="column.key === 'action'">
                <a-space v-if="isConfirmedTask && canCalibrateRecord && !record.isDeleted" size="small">
                  <a-button type="link" size="small" @click="openCalibration(record)">
                    {{ $t('calibration.action') }}
                  </a-button>
                </a-space>
                <template v-else>
                  <a-tooltip :title="(record?.isDeleted || isAbsentRow(record)) ? $t('taskEdit.restore') : $t('common.delete')">
                    <a-button 
                      v-if="record?.isDeleted || isAbsentRow(record)"
                      type="link"
                      size="small"
                      @click="toggleDelete(record, index)"
                      class="action-btn restore-btn"
                    >
                      <template #icon><UndoOutlined /></template>
                    </a-button>
                    <a-button 
                      v-else
                      type="link"
                      size="small"
                      danger
                      @click="toggleDelete(record, index)"
                      class="action-btn delete-btn"
                    >
                      <template #icon><DeleteOutlined /></template>
                    </a-button>
                  </a-tooltip>
                </template>
              </template>
            </template>
          </a-table>
          
          <div class="action-bar">
            <a-button
              v-if="task?.status === 'processed'"
              type="primary"
              :loading="submitting"
              @click="handleSubmit"
              size="large"
            >
              {{ $t('taskEdit.submitConfirm') }}
            </a-button>
            <a-button v-if="task?.status === 'processed'" @click="$router.back()" size="large">{{ $t('common.cancel') }}</a-button>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
  
  <ImagePreviewModal
    v-model:open="previewVisible"
    :images="previewImagesList"
    :initial-index="previewCurrentIndex"
  />

  <RecordCalibrationModal
    v-model:open="calibrationVisible"
    :record="calibrationRecord"
    :submitting="calibrationSubmitting"
    @submit="handleCalibrationSubmit"
  />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal as aModal } from 'ant-design-vue'
import { DeleteOutlined, UndoOutlined, ExclamationCircleOutlined, FileImageOutlined, EyeOutlined, UploadOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { getTaskDetail, confirmTask, deleteTask, retryFeishuSync, checkTaskDuplicateNames, calibrateTaskRecord } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import { resolveTaskImageUrls, fileNameFromImageUrl } from '@/utils/imageUrl'
import StatOverview from '@/components/StatOverview.vue'
import PageShell from '@/components/PageShell.vue'
import TruncatedTag from '@/components/TruncatedTag.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import RecordCalibrationModal from '@/components/RecordCalibrationModal.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import TableHeaderFilter from '@/components/TableHeaderFilter.vue'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useAutoSizedColumns } from '@/composables/useAutoSizedColumns'
import axios from 'axios'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { applyMissingPays } from '@/utils/countryDefaults'
import {
  translateAnomalyReason,
  translateSmartMark,
  buildRecordMarkTags,
  markContains,
  anomalyReasonKind,
} from '@/utils/recognitionLabels'
import { buildRecognitionTableColumns } from '@/utils/recognitionTableColumns'
import {
  hasManualCalibration,
  parseCalibrationHistory,
  formatHistoryChanges,
} from '@/utils/calibrationHistory'
import { FIELD_LABEL_KEYS } from '@/constants/calibratableFields'
import { API_BASE_PATH } from '@/constants/apiBase'
import {
  hasRequiredMissing,
  getMissingRequiredFieldKeys,
  REQUIRED_FIELD_I18N_KEYS,
} from '@/utils/requiredRecordFields'
import { startAdaptivePoll } from '@/utils/adaptivePoll'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const taskId = computed(() => route.params.taskId)
const activeTab = ref('edit')
const loading = ref(false)
const submitting = ref(false)
const retryingSync = ref(false)
const deleting = ref(false)
let stopSyncPoll = null
const records = ref([])
const rawData = ref('')
const showAnomalyDetail = ref(true)
const previewVisible = ref(false)
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)
const task = ref(null)
const canDeleteTask = computed(() => task.value?.status !== 'confirmed')
const isConfirmedTask = computed(() => task.value?.status === 'confirmed')
const canCalibrateRecord = computed(
  () => authStore.userInfo?.permissions?.recordCalibrate === true
)
const isRecordEditable = (record) =>
  !isConfirmedTask.value && !record?.isDeleted && !isAbsentRow(record)

const calibrationVisible = ref(false)
const calibrationRecord = ref(null)
const calibrationSubmitting = ref(false)
const expandedDuplicateRowKeys = ref([])
const duplicateMetaMap = ref({})
const duplicateRefreshing = ref(false)
const duplicateScope = ref('confirmed_only')
const activeHeaderFilterField = ref('')
const headerFilterKeyword = ref('')
const headerFilters = ref({})

const syncAlertType = computed(() => {
  const s = task.value?.syncStatus
  if (s === 'synced') return 'success'
  if (s === 'sync_failed') return 'error'
  return 'info'
})

const syncAlertMessage = computed(() => {
  const s = task.value?.syncStatus
  if (s === 'pending') return t('taskEdit.syncPending')
  if (s === 'synced') return t('taskEdit.syncSynced')
  if (s === 'sync_failed') return t('taskEdit.syncFailed')
  return ''
})

const canRetrySync = computed(() =>
  task.value?.status === 'confirmed' && task.value?.syncStatus === 'sync_failed'
)

const clearSyncPoll = () => {
  if (stopSyncPoll) {
    stopSyncPoll()
    stopSyncPoll = null
  }
}

const startSyncPoll = () => {
  clearSyncPoll()
  stopSyncPoll = startAdaptivePoll(
    () => task.value?.syncStatus === 'pending',
    () => loadTask(true),
    { intervalMs: 3000, maxIntervalMs: 8000 },
  )
}

const stats = computed(() => {
  const result = {
    normal: 0,
    handwriting: 0,
    blurred: 0,
    night: 0,
    absent: 0,
    deleted: 0,
  }
  
  records.value.forEach(record => {
    // 统计已删除的记录
    if (record.isDeleted) {
      result.deleted++
      return
    }
    
    // 统计识别结果 - 根据anomalies字段来判断
    const anomalies = getEffectiveAnomalies(record)
    const mark = getDisplaySmartMark(record)
    
    // 如果有效 anomalies 为空，说明识别时正常；夜班不算异常
    if (anomalies.length === 0 && !hasRequiredMissing(record)) {
      result.normal++
    }
    
    // 手写根据SmartMark中标记为手写的统计
    if (mark.includes('手写')) result.handwriting++
    
    // 其他类型根据SmartMark来判断
    if (mark.includes('模糊')) result.blurred++
    if (mark.includes('夜班')) result.night++
    if (mark.includes('未出勤')) result.absent++
  })
  
  return result
})

const statItems = computed(() => [
  { key: 'normal', variant: 'normal', value: stats.value.normal, label: t('home.statsNormal') },
  { key: 'handwriting', variant: 'handwriting', value: stats.value.handwriting, label: t('home.statsHandwriting') },
  { key: 'blurred', variant: 'blurred', value: stats.value.blurred, label: t('home.statsBlurred') },
  { key: 'night', variant: 'night', value: stats.value.night, label: t('home.statsNight') },
  { key: 'absent', variant: 'absent', value: stats.value.absent, label: t('home.statsAbsent') },
  { key: 'deleted', variant: 'deleted', value: stats.value.deleted, label: t('home.statsDeleted') },
])

const mergeCellProps = (props, column) => {
  const wrapKeys = ['anomalyReasons']
  const wrapClass = wrapKeys.includes(column?.key) ? 'cell-wrap' : ''
  if (!wrapClass) return props
  const cls = [props.class, wrapClass].filter(Boolean).join(' ')
  return { ...props, class: cls || undefined }
}

const cellStyle = (record, index, column) => {
  if (!record) return {}
  if (record?.isDeleted || isAbsentRow(record)) {
    return mergeCellProps({
      style: {
        backgroundColor: '#FFF0F0',
        color: '#D94040',
        fontStyle: 'italic',
        textDecoration: 'line-through',
        textDecorationColor: '#E8A0A0',
      },
    }, column)
  }
  if (hasRequiredMissing(record)) {
    return mergeCellProps({ style: { backgroundColor: '#FFF9EC' } }, column)
  }
  if ((record?.SmartMark || '').includes('模糊')) {
    return mergeCellProps({ style: { backgroundColor: '#FFF9EC' } }, column)
  }
  return mergeCellProps({}, column)
}

const baseColumns = computed(() => buildRecognitionTableColumns(t, {
  cellStyle,
  includeWorkHours: true,
  searchFields: true,
  fixedAction: true,
  actionColumnWidth: isConfirmedTask.value && canCalibrateRecord.value ? 88 : 50,
}))
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })
const tableRecords = computed(() => sortRows(filteredRecords.value))
const { columns, scrollX } = useAutoSizedColumns(sortedColumns, tableRecords, {
  actionWidth: isConfirmedTask.value && canCalibrateRecord.value ? 88 : 50,
  getCellSample: (col, record) => {
    if (col.key === 'workHours') return calculateWorkHours(record)
    if (col.key === 'anomalyReasons') return getRecordAnomalyReasons(record).join(', ')
    if (col.key === 'PAUSE') return formatPauseDisplay(record.PAUSE)
    return undefined
  },
})

const filteredRecords = computed(() => {
  const active = Object.entries(headerFilters.value).filter(([, v]) => String(v || '').trim())
  if (!active.length) return records.value
  return records.value.filter((row) => {
    return active.every(([field, keyword]) => {
      const value = row?.[field]
      return String(value ?? '').toLowerCase().includes(String(keyword).toLowerCase())
    })
  })
})

const handleHeaderPopoverOpen = (field, open) => {
  if (open) {
    activeHeaderFilterField.value = field
    headerFilterKeyword.value = headerFilters.value[field] || ''
  } else if (activeHeaderFilterField.value === field) {
    activeHeaderFilterField.value = ''
  }
}

const applyHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  headerFilters.value = {
    ...headerFilters.value,
    [activeHeaderFilterField.value]: (headerFilterKeyword.value || '').trim(),
  }
  activeHeaderFilterField.value = ''
}

const clearHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  headerFilters.value = {
    ...headerFilters.value,
    [activeHeaderFilterField.value]: '',
  }
  headerFilterKeyword.value = ''
  activeHeaderFilterField.value = ''
}

const isAuxHeaderColumn = (column) => {
  const key = String(column?.key || '')
  return key.includes('EXPAND_COLUMN') || key.includes('SELECTION_COLUMN')
}

const formatHeaderTitle = (title) => {
  if (Array.isArray(title)) {
    return title.join('').trim()
  }
  return title == null ? '' : String(title)
}

const getCalibrationHistoryReversed = (record) =>
  [...parseCalibrationHistory(record)].reverse()

const formatCalibrationTime = (at) => {
  if (!at) return '—'
  const s = String(at).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

const formatCalibrationHistoryChanges = (entry) =>
  formatHistoryChanges(entry, (field) => t(FIELD_LABEL_KEYS[field] || field))

const getRecordMarkTags = (record) =>
  buildRecordMarkTags(record, {
    getDisplayMark: getDisplaySmartMark,
    isAbsentRow,
    t,
    hasManualCalibration,
  })

const openCalibration = (record) => {
  if (!canCalibrateRecord.value) {
    message.warning(t('calibration.permissionDenied'))
    return
  }
  calibrationRecord.value = { ...record }
  calibrationVisible.value = true
}

const applyCalibratedRecord = (updated) => {
  if (!updated?._rowKey) return
  const idx = records.value.findIndex((r) => r._rowKey === updated._rowKey)
  if (idx >= 0) {
    records.value[idx] = normalizeRecordPause({
      ...records.value[idx],
      ...updated,
      _rowKey: updated._rowKey,
    })
  }
}

const handleCalibrationSubmit = async ({ rowKey, updates, reason }) => {
  calibrationSubmitting.value = true
  try {
    const res = await calibrateTaskRecord(taskId.value, { rowKey, updates, reason })
    message.success(t('calibration.syncingFeishu'))
    calibrationVisible.value = false
    if (res.data?.record) {
      applyCalibratedRecord(res.data.record)
    } else {
      await loadTask(true)
    }
    if (res.data?.syncStatus === 'pending') {
      startSyncPoll()
    }
  } catch (e) {
    console.error(e)
  } finally {
    calibrationSubmitting.value = false
  }
}

const loadTask = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const response = await getTaskDetail(taskId.value)
    task.value = response.data

    if (task.value?.syncStatus === 'pending') {
      startSyncPoll()
    } else {
      clearSyncPoll()
    }
    
    const dataPayload =
      task.value.status === 'confirmed' && task.value.confirmedData
        ? task.value.confirmedData
        : task.value.rawData
    if (dataPayload) {
      const parsedRecords = JSON.parse(dataPayload)
      records.value = parsedRecords.map((record, idx) => normalizeRecordPause({
        ...record,
        isDeleted: record.isDeleted || false,
        _rowKey: record._rowKey || `${taskId.value}-${idx}-${Date.now()}`,
        _baseName: String(record.NOM_PRENOM || '').trim(),
        _nameAutoNumbered: false,
        _duplicateConfirmedUnique: false
      }))
      refreshDuplicateDecorations()
      await fetchConfirmedDuplicateHints()
    }
    rawData.value = task.value.aiRawOutput || ''
    
    previewImagesList.value = await resolveTaskImageUrls(task.value.imageUrls, task.value.fileKey)
  } catch (error) {
    message.error(t('taskEdit.loadingFailed'))
    console.error(error)
  } finally {
    if (!silent) loading.value = false
  }
}

const handleRetrySync = async () => {
  if (!canRetrySync.value) return
  retryingSync.value = true
  try {
    await retryFeishuSync(taskId.value)
    message.success(t('taskEdit.syncRetrySuccess'))
    await loadTask(true)
    startSyncPoll()
  } catch (error) {
    message.error(t('taskEdit.syncRetryFailed'))
    console.error(error)
  } finally {
    retryingSync.value = false
  }
}

const getExportFilename = (response, fallback) => {
  const disposition = response.headers.get('Content-Disposition') || response.headers.get('content-disposition')
  const match = disposition && disposition.match(/filename\*?=(?:UTF-8''|")?([^";]+)/i)
  if (!match) return fallback
  try {
    return decodeURIComponent(match[1].replace(/"/g, ''))
  } catch (e) {
    return match[1].replace(/"/g, '')
  }
}

const handleExportExcel = async () => {
  if (!taskId.value) return
  try {
    const token = localStorage.getItem('attendance_token')
    const response = await fetch(`${API_BASE_PATH}/local/export/${taskId.value}/xlsx`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = getExportFilename(response, `attendance_${taskId.value}.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (error) {
    message.error(t('taskEdit.exportFailed'))
    console.error('Export Excel failed:', error)
  }
}

const runDeleteTask = async (redirectTo) => {
  deleting.value = true
  try {
    await deleteTask(taskId.value)
    message.success(t('tasks.deleteSuccess'))
    router.push(redirectTo)
  } catch (error) {
    message.error(t('taskEdit.deleteFailed'))
    console.error(error)
  } finally {
    deleting.value = false
  }
}

const handleDeleteTask = () => {
  if (!canDeleteTask.value) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  aModal.confirm({
    title: t('common.delete'),
    content: t('tasks.deleteConfirm', { taskId: taskId.value }),
    okText: t('common.delete'),
    cancelText: t('common.cancel'),
    okType: 'danger',
    maskClosable: false,
    onOk: () => runDeleteTask('/tasks'),
  })
}

const handleReupload = () => {
  if (!canDeleteTask.value) {
    message.warning(t('taskEdit.deleteNotAllowed'))
    return
  }
  aModal.confirm({
    title: t('taskEdit.confirmReupload'),
    content: t('taskEdit.reuploadDesc'),
    okText: t('taskEdit.reupload'),
    cancelText: t('common.cancel'),
    okType: 'danger',
    maskClosable: false,
    onOk: () => runDeleteTask('/home'),
  })
}

const openImagePreview = (index) => {
  previewCurrentIndex.value = Math.min(Math.max(index, 0), Math.max(previewImagesList.value.length - 1, 0))
  previewVisible.value = true
}

const getFileName = (url) => {
  const name = fileNameFromImageUrl(url)
  return name || t('taskEdit.unknownFile')
}

const toggleDelete = (record, index) => {
  if (isAbsentRow(record) && !record.isDeleted) {
    record._prevMark = record.SmartMark
    record.SmartMark = '正常'
    record._restored = true
    records.value.splice(index, 1, record)
    return
  }
  record.isDeleted = !record.isDeleted
  if (record.isDeleted) {
    record._prevMark = record.SmartMark
    record.SmartMark = '已删除'
  } else {
    if (record._prevMark && record._prevMark !== '未出勤') {
      record.SmartMark = record._prevMark
      delete record._prevMark
    } else {
      record.SmartMark = '正常'
    }
  }
  records.value.splice(index, 1, record)
  refreshDuplicateDecorations()
}

const isAbsentRow = (record) => {
  const mark = record?.SmartMark || ''
  return mark.includes('未出勤') && !record?._restored
}

const calculateWorkHours = (record) => {
  if (record?.isDeleted || isAbsentRow(record)) {
    return '-'
  }
  
  const arriveTime = record?.ARRIVEE
  const departTime = record?.DEPAR
  const pauseMinutes = normalizePauseMinutes(record?.PAUSE)
  
  if (!arriveTime || !departTime || arriveTime === '???' || departTime === '???') {
    return '-'
  }
  
  const arriveMinutes = parseTimeToMinutes(arriveTime)
  const departMinutes = parseTimeToMinutes(departTime)
  
  if (arriveMinutes === null || departMinutes === null) {
    return '-'
  }
  
  let totalMinutes = departMinutes - arriveMinutes
  if (totalMinutes < 0) {
    totalMinutes += 24 * 60
  }
  
  const pause = (pauseMinutes !== null && pauseMinutes !== undefined && pauseMinutes !== '') ? Number(pauseMinutes) : 0
  const workMinutes = totalMinutes - pause
  
  if (workMinutes < 0) {
    return '-'
  }
  
  const workHours = (workMinutes / 60).toFixed(2)
  return workHours
}

const parseTimeToMinutes = (timeStr) => {
  if (!timeStr || timeStr.trim() === '' || timeStr === '???') {
    return null
  }
  
  const cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':')
  const parts = cleanTime.split(':')
  
  if (parts.length === 2) {
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    if (!isNaN(hours) && !isNaN(minutes)) {
      return hours * 60 + minutes
    }
  } else if (parts.length === 1) {
    const num = parseFloat(parts[0])
    if (!isNaN(num)) {
      return Math.floor(num) * 60 + Math.round((num % 1) * 60)
    }
  }
  
  return null
}

const getRowTypeLabel = (record) => {
  if (record?.isDeleted) return '已删除'
  const mark = getDisplaySmartMark(record)
  if (mark.includes('未出勤')) return '未出勤'
  if (mark.includes('模糊')) return '模糊'
  if (mark.includes('手写')) return '手写'
  return '正常'
}

const getRowTypeDotClass = (record) => {
  if (record?.isDeleted) return 'dot-deleted'
  const mark = getDisplaySmartMark(record)
  if (mark.includes('未出勤')) return 'dot-absent'
  if (mark.includes('模糊')) return 'dot-blurred'
  if (mark.includes('手写')) return 'dot-handwritten'
  return 'dot-normal'
}

const getAnomalyTagColor = (reason) => {
  const kind = anomalyReasonKind(reason)
  if (kind === 'absent' || kind === 'missing') return 'red'
  if (kind === 'blurred' || kind === 'duplicate') return 'orange'
  if (kind === 'handwriting') return 'blue'
  if (kind === 'deleted') return 'default'
  return 'default'
}

const getAnomalyTagClass = (reason) => {
  if (reason.includes(t('home.statsAbsent'))) return 'tag-red'
  if (reason.includes(t('home.statsBlurred'))) return 'tag-amber'
  if (reason.includes(t('home.statsHandwriting'))) return 'tag-blue'
  return 'tag-default'
}

const getSmartMarkDisplay = (record) => {
  const mark = getDisplaySmartMark(record)
  if (mark.includes('未出勤')) {
    const shift = record?.HORAIRES_DU_TRAVAIL || ''
    return shift ? `未出勤-${shift}` : '未出勤'
  }
  return mark
}

const getEffectiveAnomalies = (record) => {
  const anomalies = Array.isArray(record?.anomalies) ? record.anomalies : []
  return anomalies.filter(reason => reason && !String(reason).includes(t('home.statsNight')) && !String(reason).includes('夜班'))
}

const isRequiredFieldEmpty = (record, fieldKey) => getMissingRequiredFieldKeys(record).includes(fieldKey)

const getRowKey = (record) => record?._rowKey || `${record?.NO || 'row'}-${record?.Date || ''}-${record?.NOM_PRENOM || ''}`

const stripSerialSuffix = (name) => String(name || '').trim().replace(/\s\d{2}$/, '').trim()

const duplicateGroupKey = (record) => [
  String(record?.Pays || '').trim().toUpperCase(),
  String(record?.Entrepot || '').trim().toUpperCase(),
  String(record?.Date || '').trim(),
  String(record?.AGENCE_INTERIMAIRE || '').trim().toUpperCase(),
  String(record?._baseName || '').trim().toUpperCase(),
].join('|')

const isEligibleForDuplicate = (record) => {
  if (!record || record.isDeleted || isAbsentRow(record) || record._duplicateConfirmedUnique) return false
  return !!(String(record?.Date || '').trim() && String(record?._baseName || '').trim())
}

const buildDuplicateMember = (record, sourceTaskId = taskId.value) => ({
  rowKey: record?._rowKey || `${sourceTaskId}-${record?.NO || ''}-${record?.NOM_PRENOM || ''}`,
  sourceTaskId,
  NO: record?.NO,
  displayName: record?.NOM_PRENOM,
  Pays: record?.Pays,
  Entrepot: record?.Entrepot,
  Date: record?.Date,
  AGENCE_INTERIMAIRE: record?.AGENCE_INTERIMAIRE,
  HORAIRES_DU_TRAVAIL: record?.HORAIRES_DU_TRAVAIL,
  ARRIVEE: record?.ARRIVEE,
  DEPAR: record?.DEPAR,
  PAUSE: record?.PAUSE,
  SIGNATURE: record?.SIGNATURE,
  Observations: record?.Observations,
})

const mergeDuplicateMembers = (remoteMembers = [], localMembers = []) => {
  const byKey = new Map()
  remoteMembers.forEach((member) => {
    if (member?.rowKey) {
      byKey.set(member.rowKey, { ...member })
    }
  })
  localMembers.forEach((member) => {
    if (!member?.rowKey) return
    byKey.set(member.rowKey, { ...(byKey.get(member.rowKey) || {}), ...member })
  })
  return [...byKey.values()]
}

const refreshDuplicateDecorations = () => {
  if (duplicateRefreshing.value) return
  duplicateRefreshing.value = true
  try {
    const remoteMetaSnapshot = { ...duplicateMetaMap.value }
    const groups = new Map()
    records.value.forEach((record) => {
      if (!record._rowKey) record._rowKey = `${taskId.value}-${Math.random().toString(36).slice(2, 8)}`
      if (!record._baseName) {
        record._baseName = stripSerialSuffix(record.NOM_PRENOM)
      }
      if (record._nameAutoNumbered && stripSerialSuffix(record.NOM_PRENOM) !== record._baseName) {
        record._baseName = stripSerialSuffix(record.NOM_PRENOM)
      }
      if (!isEligibleForDuplicate(record)) {
        if (record._nameAutoNumbered || record._duplicateConfirmedUnique) {
          record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
        }
        record._nameAutoNumbered = false
        return
      }
      const key = duplicateGroupKey(record)
      const remoteHit = duplicateMetaMap.value[record._rowKey]
      if (!remoteHit) {
        record._nameAutoNumbered = false
        return
      }
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(record)
    })

    const meta = {}
    groups.forEach((members) => {
      members.forEach((record, idx) => {
        const serial = String(idx + 1).padStart(2, '0')
        const targetName = `${record._baseName} ${serial}`.trim()
        if (!record._duplicateConfirmedUnique && (record._nameAutoNumbered || record.NOM_PRENOM === record._baseName || !record.NOM_PRENOM)) {
          record.NOM_PRENOM = targetName
          record._nameAutoNumbered = true
        }
        const localPeers = members
          .filter(m => m._rowKey !== record._rowKey)
          .map(m => `${m.NO || '?'}-${m.NOM_PRENOM || m._baseName || '?'}`)
        const remotePeers = Array.isArray(remoteMetaSnapshot?.[record._rowKey]?.peers)
          ? remoteMetaSnapshot[record._rowKey].peers
          : []
        const peers = [...new Set([...localPeers, ...remotePeers])]
        const remoteMembers = remoteMetaSnapshot[record._rowKey]?.members || []
        const localMembers = members.map((m) => buildDuplicateMember(m, taskId.value))
        meta[record._rowKey] = {
          peers,
          members: mergeDuplicateMembers(remoteMembers, localMembers),
        }
      })
    })
    const mergedMeta = {}
    Object.keys(duplicateMetaMap.value).forEach((k) => {
      mergedMeta[k] = { ...duplicateMetaMap.value[k] }
    })
    Object.keys(meta).forEach((k) => {
      mergedMeta[k] = { ...(mergedMeta[k] || {}), ...meta[k] }
    })
    duplicateMetaMap.value = mergedMeta
    expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter(key => !!mergedMeta[key])
  } finally {
    duplicateRefreshing.value = false
  }
}

const getDuplicateMeta = (record) => duplicateMetaMap.value[record?._rowKey]

const fetchConfirmedDuplicateHints = async () => {
  try {
    const payload = records.value.map((r) => ({
      _rowKey: r._rowKey,
      NO: r.NO,
      Pays: r.Pays,
      Entrepot: r.Entrepot,
      Date: r.Date,
      NOM_PRENOM: r.NOM_PRENOM,
      AGENCE_INTERIMAIRE: r.AGENCE_INTERIMAIRE,
      HORAIRES_DU_TRAVAIL: r.HORAIRES_DU_TRAVAIL,
      ARRIVEE: r.ARRIVEE,
      DEPAR: r.DEPAR,
      PAUSE: r.PAUSE,
      SIGNATURE: r.SIGNATURE,
      Observations: r.Observations,
      isDeleted: r.isDeleted,
      SmartMark: r.SmartMark,
    }))
    const res = await checkTaskDuplicateNames(taskId.value, payload, duplicateScope.value)
    const map = {}
    const duplicates = res?.data?.duplicates || []
    duplicates.forEach((d) => {
      if (!d?.rowKey) return
      const current = records.value.find((p) => p._rowKey === d.rowKey)
      const peers = (d.matches || []).map(m => `${m.NO || '?'}-${m.NOM_PRENOM || '?'}`)
      map[d.rowKey] = {
        peers,
        members: mergeDuplicateMembers(
          (d.matches || []).map((m) => ({
            rowKey: `${m.sourceTaskId}-${m.NO}-${m.NOM_PRENOM}`,
            sourceTaskId: m.sourceTaskId,
            NO: m.NO,
            displayName: m.NOM_PRENOM,
            Pays: m.Pays,
            Entrepot: m.Entrepot,
            Date: m.Date,
            AGENCE_INTERIMAIRE: m.AGENCE_INTERIMAIRE,
            HORAIRES_DU_TRAVAIL: m.HORAIRES_DU_TRAVAIL,
            ARRIVEE: m.ARRIVEE,
            DEPAR: m.DEPAR,
            PAUSE: m.PAUSE,
            SIGNATURE: m.SIGNATURE,
            Observations: m.Observations,
          })),
          current ? [buildDuplicateMember(current, taskId.value)] : [],
        ),
      }
    })
    duplicateMetaMap.value = map
    refreshDuplicateDecorations()
  } catch (error) {
    console.error('加载已确认任务重名提示失败:', error)
  }
}

const handleDuplicateScopeChange = async () => {
  await fetchConfirmedDuplicateHints()
}

const toggleDuplicateExpand = (record) => {
  const key = record?._rowKey
  if (!key || !duplicateMetaMap.value[key]) return
  if (expandedDuplicateRowKeys.value.includes(key)) {
    expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter(k => k !== key)
  } else {
    expandedDuplicateRowKeys.value = [...expandedDuplicateRowKeys.value, key]
  }
}

const handleTableExpand = (expanded, record) => {
  const key = record?._rowKey
  if (!key) return
  if (expanded) {
    if (!expandedDuplicateRowKeys.value.includes(key)) {
      expandedDuplicateRowKeys.value = [...expandedDuplicateRowKeys.value, key]
    }
  } else {
    expandedDuplicateRowKeys.value = expandedDuplicateRowKeys.value.filter(k => k !== key)
  }
}

const confirmNotDuplicate = (record) => {
  record._duplicateConfirmedUnique = true
  record._nameAutoNumbered = false
  record.NOM_PRENOM = record._baseName || stripSerialSuffix(record.NOM_PRENOM)
  refreshDuplicateDecorations()
}

const markNameManuallyEdited = (record) => {
  if (!record) return
  record._baseName = stripSerialSuffix(record.NOM_PRENOM)
  if (record._duplicateConfirmedUnique) {
    record._nameAutoNumbered = false
  }
  refreshDuplicateDecorations()
}

const normalizePauseMinutes = (value) => {
  if (value === null || value === undefined || value === '') return ''
  const normalized = String(value)
    .trim()
    .toLowerCase()
    .replace(',', '.')
    .replace(/\s+/g, '')
    .replace(/minutes?|mins?|mn/g, 'min')
  if (!normalized || normalized === '???' || normalized === '??' || normalized === 'illegible') return ''

  const hourMatch = normalized.match(/^(\d+(?:\.\d+)?)h(\d+(?:\.\d+)?)?(?:min|m)?$/)
  if (hourMatch) {
    const hours = Number(hourMatch[1])
    const minutes = hourMatch[2] ? Number(hourMatch[2]) : 0
    return Number.isNaN(hours) || Number.isNaN(minutes) ? value : Math.round(hours * 60 + minutes)
  }
  const colonMatch = normalized.match(/^(\d{1,2}):(\d{1,2})$/)
  if (colonMatch) return Number(colonMatch[1]) * 60 + Number(colonMatch[2])
  const minuteMatch = normalized.match(/^(\d+(?:\.\d+)?)(?:min|m)?$/)
  if (minuteMatch) {
    const minutes = Number(minuteMatch[1])
    return Number.isNaN(minutes) ? value : Math.round(minutes)
  }
  return value
}

const normalizeRecordPause = (record) => applyMissingPays({
  ...record,
  PAUSE: normalizePauseMinutes(record?.PAUSE),
  PAGE_NUM: record?.PAGE_NUM ?? record?.pageNum ?? '',
}, task.value?.promptCountry || getCachedWorkingCountry())

const formatPauseDisplay = (value) => {
  const minutes = normalizePauseMinutes(value)
  return minutes === '' ? '-' : `${minutes} min`
}

const formatDuplicateCell = (value, type = 'text') => {
  if (value === null || value === undefined || value === '') return '-'
  if (type === 'pause') {
    return formatPauseDisplay(value)
  }
  return String(value)
}

const formatPauseInput = (value) => {
  const minutes = normalizePauseMinutes(value)
  return minutes === '' ? '' : `${minutes} min`
}

const parsePauseInput = (value) => {
  const minutes = normalizePauseMinutes(value)
  return minutes === '' ? '' : minutes
}

const getRecordAnomalyReasons = (record) => {
  if (!record || record.isDeleted) return []
  const mark = getDisplaySmartMark(record)
  const reasons = getEffectiveAnomalies(record).map((r) => translateAnomalyReason(r, t))
  if (markContains(mark, 'blurred')) reasons.push(t('taskEdit.blurredContent'))
  if (markContains(mark, 'handwriting')) reasons.push(t('taskEdit.handwrittenContent'))
  if (markContains(mark, 'absent')) reasons.push(t('taskEdit.absentReason'))
  if (hasRequiredMissing(record)) reasons.push(t('taskEdit.requiredFieldMissingShort'))
  const duplicateMeta = getDuplicateMeta(record)
  if (duplicateMeta?.peers?.length) {
    reasons.push(t('taskEdit.duplicateSuspect', { names: duplicateMeta.peers.join('、') }))
  }
  return [...new Set(reasons)]
}

const getRowClassName = (record, index) => {
  if (!record) return ''
  if (record?.isDeleted) return 'deleted-row'
  if (hasRequiredMissing(record)) return 'incomplete-row'
  const mark = getDisplaySmartMark(record)
  if (markContains(mark, 'absent')) return 'absent-row'
  if (markContains(mark, 'blurred')) return 'blurred-row'
  return ''
}

const getMarkColor = (mark) => {
  if (!mark) return 'default'
  if (markContains(mark, 'absent')) return 'error'
  if (markContains(mark, 'blurred')) return 'warning'
  if (markContains(mark, 'handwriting')) return 'processing'
  if (markContains(mark, 'nightShift')) return 'purple'
  if (markContains(mark, 'normal')) return 'success'
  return 'default'
}

const hasHandwrittenText = (value) => {
  const text = String(value || '').toLowerCase()
  return text.includes('手写')
    || text.includes('handwritten')
    || text.includes('manuscrit')
    || text.includes('manuscrite')
    || text.includes('ecrit main')
    || text.includes('écrit main')
    || text.includes('ecrit a la main')
    || text.includes('écrit à la main')
}

const hasHandwrittenIdentity = (record) => {
  const anomalyText = Array.isArray(record?.anomalies) ? record.anomalies.join(' ') : ''
  return hasHandwrittenText(record?.NO)
    || hasHandwrittenText(record?.NOM_PRENOM)
    || hasHandwrittenText(record?.Mark)
    || hasHandwrittenText(record?.mark)
    || hasHandwrittenText(record?.smartMark)
    || hasHandwrittenText(anomalyText)
}

const getDisplaySmartMark = (record) => {
  const sourceMarks = [record?.SmartMark, record?.Mark, record?.mark, record?.smartMark]
    .map(v => String(v || '').trim())
    .filter(Boolean)
  const raw = [...new Set(sourceMarks.join(';').split(/[;；,，]/).map(v => v.trim()).filter(Boolean))].join(';')
  const hasHandwritten = hasHandwrittenIdentity(record) || raw.includes('手写')
  if (!hasHandwritten || raw.includes('已删除') || raw.includes('未出勤')) {
    return raw || '-'
  }
  if (!raw || raw === '-' || raw === '正常') return '手写'
  if (raw.includes('手写')) return raw
  return `${raw};手写`
}

const anomalyAlerts = computed(() => {
  return records.value
    .map((record, index) => {
      if (record.isDeleted) return null
      const reasons = getRecordAnomalyReasons(record)
      
      if (reasons.length === 0) return null
      return {
        index,
        name: `${record.NO || '?'} - ${record.NOM_PRENOM || '?'}`,
        reasons: [...new Set(reasons)]
      }
    })
    .filter(Boolean)
})

const handleSubmit = async () => {
  const nonDeletedRecords = records.value.filter(r => !r.isDeleted).map(normalizeRecordPause)
  
  const incompleteRecords = nonDeletedRecords.filter((r) => hasRequiredMissing(r))
  
  if (incompleteRecords.length > 0) {
    const details = incompleteRecords.map((r) => {
      const missing = getMissingRequiredFieldKeys(r).map((key) => t(REQUIRED_FIELD_I18N_KEYS[key]))
      return `${t('taskEdit.missingField', { line: records.value.indexOf(r) + 1, id: r.NO || '?' })}: ${missing.join(', ')}`
    })
    message.error(t('taskEdit.requiredFieldsMissing', { count: incompleteRecords.length }))
    console.warn('Missing fields details:', details)
    return
  }
  
  if (nonDeletedRecords.length === 0) {
    message.warning(t('taskEdit.noValidRecords'))
    return
  }
  
  const anomalyRecords = records.value.filter(r => {
    if (r.isDeleted) return false
    return getRecordAnomalyReasons(r).length > 0
  })
  
  const allAnomalyReasons = []
  anomalyRecords.forEach(r => {
    const reasons = getRecordAnomalyReasons(r)
    reasons.forEach(reason => {
      if (!allAnomalyReasons.includes(reason)) {
        allAnomalyReasons.push(reason)
      }
    })
  })
  
  const anomalySummary = JSON.stringify({
    totalRecords: records.value.length,
    validRecords: nonDeletedRecords.length,
    deletedRecords: records.value.filter(r => r.isDeleted).length,
    anomalyRecords: anomalyRecords.length,
    anomalyReasons: allAnomalyReasons,
    riskLevel: anomalyRecords.length > 0 ? 'high' : 'none'
  })
  
  submitting.value = true
  try {
    await confirmTask(taskId.value, { 
      data: nonDeletedRecords,
      anomalySummary: anomalySummary
    })
    message.success(t('taskEdit.submitSuccess'))
    await loadTask(true)
    startSyncPoll()
  } catch (error) {
    message.error(t('taskEdit.submitFailed'))
    console.error(error)
  } finally {
    submitting.value = false
  }
}

let isComponentMounted = true

onMounted(async () => {
  isComponentMounted = true
  if (!authStore.userInfo?.permissions) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      /* ignore */
    }
  }
  loadTask()
})

onUnmounted(() => {
  isComponentMounted = false
  clearSyncPoll()
})

watch(taskId, () => {
  if (isComponentMounted) {
    loadTask()
  }
})

watch(records, () => {
  if (!duplicateRefreshing.value) {
    refreshDuplicateDecorations()
  }
}, { deep: true })
</script>

<style lang="scss" scoped>
.task-edit-container {
  padding: 0;

  .record-count {
    font-size: 13px;
    color: $primary;
    background: $primary-light;
    padding: 4px 12px;
    border-radius: $radius-xl;
    border: 1px solid $border-accent;
  }

  .edit-card {
    border-radius: $radius-lg;
    box-shadow: $shadow-xs;
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid $border;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .header-right {
      .record-count {
        font-size: 13px;
        color: $text-tertiary;
        background: $primary-light;
        color: $primary;
        padding: 4px 12px;
        border-radius: $radius-xl;
        border: 1px solid $border-accent;
      }
    }

    .page-title {
      margin: 0;
      font-size: $font-size-2xl;
      font-weight: $font-weight-extrabold;
      color: $text-strong;
      letter-spacing: -0.02em;
    }

    .task-id-tag {
      font-size: 13px;
    }
    
    .status-tag {
      font-size: 13px;
    }
    
    .header-right {
      display: flex;
      align-items: center;
      gap: 16px;
      
      .reupload-btn {
        height: 36px;
        border-radius: $radius-sm;
        font-weight: $font-weight-medium;
      }
    }
  }

  .sync-alert {
    margin-bottom: 16px;
  }

  .edit-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 20px;
    }
  }

  .cell-text {
    font-size: 13px;
    color: $text-primary;
  }

  .mark-tag {
    font-size: $font-size-sm;
    border-radius: 4px;
  }

  .anomaly-hint {
    margin-bottom: 20px;
    padding: 14px 18px;
    background: $warning-light;
    border-radius: 10px;
    border-left: 4px solid $warning;

    .anomaly-summary {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .anomaly-icon {
      color: $warning-dark;
      font-size: 18px;
    }

    .anomaly-text {
      font-size: $font-size-md;
      color: $accent-dark;
      font-weight: $font-weight-semibold;
    }

    .anomaly-toggle {
      font-size: 13px;
      color: $primary;
      padding: 0;
      height: auto;
    }

    .anomaly-detail-list {
      margin-top: 14px;
      padding-top: 14px;
      border-top: 1px solid rgba(60, 60, 67, 0.12);

      .anomaly-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 8px 0;
        font-size: 13px;

        .anomaly-index {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 22px;
          height: 22px;
          border-radius: 50%;
          background: rgba($primary, 0.12);
          color: $primary;
          font-size: $font-size-sm;
          font-weight: $font-weight-bold;
          flex-shrink: 0;
        }

        .anomaly-name {
          color: $text-primary;
          font-weight: $font-weight-semibold;
          min-width: 120px;
        }

        .anomaly-reasons {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
          align-items: center;
          min-width: 0;
          flex: 1;
        }
      }
    }
  }

  .duplicate-scope-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
  }

  .duplicate-scope-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    font-weight: $font-weight-semibold;
  }

  .edit-table {
    .name-cell {
      min-width: 0;
    }

    .name-cell :deep(.ant-input) {
      white-space: normal;
    }

    .name-cell .cell-text {
      white-space: normal;
      word-break: break-word;
      line-height: 1.35;
    }

    .name-cell {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .mark-tags-cell {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: center;
    }

    .calibration-tag {
      cursor: pointer;
    }

    .duplicate-tools {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    .duplicate-link {
      padding: 0;
      height: auto;
      font-size: $font-size-sm;
    }

    .inline-anomaly-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: center;

      :deep(.ant-tag) {
        margin-right: 0;
        line-height: 20px;
      }
    }

    .cell-muted {
      color: $text-tertiary;
    }

    :deep(.ant-table) {
      border-radius: 10px;
      border: 1px solid $border;
    }

    :deep(.ant-table-container) {
      overflow-x: auto;
    }

    :deep(.ant-table-expanded-row > td) {
      overflow: visible;
      padding: 12px 16px !important;
      background: transparent;
    }

    :deep(.ant-table-tbody > tr > td) {
      padding: 10px 10px;
      font-size: 13px;
      vertical-align: middle;
      border-bottom: 1px solid $border;
    }

    :deep(.ant-table-tbody > tr:hover > td) {
      background: $bg-muted !important;
    }

    :deep(.ant-input) {
      font-size: 13px;
      padding: 4px 8px;
      border-radius: $radius-sm;
      background: transparent;
      transition: all $duration-base $ease-smooth;

      &:focus, &:hover {
        background: $bg-surface;
        box-shadow: 0 0 0 2px rgba($primary, 0.15);
      }
    }

    :deep(.ant-input-number) {
      font-size: 13px;

      .ant-input-number-input {
        padding: 4px 8px;
      }
    }

    :deep(.ant-input-number-focused) {
      box-shadow: 0 0 0 2px rgba($primary, 0.15);
    }
  }

  .row-type-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-right: 6px;
    vertical-align: middle;

    &.dot-normal { background-color: $success; }
    &.dot-blurred { background-color: $warning; }
    &.dot-handwritten { background-color: $primary; }
    &.dot-absent { background-color: $danger; }
    &.dot-deleted { background-color: $text-tertiary; }
  }

  .row-type-label {
    font-size: $font-size-sm;
    color: $text-secondary;
    vertical-align: middle;
    font-weight: $font-weight-medium;
  }

  .action-btn {
    padding: 4px 8px;
  }

  :deep(.required-empty) {
    background: $bg-surface !important;
    border-color: $danger !important;
    border-radius: $radius-sm;

    &:hover {
      border-color: $danger !important;
    }

    input {
      border-color: transparent !important;
    }
  }

  .task-image-files {
    margin-bottom: $space-4;
    padding: $space-3 $space-4;
    border-radius: $radius-lg;
    border: 1px solid $border-light;
    background: $bg-muted;

    &__head {
      display: flex;
      align-items: center;
      gap: $space-2;
      margin-bottom: $space-2;
      color: $text-secondary;
      font-size: $font-size-sm;
    }

    &__title {
      font-weight: $font-weight-semibold;
      color: $text-strong;
      font-size: $font-size-base;

      em {
        font-style: normal;
        font-weight: $font-weight-normal;
        color: $text-secondary;
        margin-left: 4px;
      }
    }

    &__list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: $space-2;
    }

    &__item {
      margin: 0;
    }

    &__link {
      width: 100%;
      display: flex;
      align-items: center;
      gap: $space-3;
      padding: $space-3 $space-4;
      border: 1px solid $border;
      border-radius: $radius-md;
      background: $bg-surface;
      cursor: pointer;
      text-align: left;
      transition: border-color $duration-fast, box-shadow $duration-fast;

      &:hover {
        border-color: $primary;
        box-shadow: 0 2px 8px rgba($primary, 0.1);
      }
    }

    &__icon {
      font-size: 18px;
      color: $primary;
      flex-shrink: 0;
    }

    &__name {
      flex: 1;
      min-width: 0;
      font-size: $font-size-base;
      font-weight: $font-weight-medium;
      color: $text-strong;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &__view {
      font-size: 16px;
      color: $text-tertiary;
      flex-shrink: 0;
    }
  }

  .action-bar {
    margin-top: 28px;
    padding-top: 24px;
    border-top: 1px solid $border;
    display: flex;
    justify-content: center;
    gap: 20px;
  }

  .duplicate-expanded {
    padding: 10px 6px;
    background: $warning-light;
    border: 1px dashed $warning;
    border-radius: $radius-md;
  }

  .duplicate-expanded-title {
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
    color: $warning-dark;
    margin-bottom: 8px;
  }

  .duplicate-expanded-list {
    overflow-x: auto;
    max-width: 100%;
    -webkit-overflow-scrolling: touch;
  }

  .duplicate-expanded-table {
    width: max-content;
    min-width: 100%;
    border-collapse: separate;
    border-spacing: 0 4px;
    font-size: $font-size-sm;

    th, td {
      border: none;
      padding: 6px 10px;
      vertical-align: top;
      text-align: left;
      white-space: normal;
      word-break: break-word;
      line-height: 1.35;
      color: $text-primary;
    }

    th {
      background: transparent;
      font-weight: $font-weight-semibold;
      color: $text-secondary;
      padding-bottom: 4px;
    }

    tbody tr {
      background: $bg-warm;
    }

    tbody td:first-child {
      border-top-left-radius: 6px;
      border-bottom-left-radius: 6px;
    }

    tbody td:last-child {
      border-top-right-radius: 6px;
      border-bottom-right-radius: 6px;
    }
  }
}
</style>

<style lang="scss">
.task-edit-container {
  .edit-table {
    .ant-table-tbody > tr.deleted-row,
    .ant-table-tbody > tr.absent-row {
      td {
        background-color: $danger-light;
      }
    }
    
    .ant-table-tbody > tr.deleted-row:hover > td,
    .ant-table-tbody > tr.absent-row:hover > td {
      background-color: $danger-light !important;
    }
    
    .ant-table-tbody > tr.incomplete-row {
      td {
        background-color: $warning-light;
      }
    }
    
    .ant-table-tbody > tr.incomplete-row:hover > td {
      background-color: $warning-light !important;
    }
    
    .ant-table-tbody > tr.blurred-row {
      td {
        background-color: $warning-light;
      }
    }
    
    .ant-table-tbody > tr.blurred-row:hover > td {
      background-color: $warning-light !important;
    }
  }
}
</style>
