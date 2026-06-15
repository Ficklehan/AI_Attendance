<template>
  <div class="task-edit-container page-inner" :class="{ 'has-sticky-submit': canShowSubmitBar }">
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
          <div class="table-toolbar">
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
          <a-alert
            v-if="requiredMissingCount > 0 && !isConfirmedTask"
            type="warning"
            show-icon
            class="required-validation-banner"
          >
            <template #message>
              <span>{{ $t('taskEdit.requiredValidationBanner', { count: requiredMissingCount }) }}</span>
              <a-button type="link" size="small" class="required-validation-detail-btn" @click="showRequiredValidationDetail">
                {{ $t('taskEdit.requiredValidationViewDetail') }}
              </a-button>
            </template>
          </a-alert>
          <div v-if="anomalyAlertCount > 0" class="anomaly-hint">
            <div class="anomaly-summary">
              <ExclamationCircleOutlined class="anomaly-icon" />
              <span class="anomaly-text">{{ $t('home.anomalyAlert', { count: anomalyAlertCount }) }}</span>
              <a-button type="link" size="small" @click="toggleAnomalyDetail" class="anomaly-toggle">
                {{ showAnomalyDetail ? $t('home.collapse') : $t('home.expand') }}
              </a-button>
            </div>
            <div v-if="showAnomalyDetail" class="anomaly-detail-list">
              <div v-for="(alert, idx) in anomalyAlertsDetail" :key="idx" class="anomaly-item">
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
              <div v-if="anomalyAlertsOverflow > 0" class="anomaly-more-hint">
                {{ $t('taskEdit.anomalyAlertMore', { count: anomalyAlertsOverflow }) }}
              </div>
            </div>
          </div>

          <a-table 
            :columns="columns" 
            :data-source="visibleTableRecords" 
            :pagination="false"
            :scroll="{ x: scrollX }"
            :row-key="getRowKey"
            :row-class-name="getRowClassName"
            :expanded-row-keys="expandedDuplicateRowKeys"
            :expand-icon="hiddenExpandIcon"
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
                    :active="isHeaderFilterActive(column.searchField)"
                    @openChange="(open) => handleHeaderPopoverOpen(column, open)"
                    @reset="clearHeaderFilter"
                    @apply="applyHeaderFilter"
                  >
                    <template #field>
                      <FieldFilterControl
                        v-model:model-value="headerFilterDraft"
                        :filter-type="resolveColumnFilterType(column)"
                        :options="resolveColumnFilterOptions(column, t)"
                        class="table-header-filter-panel__input"
                        @submit="applyHeaderFilter"
                      />
                    </template>
                  </TableHeaderFilter>
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
              <template v-if="column.key === 'serialNo'">
                <span class="cell-text cell-serial">{{ globalRowSerial(index) }}</span>
              </template>
              <template v-if="column.key === 'anomalyReasons'">
                <div v-if="getRowAnomalyReasons(record).length > 0" class="inline-anomaly-tags">
                  <TruncatedTag
                    v-for="(reason, reasonIdx) in getRowAnomalyReasons(record)"
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
                <span v-else class="cell-text">{{ displayFieldValue(record.PAGE_NUM || record.pageNum) }}</span>
              </template>
              <template v-if="column.key === 'NO'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.NO" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ displayFieldValue(record.NO) }}</span>
              </template>
              <template v-if="column.key === 'Pays'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Pays" size="small" :class="requiredInputClass(record, 'Pays')" :bordered="false" />
                <span v-else :class="requiredTextClass(record, 'Pays')">{{ displayFieldValue(record.Pays) }}</span>
              </template>
              <template v-if="column.key === 'Entrepot'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Entrepot" size="small" :class="requiredInputClass(record, 'Entrepot')" :bordered="false" />
                <span v-else :class="requiredTextClass(record, 'Entrepot')">{{ displayFieldValue(record.Entrepot) }}</span>
              </template>
              <template v-if="column.key === 'NOM_PRENOM'">
                <div class="name-cell">
                  <a-input
                    v-if="isRecordEditable(record)"
                    v-model:value="record.NOM_PRENOM"
                    size="small"
                    :class="requiredInputClass(record, 'NOM_PRENOM')"
                    :bordered="false"
                    @change="markNameManuallyEdited(record)"
                  />
                  <span v-else :class="requiredTextClass(record, 'NOM_PRENOM')">{{ displayFieldValue(record.NOM_PRENOM) }}</span>
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
                <a-input v-if="isRecordEditable(record)" v-model:value="record.AGENCE_INTERIMAIRE" size="small" :class="requiredInputClass(record, 'AGENCE_INTERIMAIRE')" :bordered="false" />
                <span v-else :class="requiredTextClass(record, 'AGENCE_INTERIMAIRE')">{{ displayFieldValue(record.AGENCE_INTERIMAIRE) }}</span>
              </template>
              <template v-if="column.key === 'HORAIRES_DU_TRAVAIL'">
                <a-input
                  v-if="isRecordEditable(record)"
                  v-model:value="record.HORAIRES_DU_TRAVAIL"
                  size="small"
                  :class="requiredInputClass(record, 'HORAIRES_DU_TRAVAIL')"
                  :bordered="false"
                  @change="() => refreshRecordNightShiftMark(record)"
                />
                <span v-else :class="requiredTextClass(record, 'HORAIRES_DU_TRAVAIL')">{{ displayFieldValue(record.HORAIRES_DU_TRAVAIL) }}</span>
              </template>
              <template v-if="column.key === 'Date'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Date" size="small" :class="requiredInputClass(record, 'Date')" :bordered="false" />
                <span v-else :class="requiredTextClass(record, 'Date')">{{ displayFieldValue(record.Date) }}</span>
              </template>
              <template v-if="column.key === 'ARRIVEE'">
                <a-input
                  v-if="isRecordEditable(record)"
                  v-model:value="record.ARRIVEE"
                  size="small"
                  :class="requiredInputClass(record, 'ARRIVEE')"
                  :bordered="false"
                  @change="() => refreshRecordNightShiftMark(record)"
                />
                <span v-else :class="requiredTextClass(record, 'ARRIVEE')">{{ displayFieldValue(record.ARRIVEE) }}</span>
              </template>
              <template v-if="column.key === 'DEPAR'">
                <a-input
                  v-if="isRecordEditable(record)"
                  v-model:value="record.DEPAR"
                  size="small"
                  :class="requiredInputClass(record, 'DEPAR')"
                  :bordered="false"
                  @change="() => refreshRecordNightShiftMark(record)"
                />
                <span v-else :class="requiredTextClass(record, 'DEPAR')">{{ displayFieldValue(record.DEPAR) }}</span>
              </template>
              <template v-if="column.key === 'PAUSE'">
                <a-input-number v-if="isRecordEditable(record)" v-model:value="record.PAUSE" size="small" :class="requiredInputClass(record, 'PAUSE')" :bordered="false" :controls="false" :min="0" :precision="0" style="width: 100%" @blur="() => normalizeRecordPauseOnBlur(record)" />
                <span v-else :class="requiredTextClass(record, 'PAUSE')">{{ formatPauseDisplay(record.PAUSE) }}</span>
              </template>
              <template v-if="column.key === 'SIGNATURE'">
                <a-select
                  v-if="isRecordEditable(record)"
                  v-model:value="record.SIGNATURE"
                  size="small"
                  :bordered="false"
                  class="signature-mark-select"
                  :placeholder="$t('taskEdit.signature')"
                  allow-clear
                >
                  <a-select-option value="未签字">{{ $t('recognition.marks.unsigned') }}</a-select-option>
                  <a-select-option value="已签字">{{ $t('recognition.marks.signed') }}</a-select-option>
                </a-select>
                <a-tag
                  v-else
                  :color="getSignatureMarkColor(getDisplaySignature(record.SIGNATURE, record))"
                  class="signature-mark-tag"
                >
                  {{ translateSignatureMark(getDisplaySignature(record.SIGNATURE, record), t) }}
                </a-tag>
              </template>
              <template v-if="column.key === 'Observations'">
                <a-input v-if="isRecordEditable(record)" v-model:value="record.Observations" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ displayFieldValue(record.Observations) }}</span>
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
                <div class="table-action-cell table-action-cell--icons-mixed">
                  <span class="table-action-cell__slot">
                    <a-button
                      v-if="isConfirmedTask && canCalibrateRecord && !record.isDeleted"
                      type="link"
                      size="small"
                      @click="openCalibration(record)"
                    >
                      {{ $t('calibration.action') }}
                    </a-button>
                  </span>
                  <span class="table-action-cell__slot">
                    <a-tooltip
                      v-if="!(isConfirmedTask && canCalibrateRecord && !record.isDeleted)"
                      :title="(record?.isDeleted || isAbsentRow(record)) ? $t('taskEdit.restore') : $t('common.delete')"
                    >
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
                  </span>
                </div>
              </template>
            </template>
          </a-table>

          <div
            v-if="hasMoreTableRows"
            ref="tableLoadMoreSentinel"
            class="table-scroll-load-more"
          >
            <a-spin v-if="loadingMoreTableRows" size="small" />
            <span v-else class="table-scroll-load-more__text">
              {{ $t('taskEdit.scrollLoadMore', { loaded: visibleTableRecords.length, total: tableRecords.length }) }}
            </span>
          </div>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <Teleport to="body">
      <div v-if="canShowSubmitBar" class="task-edit-submit-bar">
        <div class="task-edit-submit-bar__inner">
          <a-button
            type="primary"
            :loading="submitting"
            @click="handleSubmit"
            size="large"
          >
            {{ $t('taskEdit.submitConfirm') }}
          </a-button>
          <a-button @click="$router.back()" size="large">{{ $t('common.cancel') }}</a-button>
        </div>
      </div>
    </Teleport>
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
import { ref, computed, shallowRef, onMounted, onUnmounted, watch, h, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal as aModal } from 'ant-design-vue'
import { DeleteOutlined, UndoOutlined, ExclamationCircleOutlined, FileImageOutlined, EyeOutlined, UploadOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { getTaskDetail, confirmTask, deleteTask, retryFeishuSync, calibrateTaskRecord } from '@/api/task'
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
  translateSmartMark,
  computeSignatureMark,
  getDisplaySignature,
  translateSignatureMark,
  getSignatureMarkColor,
  refreshNightShiftInSmartMark,
  getRawSmartMark,
} from '@/utils/recognitionLabels'
import FieldFilterControl from '@/components/FieldFilterControl.vue'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import {
  emptyFilterValue,
  isFilterActive,
  matchRecordByFilter,
  resolveColumnFilterOptions,
  resolveColumnFilterType,
  serializeFilterValue,
} from '@/utils/fieldFilterValue'
import { buildRecognitionTableColumns } from '@/utils/recognitionTableColumns'
import {
  hasManualCalibration,
  parseCalibrationHistory,
  formatHistoryChanges,
} from '@/utils/calibrationHistory'
import { FIELD_LABEL_KEYS } from '@/constants/calibratableFields'
import { API_BASE_PATH } from '@/constants/apiBase'
import { getConfirmValidationConfig as fetchConfirmValidationConfig } from '@/api/config'
import { displayFieldValue, isPlaceholderValue, sanitizeFieldValue, sanitizeRecordPlaceholders } from '@/utils/fieldPlaceholder'
import { startAdaptivePoll } from '@/utils/adaptivePoll'
import { isAbsentRow } from '@/utils/recordDisplay'
import { useTaskEditDuplicates } from '@/composables/useTaskEditDuplicates'
import { useTaskEditConfirmValidation } from '@/composables/useTaskEditConfirmValidation'
import { useTaskEditRecordDisplay } from '@/composables/useTaskEditRecordDisplay'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const taskId = computed(() => route.params.taskId)
const activeTab = ref('edit')
const TABLE_SCROLL_BATCH = 50
const visibleRowCount = ref(TABLE_SCROLL_BATCH)
const loadingMoreTableRows = ref(false)
const tableLoadMoreSentinel = ref(null)
const columnsLocked = ref(false)
const lockedSizedColumns = shallowRef([])
let tableLoadMoreObserver = null
const loading = ref(false)
const submitting = ref(false)
const retryingSync = ref(false)
const deleting = ref(false)
let stopSyncPoll = null
const records = ref([])

/** ant-design-vue Table：expandIcon 返回 null 会在路由卸载时触发 vnode 为 null 的崩溃 */
const hiddenExpandIcon = () =>
  h('span', { class: 'task-edit-expand-icon-placeholder', style: { display: 'none' }, 'aria-hidden': 'true' })

const {
  expandedDuplicateRowKeys,
  duplicateRefreshing,
  duplicateScope,
  getDuplicateMeta,
  refreshDuplicateDecorations,
  fetchConfirmedDuplicateHints,
  handleDuplicateScopeChange,
  toggleDuplicateExpand,
  handleTableExpand,
  confirmNotDuplicate,
  markNameManuallyEdited,
} = useTaskEditDuplicates(taskId, records)

const {
  confirmRequiredFields,
  applyConfirmValidationConfig,
  isConfiguredRequiredField,
  isRequiredFieldEmpty,
  requiredInputClass,
  requiredTextClass,
  validateBeforeConfirm,
  collectConfirmValidationIssues,
  showConfirmValidationModal,
} = useTaskEditConfirmValidation()

const {
  statItems,
  getRecordMarkTags,
  getDisplaySmartMark,
  getSmartMarkDisplay,
  getRecordAnomalyReasons,
  getRowClassName,
  getMarkColor,
  getRowTypeLabel,
  getRowTypeDotClass,
  getAnomalyTagColor,
  getAnomalyTagClass,
  countAnomalyRecords,
  buildAnomalyAlertsSlice,
  clearRowCache,
} = useTaskEditRecordDisplay(records, getDuplicateMeta, { isAbsentRow, hasManualCalibration })

const getRowAnomalyReasons = (record) => getRecordAnomalyReasons(record)

const rawData = ref('')
const showAnomalyDetail = ref(false)
const ANOMALY_DETAIL_LIMIT = 20
const VALIDATION_BANNER_DEBOUNCE_MS = 450
const previewVisible = ref(false)
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)
const task = ref(null)
const canDeleteTask = computed(() => task.value?.status !== 'confirmed')
const isConfirmedTask = computed(() => task.value?.status === 'confirmed')
const canShowSubmitBar = computed(() => task.value?.status === 'processed')

const requiredMissingCount = ref(0)
let requiredValidationDebounceTimer = null
const scheduleRequiredMissingCountUpdate = (immediate = false) => {
  if (requiredValidationDebounceTimer) {
    window.clearTimeout(requiredValidationDebounceTimer)
    requiredValidationDebounceTimer = null
  }
  if (immediate) {
    requiredMissingCount.value = collectConfirmValidationIssues(records.value).length
    return
  }
  requiredValidationDebounceTimer = window.setTimeout(() => {
    requiredValidationDebounceTimer = null
    requiredMissingCount.value = collectConfirmValidationIssues(records.value).length
  }, VALIDATION_BANNER_DEBOUNCE_MS)
}

const anomalyAlertCount = ref(0)
let anomalyCountDebounceTimer = null
const scheduleAnomalyCountUpdate = (immediate = false) => {
  if (anomalyCountDebounceTimer) {
    window.clearTimeout(anomalyCountDebounceTimer)
    anomalyCountDebounceTimer = null
  }
  if (immediate) {
    anomalyAlertCount.value = countAnomalyRecords(records.value)
    return
  }
  anomalyCountDebounceTimer = window.setTimeout(() => {
    anomalyCountDebounceTimer = null
    anomalyAlertCount.value = countAnomalyRecords(records.value)
  }, VALIDATION_BANNER_DEBOUNCE_MS)
}

const anomalyAlertsDetail = computed(() => {
  if (!showAnomalyDetail.value) return []
  return buildAnomalyAlertsSlice(records.value, ANOMALY_DETAIL_LIMIT)
})

const anomalyAlertsOverflow = computed(() => {
  if (!showAnomalyDetail.value) return 0
  return Math.max(0, anomalyAlertCount.value - anomalyAlertsDetail.value.length)
})

const toggleAnomalyDetail = () => {
  const next = !showAnomalyDetail.value
  showAnomalyDetail.value = next
  if (next) {
    scheduleAnomalyCountUpdate(true)
  }
}

const showRequiredValidationDetail = () => {
  const issues = collectConfirmValidationIssues(records.value)
  if (issues.length > 0) {
    showConfirmValidationModal(issues)
  }
}
const canCalibrateRecord = computed(
  () => authStore.userInfo?.permissions?.recordCalibrate === true
)
const isRecordEditable = (record) =>
  !isConfirmedTask.value && !record?.isDeleted && !isAbsentRow(record)

const calibrationVisible = ref(false)
const calibrationRecord = ref(null)
const calibrationSubmitting = ref(false)
const activeHeaderFilterField = ref('')
const headerFilterDraft = ref('')
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
  const fieldKey = column?.key
  if (fieldKey && isConfiguredRequiredField(fieldKey) && isRequiredFieldEmpty(record, fieldKey)) {
    return mergeCellProps({ class: 'required-field-cell' }, column)
  }
  if ((record?.SmartMark || '').includes('模糊')) {
    return mergeCellProps({ style: { backgroundColor: '#FFF9EC' } }, column)
  }
  return mergeCellProps({}, column)
}

const baseColumns = computed(() => buildRecognitionTableColumns(t, {
  requiredFieldKeys: confirmRequiredFields.value,
  cellStyle,
  includeWorkHours: true,
  searchFields: true,
  fixedAction: true,
  actionColumnWidth: 88,
}))
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })

const filteredRecords = computed(() => {
  const active = Object.entries(headerFilters.value).filter(([, v]) => {
    if (Array.isArray(v)) return v.length > 0
    if (v && typeof v === 'object') return Boolean(v.from?.trim() || v.to?.trim())
    return String(v || '').trim() !== ''
  })
  if (!active.length) return records.value
  return records.value.filter((row) => active.every(([field, value]) => {
    const column = sortedColumns.value.find((c) => c.searchField === field)
    const filterType = resolveColumnFilterType(column || { searchField: field })
    const keyword = serializeFilterValue(filterType, value)
    return matchRecordByFilter(filterType, field, keyword, row)
  }))
})

const tableRecords = computed(() => sortRows(filteredRecords.value))

const visibleTableRecords = computed(() => tableRecords.value.slice(0, visibleRowCount.value))

const hasMoreTableRows = computed(() => visibleRowCount.value < tableRecords.value.length)

const resetVisibleTableRows = () => {
  visibleRowCount.value = TABLE_SCROLL_BATCH
}

const loadMoreTableRows = () => {
  if (!hasMoreTableRows.value || loadingMoreTableRows.value) return
  loadingMoreTableRows.value = true
  window.requestAnimationFrame(() => {
    visibleRowCount.value = Math.min(
      visibleRowCount.value + TABLE_SCROLL_BATCH,
      tableRecords.value.length,
    )
    loadingMoreTableRows.value = false
    nextTick(() => bindTableLoadMoreObserver())
  })
}

const bindTableLoadMoreObserver = () => {
  if (!tableLoadMoreObserver) return
  tableLoadMoreObserver.disconnect()
  const el = tableLoadMoreSentinel.value
  if (el && hasMoreTableRows.value) {
    tableLoadMoreObserver.observe(el)
  }
}

const globalRowSerial = (index) => index + 1

const resetTableColumnsLock = () => {
  columnsLocked.value = false
  lockedSizedColumns.value = []
}

const { columns: sizedColumns, scrollX } = useAutoSizedColumns(sortedColumns, tableRecords, {
  actionWidth: isConfirmedTask.value && canCalibrateRecord.value ? 88 : 50,
  getCellSample: (col, record) => {
    if (col.key === 'workHours') return calculateWorkHours(record)
    if (col.key === 'anomalyReasons') return getRecordAnomalyReasons(record).join(', ')
    if (col.key === 'PAUSE') return formatPauseDisplay(record.PAUSE)
    return undefined
  },
})

watch(
  () => sizedColumns.value,
  (cols) => {
    if (columnsLocked.value || !cols?.length || !records.value.length) return
    lockedSizedColumns.value = cols.map((col) => ({ ...col }))
    columnsLocked.value = true
  },
  { immediate: true },
)

const effectiveSizedColumns = computed(() => (
  columnsLocked.value && lockedSizedColumns.value.length
    ? lockedSizedColumns.value
    : sizedColumns.value
))

const {
  frozenColumns: columns,
  hiddenKeys,
  frozenKeys,
  configurableColumns,
  setHiddenKeys,
  setFrozenKeys,
  showAllColumns,
  clearFrozenKeys,
} = useColumnFreeze('task-edit', effectiveSizedColumns, { defaultFrozen: ['serialNo', 'PAGE_NUM', 'NO'] })

const isHeaderFilterActive = (field) => {
  const value = headerFilters.value[field]
  const column = columns.value.find((c) => c.searchField === field)
  const filterType = resolveColumnFilterType(column || { searchField: field })
  return isFilterActive(filterType, value)
}

const handleHeaderPopoverOpen = (column, open) => {
  const field = column?.searchField
  if (!field) return
  const filterType = resolveColumnFilterType(column)
  if (open) {
    activeHeaderFilterField.value = field
    const stored = headerFilters.value[field]
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
  const column = columns.value.find((c) => c.searchField === activeHeaderFilterField.value)
  const filterType = resolveColumnFilterType(column || { searchField: activeHeaderFilterField.value })
  headerFilters.value = {
    ...headerFilters.value,
    [activeHeaderFilterField.value]: isFilterActive(filterType, headerFilterDraft.value)
      ? headerFilterDraft.value
      : emptyFilterValue(filterType),
  }
  activeHeaderFilterField.value = ''
}

const clearHeaderFilter = () => {
  if (!activeHeaderFilterField.value) return
  const column = columns.value.find((c) => c.searchField === activeHeaderFilterField.value)
  const filterType = resolveColumnFilterType(column || { searchField: activeHeaderFilterField.value })
  headerFilters.value = {
    ...headerFilters.value,
    [activeHeaderFilterField.value]: emptyFilterValue(filterType),
  }
  headerFilterDraft.value = emptyFilterValue(filterType)
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
      const updated = res.data.record
      if (updated.SmartMark) {
        updated.Mark = updated.SmartMark
      }
      applyCalibratedRecord(updated)
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
    resetVisibleTableRows()
    resetTableColumnsLock()
    clearRowCache()
    showAnomalyDetail.value = false

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
      records.value = parsedRecords.map((record, idx) => {
        const normalized = sanitizeRecordPlaceholders(normalizeRecordPause({
          ...record,
          isDeleted: record.isDeleted || false,
          _rowKey: record._rowKey || `${taskId.value}-${idx}-${Date.now()}`,
          _baseName: String(sanitizeFieldValue(record.NOM_PRENOM) || '').trim(),
          _nameAutoNumbered: false,
          _duplicateConfirmedUnique: false
        }))
        const signatureMark = computeSignatureMark(normalized)
        normalized.SIGNATURE = signatureMark
        normalized.CHECKER = signatureMark
        return normalized
      })
      refreshDuplicateDecorations()
      await fetchConfirmedDuplicateHints()
      scheduleRequiredMissingCountUpdate(true)
      scheduleAnomalyCountUpdate(true)
    }
    rawData.value = task.value.aiRawOutput || ''
    
    previewImagesList.value = await resolveTaskImageUrls(task.value.imageUrls, task.value.fileKey)
  } catch (error) {
    const msg = String(error?.message || '')
    if (msg.includes(t('errors.taskNotFound')) || /任务不存在|任务已删除|Task not found/i.test(msg)) {
      message.warning(t('notification.taskDeleted'))
      router.replace('/tasks')
      return
    }
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

const getRowKey = (record) => record?._rowKey || `${record?.NO || 'row'}-${record?.Date || ''}-${record?.NOM_PRENOM || ''}`

const normalizePauseMinutes = (value) => {
  if (value === null || value === undefined || value === '') return ''
  const normalized = String(value)
    .trim()
    .toLowerCase()
    .replace(',', '.')
    .replace(/\s+/g, '')
    .replace(/minutes?|mins?|mn/g, 'min')
  if (isPlaceholderValue(normalized)) return ''

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
  if (type === 'pause') {
    return formatPauseDisplay(value)
  }
  return displayFieldValue(value)
}

const normalizeRecordPauseOnBlur = (record) => {
  if (!record) return
  const minutes = normalizePauseMinutes(record.PAUSE)
  record.PAUSE = minutes === '' ? null : Number(minutes)
}

const refreshRecordNightShiftMark = (record) => {
  if (!record || record.isDeleted) return
  const refreshed = refreshNightShiftInSmartMark(getRawSmartMark(record), record)
  record.SmartMark = refreshed
  if (record.Mark != null && record.Mark !== '') {
    record.Mark = refreshed
  }
}

const handleSubmit = async () => {
  const preparedRecords = records.value.map((record) => {
    const normalized = sanitizeRecordPlaceholders(normalizeRecordPause(record))
    refreshRecordNightShiftMark(normalized)
    return normalized
  })
  if (!validateBeforeConfirm(preparedRecords)) return

  const nonDeletedRecords = preparedRecords.filter(r => !r.isDeleted)

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

watch([tableLoadMoreSentinel, hasMoreTableRows], () => {
  nextTick(() => bindTableLoadMoreObserver())
})

onMounted(async () => {
  isComponentMounted = true
  if (typeof IntersectionObserver !== 'undefined') {
    tableLoadMoreObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          loadMoreTableRows()
        }
      },
      { root: null, rootMargin: '160px', threshold: 0 },
    )
    nextTick(() => bindTableLoadMoreObserver())
  }
  if (!authStore.userInfo?.permissions) {
    try {
      await authStore.fetchUserInfo()
    } catch {
      /* ignore */
    }
  }
  try {
    const res = await fetchConfirmValidationConfig()
    applyConfirmValidationConfig(res.data)
  } catch (e) {
    console.error('加载确认校验配置失败:', e)
  }
  loadTask()
})

onUnmounted(() => {
  isComponentMounted = false
  expandedDuplicateRowKeys.value = []
  if (duplicateDebounceTimer) window.clearTimeout(duplicateDebounceTimer)
  if (requiredValidationDebounceTimer) window.clearTimeout(requiredValidationDebounceTimer)
  if (anomalyCountDebounceTimer) window.clearTimeout(anomalyCountDebounceTimer)
  tableLoadMoreObserver?.disconnect()
  tableLoadMoreObserver = null
  clearSyncPoll()
})

watch(taskId, () => {
  if (isComponentMounted) {
    resetVisibleTableRows()
    resetTableColumnsLock()
    clearRowCache()
    loadTask()
  }
})

let duplicateDebounceTimer = null
watch(records, () => {
  scheduleRequiredMissingCountUpdate()
  scheduleAnomalyCountUpdate()
  if (duplicateRefreshing.value) return
  if (duplicateDebounceTimer) window.clearTimeout(duplicateDebounceTimer)
  duplicateDebounceTimer = window.setTimeout(() => {
    duplicateDebounceTimer = null
    refreshDuplicateDecorations()
  }, 300)
}, { deep: true })

watch(headerFilters, () => {
  resetVisibleTableRows()
}, { deep: true })
</script>

<style lang="scss" scoped>
.task-edit-container {
  padding: 0;

  &.has-sticky-submit {
    padding-bottom: calc(80px + env(safe-area-inset-bottom, 0px));
  }

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

      .anomaly-more-hint {
        padding-top: 8px;
        font-size: 12px;
        color: $text-secondary;
      }

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

  .table-scroll-load-more {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-height: 44px;
    margin: 8px 0 4px;
    color: $text-tertiary;
    font-size: $font-size-sm;
  }

  .table-scroll-load-more__text {
    color: $text-secondary;
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

  $required-empty-surface: $warning-light;
  $required-empty-border: rgba($warning, 0.45);

  :deep(.required-field-cell) {
    background: $required-empty-surface !important;
    box-shadow: inset 0 0 0 1px $required-empty-border;
  }

  :deep(th.col-required-header) {
    .ant-table-column-title {
      color: $text-primary;
      font-weight: 600;
    }
  }

  .required-validation-banner {
    margin-bottom: 12px;

    .required-validation-detail-btn {
      padding: 0 0 0 8px;
      height: auto;
      line-height: inherit;
    }
  }

  :deep(.required-empty) {
    background: #fffdf5 !important;
    border: 1px solid $required-empty-border !important;
    border-radius: $radius-sm;
    box-shadow: none;

    &:hover,
    &:focus,
    &.ant-input-number-focused {
      background: $required-empty-surface !important;
      border-color: rgba($warning, 0.62) !important;
      box-shadow: 0 0 0 2px $warning-ring;
    }

    input {
      border-color: transparent !important;
      color: $text-primary;
    }
  }

  .required-empty-display {
    color: $text-secondary;
    font-weight: 600;
    background: $required-empty-surface;
    box-shadow: inset 0 0 0 1px $required-empty-border;
    border-radius: $radius-sm;
    padding: 2px 6px;
    display: inline-block;
    min-width: 1.2em;
    text-align: center;
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

.task-edit-submit-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 110;
  padding: 14px 24px calc(14px + env(safe-area-inset-bottom, 0px));
  background: rgba($bg-surface, 0.96);
  backdrop-filter: blur(10px);
  border-top: 1px solid $border;
  box-shadow: 0 -8px 24px rgba(15, 23, 42, 0.08);
  pointer-events: auto;

  &__inner {
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 20px;
    max-width: 1200px;
    margin: 0 auto;
  }
}
</style>
