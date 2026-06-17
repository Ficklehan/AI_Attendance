<template>
  <div class="home" :class="{ 'result-mode': showResult }">
    <!-- ──────────── Initial Mode ──────────── -->
    <div v-if="!showResult" class="home__initial">
      <!-- Hero Banner -->
      <section class="hero">
        <div class="hero__content">
          <div class="hero__badge">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M13 2L3 14H12L11 22L21 10H12L13 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span>AI-Powered</span>
          </div>
          <h1 class="hero__title">{{ $t('home.uploadTitle') }}</h1>
          <p class="hero__desc">{{ $t('home.uploadDesc') }}</p>
        </div>
        <div class="hero__deco" aria-hidden="true">
          <div class="hero__orb hero__orb--1"></div>
          <div class="hero__orb hero__orb--2"></div>
          <div class="hero__orb hero__orb--3"></div>
        </div>
      </section>

      <!-- Working country context -->
      <div class="country-context surface-card">
        <div class="country-context__main">
          <span class="country-context__label">{{ $t('home.workingCountry') }}</span>
          <span class="country-context__value">{{ localizedWorkingCountryLabel }}</span>
          <a-space v-if="countryStore.bundle" size="small" wrap class="country-context__tags">
            <a-tag v-if="countryStore.promptFromGlobalFallback" color="orange" size="small">
              {{ $t('config.aiFallbackGlobal') }}
            </a-tag>
            <a-tag v-else color="blue" size="small">{{ $t('config.aiCountrySpecific') }}</a-tag>
            <a-tag v-if="countryStore.feishuFromGlobalFallback" color="orange" size="small">
              {{ $t('config.feishuFallbackGlobal') }}
            </a-tag>
            <a-tag v-else color="green" size="small">{{ $t('config.feishuCountrySpecific') }}</a-tag>
          </a-space>
        </div>
        <a-button type="link" size="small" @click="router.push('/config')">
          {{ $t('home.changeCountry') }}
        </a-button>
      </div>

      <!-- Step Guide -->
      <StepGuide :steps="guideSteps" />

      <!-- Upload Card -->
      <div class="upload-card">
        <div class="upload-card__header">
          <div class="upload-card__icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path d="M23 19C23 20.1046 22.1046 21 21 21H3C1.89543 21 1 20.1046 1 19V8C1 6.89543 1.89543 6 3 6H7L9 3H15L17 6H21C22.1046 6 23 6.89543 23 8V19Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <circle cx="12" cy="13" r="4" stroke="currentColor" stroke-width="2"/>
            </svg>
          </div>
          <div>
            <h3 class="upload-card__title">{{ $t('home.uploadTitle') }}</h3>
            <p class="upload-card__subtitle">{{ $t('home.uploadHint') }}</p>
          </div>
        </div>

        <div class="dropzone">
          <a-upload
            :multiple="true"
            :file-list="fileList"
            :before-upload="beforeUpload"
            :custom-request="customUpload"
            @preview="handleFilePreview"
            @remove="handleFileRemove"
            accept="image/*,.pdf,application/pdf"
            list-type="picture-card"
            class="dropzone__upload"
          >
            <div class="dropzone__trigger">
              <div class="dropzone__icon-ring">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
                  <path d="M12 16V4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  <path d="M8 8L12 4L16 8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M4 14V18C4 19.1046 4.89543 20 6 20H18C19.1046 20 20 19.1046 20 18V14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
              <div class="dropzone__text">{{ $t('home.uploadArea') }}</div>
              <div class="dropzone__hint">JPG, PNG, PDF &mdash; {{ $t('home.uploadHint') }}</div>
            </div>
          </a-upload>
        </div>

        <!-- File Summary -->
        <div v-if="fileList.length > 0" class="upload-summary">
          <div class="upload-summary__icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M14 2H6C4.89543 2 4 2.89543 4 4V20C4 21.1046 4.89543 22 6 22H18C19.1046 22 20 21.1046 20 20V8L14 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M14 2V8H20" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="upload-summary__text">{{ $t('home.selectedCount', { count: fileList.length }) }}</span>
          <span v-if="isPreparingImages" class="upload-summary__preparing">{{ $t('home.compressingImage') }}</span>
          <span class="upload-summary__size">{{ $t('home.totalSize', { size: totalSizeDisplay }) }}</span>
        </div>

        <!-- Actions -->
        <div class="upload-actions">
          <button
            class="btn-recognize"
            :class="{ 'btn-recognize--loading': uploading }"
            :disabled="!canStartRecognize"
            @click="handleUpload"
          >
            <svg v-if="!uploading && !isPreparingImages" width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M12 16V4M12 4L8 8.5M12 4L16 8.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M4 14V18C4 19.1046 4.89543 20 6 20H18C19.1046 20 20 19.1046 20 18V14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <span v-if="uploading || isPreparingImages" class="btn-spinner"></span>
            {{ uploading ? $t('home.recognizing') : (isPreparingImages ? $t('home.compressingImage') : $t('home.startRecognize')) }}
          </button>
          <button class="btn-clear" :disabled="uploading" @click="handleClear">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M3 6H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
              <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M5 6L6 20C6 20.5523 6.44772 21 7 21H17C17.5523 21 18 20.5523 18 20L19 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            {{ $t('home.clear') }}
          </button>
        </div>
      </div>
    </div>

    <!-- ──────────── Result Mode ──────────── -->
    <div v-else class="home__result">
      <!-- Left: Upload sidebar -->
      <aside class="sidebar">
        <div class="sidebar__card">
          <div class="sidebar__header">
            <div class="sidebar__icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M23 19C23 20.1046 22.1046 21 21 21H3C1.89543 21 1 20.1046 1 19V8C1 6.89543 1.89543 6 3 6H7L9 3H15L17 6H21C22.1046 6 23 6.89543 23 8V19Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="12" cy="13" r="4" stroke="currentColor" stroke-width="2"/>
              </svg>
            </div>
            <h3 class="sidebar__title">{{ $t('home.uploadTitle') }}</h3>
          </div>

          <div class="sidebar__actions">
            <button
              class="btn-recognize btn-recognize--sm"
              :class="{ 'btn-recognize--loading': uploading }"
              :disabled="!canStartRecognize"
              @click="handleUpload"
            >
              <span v-if="uploading || isPreparingImages" class="btn-spinner"></span>
              {{ uploading ? $t('home.recognizing') : (isPreparingImages ? $t('home.compressingImage') : $t('home.continueRecognize')) }}
            </button>
            <button class="btn-clear btn-clear--sm" @click="handleClear">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <path d="M3 6H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M5 6L6 20C6 20.5523 6.44772 21 7 21H17C17.5523 21 18 20.5523 18 20L19 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              {{ $t('home.uploadAgain') }}
            </button>
          </div>

          <div v-if="uploading" class="sidebar__processing">
            <div class="processing-ring"></div>
            <p v-if="uploadProgress.active">{{ $t('home.uploadingProgress', { uploaded: uploadProgress.uploaded, total: uploadProgress.total }) }}</p>
            <p v-else>{{ $t('home.recognizing') }}</p>
            <div v-if="uploadProgress.active && uploadProgress.total > 0" class="sidebar__upload-bar">
              <div
                class="sidebar__upload-bar-fill"
                :style="{ width: `${Math.round((uploadProgress.uploaded / uploadProgress.total) * 100)}%` }"
              />
            </div>
            <p v-if="displayProgressRowCount > 0" class="sidebar__progress">
              {{ $t('home.progressRows', { count: displayProgressRowCount }) }}
            </p>
            <p v-if="networkUnstable" class="sidebar__network-hint">{{ $t('home.networkUnstable') }}</p>
            <p class="sidebar__bg-hint">{{ $t('home.backgroundRecognitionHint') }}</p>
            <button type="button" class="btn-cancel-recognize" @click="cancelRecognition">
              {{ $t('home.runInBackground') }}
            </button>
          </div>
        </div>
      </aside>

      <!-- Right: Results -->
      <main class="results">
        <div class="results__card">
          <!-- Results Header -->
          <div class="results__header">
            <div class="results__header-left">
              <div class="results__header-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
                  <path d="M4 7V4H7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M17 4H20V7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M20 17V20H17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M7 20H4V17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M4 12H20" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  <path d="M12 4V20" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                </svg>
              </div>
              <h3 class="results__title">{{ $t('home.resultTitle') }}</h3>
            </div>
            <span v-if="records.length > 0" class="results__count">{{ $t('home.recordsCount', { count: records.length }) }}</span>
          </div>

          <!-- Stats -->
          <StatOverview v-if="records.length > 0" :items="statItems" />

          <!-- Anomaly Alerts -->
          <div v-if="anomalyAlerts.length > 0" class="anomaly-section">
            <div class="anomaly-banner" @click="showAnomalyDetail = !showAnomalyDetail">
              <div class="anomaly-banner__left">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <path d="M10.29 3.86L1.82 18C1.64526 18.3024 1.55285 18.6453 1.55201 18.9945C1.55117 19.3437 1.64192 19.6871 1.81518 19.9905C1.98844 20.2939 2.23714 20.5464 2.53782 20.7241C2.83851 20.9018 3.18048 20.9977 3.53 21H20.47C20.8195 20.9977 21.1615 20.9018 21.4622 20.7241C21.7629 20.5464 22.0116 20.2939 22.1848 19.9905C22.3581 19.6871 22.4488 19.3437 22.448 18.9945C22.4472 18.6453 22.3547 18.3024 22.18 18L13.71 3.86C13.5317 3.56611 13.2807 3.32312 12.9812 3.15448C12.6817 2.98585 12.3437 2.89722 12 2.89722C11.6563 2.89722 11.3183 2.98585 11.0188 3.15448C10.7193 3.32312 10.4683 3.56611 10.29 3.86Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <path d="M12 9V13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                  <circle cx="12" cy="17" r="1" fill="currentColor"/>
                </svg>
                <span class="anomaly-banner__title">{{ $t('home.anomalyAlert', { count: anomalyAlerts.length }) }}</span>
              </div>
              <span class="anomaly-banner__toggle">{{ showAnomalyDetail ? $t('home.collapse') : $t('home.expand') }}</span>
            </div>
            <div v-if="showAnomalyDetail" class="anomaly-list">
              <div v-for="(alert, idx) in anomalyAlerts" :key="idx" class="anomaly-item">
                <span class="anomaly-item__name">{{ alert.name }}</span>
                <span class="anomaly-item__tags">
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

          <!-- Data Table -->
          <div v-if="records.length > 0" class="table-wrap">
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
            <div ref="homeTableAnchor" class="table-body-scroll-anchor">
            <a-table
              :columns="columns"
              :data-source="tableRecords"
              :pagination="false"
              :scroll="tableScroll"
              :size="'small'"
              class="data-table rich-table-header"
              :row-class-name="getRowClassName"
            >
              <template #headerCell="{ column }">
                <TableSortableHeader
                  :column="column"
                  :title="column.title"
                  @sort="onSorterToggle"
                />
              </template>
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'serialNo'">
                  <span class="cell-text cell-serial">{{ index + 1 }}</span>
                </template>
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
                  <span v-else class="cell-muted">&mdash;</span>
                </template>
                <template v-if="column.key === 'SmartMark'">
                  <a-tag :color="getMarkColor(getDisplaySmartMark(record))" class="mark-tag">
                    {{ translateSmartMark(getDisplaySmartMark(record), t) }}
                  </a-tag>
                </template>
                <template v-if="column.key === 'PAUSE'">
                  <span class="cell-text">{{ formatPauseDisplay(record.PAUSE) }}</span>
                </template>
                <template v-if="column.key === 'SIGNATURE'">
                  <a-tag
                    :color="getSignatureMarkColor(getDisplaySignature(record.SIGNATURE, record))"
                    class="signature-mark-tag"
                  >
                    {{ translateSignatureMark(getDisplaySignature(record.SIGNATURE, record), t) }}
                  </a-tag>
                </template>
                <template v-if="column.key === 'action'">
                  <div class="table-action-cell table-action-cell--icons table-action-cell--icons-1">
                    <span class="table-action-cell__slot">
                      <a-tooltip :title="record.isDeleted ? $t('common.undo') : $t('common.delete')">
                        <a-button
                          type="text"
                          :danger="!record.isDeleted"
                          shape="circle"
                          size="small"
                          @click="deleteRecord(record)"
                        >
                          <svg v-if="!record.isDeleted" width="14" height="14" viewBox="0 0 24 24" fill="none">
                            <path d="M3 6H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                            <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                            <path d="M5 6L6 20C6 20.5523 6.44772 21 7 21H17C17.5523 21 18 20.5523 18 20L19 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none">
                            <path d="M1 4V10H7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                            <path d="M3.51 15C4.15839 16.8404 5.38734 18.4202 7.01166 19.5014C8.63598 20.5826 10.5677 21.1066 12.5157 20.9945C14.4637 20.8824 16.3226 20.1402 17.8121 18.8798C19.3017 17.6193 20.3413 15.9074 20.7742 14.0064C21.2072 12.1053 21.0101 10.1158 20.2126 8.33953C19.4152 6.56328 18.0605 5.09319 16.3528 4.15275C14.6451 3.21231 12.6769 2.8519 10.7447 3.12488C8.81245 3.39786 7.02091 4.28915 5.64 5.66L1 10" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          </svg>
                        </a-button>
                      </a-tooltip>
                    </span>
                  </div>
                </template>
              </template>
            </a-table>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="empty-state">
            <div class="empty-state__icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none">
                <path d="M4 7V4H7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M17 4H20V7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M20 17V20H17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M7 20H4V17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M4 12H20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                <path d="M12 4V20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </div>
            <p>{{ $t('home.noRecords') }}</p>
          </div>

          <!-- Confirm Button -->
          <div v-if="records.length > 0 && !uploading" class="results__footer">
            <button class="btn-confirm" @click="handleConfirm">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M9 12L11 14L15 10" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/>
              </svg>
              {{ $t('home.confirmAndEdit') }}
            </button>
          </div>
        </div>
      </main>
    </div>

    <ImagePreviewModal
      v-model:open="previewVisible"
      :images="uploadPreviewImages"
      :initial-index="previewIndex"
      :title="previewTitle"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal as aModal } from 'ant-design-vue'
import { compressImage, getImageHash, getFileSizeDisplay } from '@/utils/image'
import StatOverview from '@/components/StatOverview.vue'
import TruncatedTag from '@/components/TruncatedTag.vue'
import StepGuide from '@/components/StepGuide.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import { useCountryStore } from '@/stores/country'
import { getTaskDetail, getTaskProgress } from '@/api/task'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import {
  submitBackgroundRecognition,
  pollRecognitionUntilDone,
  retryTaskRecognition,
  persistBgTaskId,
  clearBgTaskId,
  getPersistedBgTaskId,
  parseRecordsFromTask,
} from '@/utils/backgroundRecognition'
import { applyMissingPays } from '@/utils/countryDefaults'
import {
  translateAnomalyReason,
  translateSmartMark,
  stripSignatureMarksFromSmartMark,
  getDisplaySignature,
  translateSignatureMark,
  getSignatureMarkColor,
  markContains,
  computeSignatureMark,
  anomalyReasonKind,
  calculateRecordStats,
} from '@/utils/recognitionLabels'
import { buildRecognitionTableColumns } from '@/utils/recognitionTableColumns'
import { useTableColumnSort } from '@/composables/useTableColumnSort'
import { useAutoSizedColumns } from '@/composables/useAutoSizedColumns'
import { useTableBodyScrollY } from '@/composables/useTableBodyScrollY'
import { useColumnFreeze } from '@/composables/useColumnFreeze'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import TableSortableHeader from '@/components/TableSortableHeader.vue'
import { hasRequiredMissing } from '@/utils/requiredRecordFields'
import { isAbsentRow } from '@/utils/recordDisplay'
import { formatCountryLabel } from '@/utils/countryLabels'
import { translateErrorMessage, showHomeUploadError } from '@/utils/translateError'

const router = useRouter()
const { t, locale } = useI18n()
const countryStore = useCountryStore()

const localizedWorkingCountryLabel = computed(() => {
  void locale.value
  const code = countryStore.workingCountry || 'default'
  const found = (countryStore.options || []).find((item) => item.code === code)
  if (found) return formatCountryLabel(found.code, found.flag, found.name)
  return countryStore.workingCountryLabel
})

const cellStr = (v) => (v == null || v === '' ? '' : String(v))

const fileList = ref([])
const uploading = ref(false)
const submitting = ref(false)
const records = ref([])
const currentTaskId = ref(null)
const processedHashes = ref(new Set())
const showResult = ref(false)
const previewVisible = ref(false)
const previewIndex = ref(0)
const previewTitle = ref('')

const uploadPreviewImages = computed(() =>
  fileList.value
    .filter((file) => !file.isPdf && (file.url || file.thumbUrl))
    .map((file) => file.url || file.thumbUrl)
)
const showAnomalyDetail = ref(true)
const homeTableAnchor = ref(null)

const stopPolling = ref(false)
const progressRowCount = ref(0)
const displayProgressRowCount = computed(() => {
  if (uploading.value && records.value.length > 0) {
    return records.value.length
  }
  return progressRowCount.value
})
const uploadProgress = ref({ uploaded: 0, total: 0, active: false })
const networkUnstable = ref(false)
let lastPartialFetchCount = 0

const cancelRecognition = () => {
  stopPolling.value = true
  uploading.value = false
  message.info(t('home.runInBackground'))
  const taskId = currentTaskId.value
  if (taskId) {
    router.push({ path: '/tasks', query: { status: 'processing', taskId } })
  } else {
    router.push({ path: '/tasks', query: { status: 'processing' } })
  }
}

const applyRecordsFromTask = (taskRows) => {
  records.value = (taskRows || []).map((record) => normalizeRecordPause({ ...record, isDeleted: false }))
}

const finishRecognitionSuccess = (result) => {
  applyRecordsFromTask(result.records)
  currentTaskId.value = result.taskId
  if (result.records.length > 0) {
    clearBgTaskId()
    message.success(t('home.recognizeSuccess', { count: result.records.length }))
    router.push(`/tasks/${result.taskId}`)
  } else {
    message.warning(t('home.noRecordsFound'))
  }
}

const showRecognitionFailure = (taskId, error) => {
  if (!taskId || stopPolling.value) {
    showHomeUploadError(error)
    return
  }
  const reason = translateErrorMessage({
    message: error?.message,
    messageKey: error?.messageKey,
    messageArgs: error?.messageArgs,
  })
  aModal.confirm({
    title: t('home.recognitionFailedTitle'),
    content: reason,
    okText: t('home.recognitionRetry'),
    cancelText: t('home.recognitionReupload'),
    centered: true,
    width: 420,
    onOk: () => {
      void runRecognitionPolling(taskId)
    },
    onCancel: () => {
      handleClear()
    },
  })
}

const runRecognitionPolling = async (taskId) => {
  stopPolling.value = false
  uploading.value = true
  showResult.value = true
  currentTaskId.value = taskId
  persistBgTaskId(taskId)
  lastPartialFetchCount = records.value.length
  try {
    const result = await retryTaskRecognition(taskId, {
      shouldAbort: () => stopPolling.value,
      onProgress: async (p) => {
        currentTaskId.value = p.taskId
        if (p.phase !== 'refresh') {
          progressRowCount.value = p.rowCount || 0
          networkUnstable.value = !!p.networkRetry
        }
      },
    })
    if (result.aborted) return
    finishRecognitionSuccess(result)
  } catch (error) {
    if (String(error?.message || '').includes(t('errors.taskAccessDenied'))) {
      clearBgTaskId()
    }
    if (!stopPolling.value) {
      const failedTaskId = error?.taskId || taskId
      console.error('Recognition error:', error)
      showRecognitionFailure(failedTaskId, error)
    }
  } finally {
    uploading.value = false
    networkUnstable.value = false
    uploadProgress.value = { uploaded: 0, total: 0, active: false }
  }
}

const refreshPartialRecords = async (taskId) => {
  if (!taskId) return
  try {
    const detailRes = await getTaskDetail(taskId)
    const rows = parseRecordsFromTask(detailRes.data || {})
    if (rows.length > lastPartialFetchCount) {
      applyRecordsFromTask(rows)
      lastPartialFetchCount = rows.length
    }
    if (rows.length > 0) {
      progressRowCount.value = rows.length
    }
  } catch {
    // partial refresh is best-effort
  }
}

const resumeBackgroundPolling = async (taskId) => {
  stopPolling.value = false
  uploading.value = true
  showResult.value = true
  currentTaskId.value = taskId
  lastPartialFetchCount = 0
  try {
    const result = await pollRecognitionUntilDone(taskId, {
      shouldAbort: () => stopPolling.value,
      onProgress: async (p) => {
        currentTaskId.value = p.taskId
        if (p.phase !== 'refresh') {
          progressRowCount.value = p.rowCount || 0
          networkUnstable.value = !!p.networkRetry
        }
      },
    })
    if (result.aborted) return
    finishRecognitionSuccess(result)
  } catch (error) {
    if (String(error?.message || '').includes(t('errors.taskAccessDenied'))) {
      clearBgTaskId()
    }
    if (!stopPolling.value) {
      const failedTaskId = error?.taskId || taskId
      showRecognitionFailure(failedTaskId, error)
    }
  } finally {
    uploading.value = false
    networkUnstable.value = false
  }
}

const guideSteps = computed(() => [
  { title: t('home.guideStep1Title'), desc: t('home.guideStep1Desc') },
  { title: t('home.guideStep2Title'), desc: t('home.guideStep2Desc') },
  { title: t('home.guideStep3Title'), desc: t('home.guideStep3Desc') }
])

const resetState = () => {
  stopPolling.value = true
  fileList.value.forEach(revokePreviewUrl)
  fileList.value = []
  uploading.value = false
  submitting.value = false
  records.value = []
  currentTaskId.value = null
  processedHashes.value = new Set()
  showResult.value = false
  previewVisible.value = false
  previewIndex.value = 0
  previewTitle.value = ''
  progressRowCount.value = 0
  uploadProgress.value = { uploaded: 0, total: 0, active: false }
  networkUnstable.value = false
  lastPartialFetchCount = 0
}

onMounted(async () => {
  const persistedTaskId = getPersistedBgTaskId()
  resetState()
  stopPolling.value = false
  try {
    await countryStore.hydrate()
  } catch (error) {
    console.error('加载工作国家失败:', error)
  }
  if (persistedTaskId) {
    try {
      const progressRes = await getTaskProgress(persistedTaskId)
      const status = progressRes.data?.status
      if (status === 'processing') {
        void resumeBackgroundPolling(persistedTaskId)
      } else if (status === 'failed') {
        currentTaskId.value = persistedTaskId
        showResult.value = true
        await refreshPartialRecords(persistedTaskId)
      } else {
        clearBgTaskId()
      }
    } catch {
      clearBgTaskId()
    }
  }
})
onBeforeUnmount(() => {
  stopPolling.value = true
  fileList.value.forEach(revokePreviewUrl)
})

const stats = computed(() => calculateRecordStats(records.value))

const statItems = computed(() => [
  { key: 'normal', variant: 'normal', value: stats.value.normal, label: t('home.statsNormal') },
  { key: 'handwriting', variant: 'handwriting', value: stats.value.handwriting, label: t('home.statsHandwriting') },
  { key: 'blurred', variant: 'blurred', value: stats.value.blurred, label: t('home.statsBlurred') },
  { key: 'night', variant: 'night', value: stats.value.night, label: t('home.statsNight') },
  { key: 'absent', variant: 'absent', value: stats.value.absent, label: t('home.statsAbsent') },
  { key: 'deleted', variant: 'deleted', value: stats.value.deleted, label: t('home.statsDeleted') },
])

const normalizePauseMinutes = (value) => {
  if (value === null || value === undefined || value === '') return ''
  const normalized = String(value).trim().toLowerCase().replace(',', '.').replace(/\s+/g, '').replace(/minutes?|mins?|mn/g, 'min')
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

const normalizeRecordPause = (record) => {
  const signatureMark = computeSignatureMark(record)
  return applyMissingPays(
    {
      ...record,
      PAUSE: normalizePauseMinutes(record?.PAUSE),
      PAGE_NUM: record?.PAGE_NUM ?? record?.pageNum ?? '',
      SIGNATURE: signatureMark,
      CHECKER: signatureMark,
    },
    getCachedWorkingCountry()
  )
}

const formatPauseDisplay = (value) => {
  const minutes = normalizePauseMinutes(value)
  return minutes === '' ? '-' : `${minutes} min`
}

const getEffectiveAnomalies = (record) => {
  const anomalies = Array.isArray(record?.anomalies) ? record.anomalies : []
  return anomalies.filter(reason => reason && !String(reason).includes(t('home.statsNight')) && !String(reason).includes('夜班'))
}

const getRecordAnomalyReasons = (record) => {
  if (!record || record.isDeleted) return []
  const mark = getDisplaySmartMark(record)
  const reasons = getEffectiveAnomalies(record).map((r) => translateAnomalyReason(r, t))
  if (markContains(mark, 'blurred')) reasons.push(t('taskEdit.blurredContent'))
  if (markContains(mark, 'handwriting')) reasons.push(t('taskEdit.handwrittenContent'))
  if (markContains(mark, 'absent')) reasons.push(t('taskEdit.absentReason'))
  if (hasRequiredMissing(record)) reasons.push(t('taskEdit.requiredFieldMissingShort'))
  return [...new Set(reasons)]
}

const cellStyle = (record) => {
  if (!record) return {}
  if (record?.isDeleted || isAbsentRow(record)) {
    return { style: { backgroundColor: '#FFF0F0', color: '#D94040', fontStyle: 'italic', textDecoration: 'line-through', textDecorationColor: '#E8A0A0' } }
  }
  if (hasRequiredMissing(record)) return { style: { backgroundColor: '#FFF9EC' } }
  if (getDisplaySmartMark(record).includes('模糊')) return { style: { backgroundColor: '#FFF9EC' } }
  return {}
}

const baseColumns = computed(() => buildRecognitionTableColumns(t, { cellStyle }))
const { columns: sortedColumns, onSorterToggle, sortRows } = useTableColumnSort(baseColumns, { customHeader: true })
const tableRecords = computed(() => sortRows(records.value))
const { columns: sizedColumns, scrollX } = useAutoSizedColumns(sortedColumns, tableRecords, { actionWidth: 50 })
const { tableScroll, measure: measureHomeTableScroll } = useTableBodyScrollY(
  homeTableAnchor,
  scrollX,
  { enabled: computed(() => records.value.length > 0) },
)

watch(showAnomalyDetail, () => {
  measureHomeTableScroll()
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
} = useColumnFreeze('home-records', sizedColumns, { defaultFrozen: ['serialNo', 'PAGE_NUM', 'NO'] })

const totalSizeDisplay = computed(() => {
  const total = fileList.value.reduce((sum, file) => sum + (file.size || 0), 0)
  return getFileSizeDisplay(total)
})

const isPreparingImages = computed(() => fileList.value.some((file) => file.status === 'uploading'))
const readyFiles = computed(() => fileList.value.filter((file) => file.status === 'done' && file.raw))
const canStartRecognize = computed(() => readyFiles.value.length > 0 && !uploading.value && !isPreparingImages.value)

const updateFileEntry = (uid, patch) => {
  fileList.value = fileList.value.map((item) => (item.uid === uid ? { ...item, ...patch } : item))
}

const removeFileEntry = (uid) => {
  const target = fileList.value.find((item) => item.uid === uid)
  if (target) revokePreviewUrl(target)
  fileList.value = fileList.value.filter((item) => item.uid !== uid)
}

const isPdfFile = (file) => {
  const type = (file.type || '').toLowerCase()
  const name = (file.name || '').toLowerCase()
  return type === 'application/pdf' || name.endsWith('.pdf')
}

const prepareSelectedFile = async (file) => {
  const isPdf = isPdfFile(file)
  const previewUrl = isPdf ? '' : URL.createObjectURL(file)
  const uid = file.uid
  fileList.value.push({
    uid,
    name: file.name,
    size: file.size,
    url: previewUrl,
    thumbUrl: previewUrl,
    status: 'uploading',
    raw: null,
    isPdf,
  })

  try {
    if (isPdf) {
      const hash = `${file.name}:${file.size}:${file.lastModified}`
      if (processedHashes.value.has(hash)) {
        removeFileEntry(uid)
        message.warning(t('home.duplicateImage'))
        return
      }
      processedHashes.value.add(hash)
      updateFileEntry(uid, {
        raw: file,
        hash,
        status: 'done',
      })
      return
    }

    const hash = await getImageHash(file)
    if (processedHashes.value.has(hash)) {
      removeFileEntry(uid)
      message.warning(t('home.duplicateImage'))
      return
    }

    const compressedFile = await compressImage(file, {
      maxSizeKB: 2000,
      maxWidth: 1600,
      maxHeight: 1600,
      quality: 0.85,
    })
    processedHashes.value.add(hash)

    const compressedUrl = URL.createObjectURL(compressedFile)
    URL.revokeObjectURL(previewUrl)
    updateFileEntry(uid, {
      size: compressedFile.size,
      raw: compressedFile,
      hash,
      url: compressedUrl,
      thumbUrl: compressedUrl,
      status: 'done',
    })
  } catch (error) {
    console.error('图片压缩失败:', error)
    removeFileEntry(uid)
    message.error(t('home.compressFailed'))
  }
}

const beforeUpload = (file) => {
  prepareSelectedFile(file)
  return false
}

const customUpload = () => {}

const revokePreviewUrl = (file) => {
  if (file && file.url && file.url.startsWith('blob:')) URL.revokeObjectURL(file.url)
}

const handleFilePreview = (file) => {
  const target = fileList.value.find((item) => item.uid === file.uid) || file
  if (target.isPdf || (!target.url && !target.thumbUrl)) {
    message.info(t('home.pdfNoPreview'))
    return
  }
  const imageFiles = fileList.value.filter(
    (item) => !item.isPdf && (item.url || item.thumbUrl)
  )
  const index = imageFiles.findIndex((item) => item.uid === target.uid)
  if (index < 0) return
  previewTitle.value = target.name || ''
  previewIndex.value = index
  previewVisible.value = true
}

const handleFileRemove = (file) => {
  const target = fileList.value.find(item => item.uid === file.uid)
  if (target) {
    revokePreviewUrl(target)
    if (target.hash) {
      const nextHashes = new Set(processedHashes.value)
      nextHashes.delete(target.hash)
      processedHashes.value = nextHashes
    }
  }
  fileList.value = fileList.value.filter(item => item.uid !== file.uid)
  if (previewVisible.value && previewTitle.value === file.name) {
    previewVisible.value = false
    previewIndex.value = 0
    previewTitle.value = ''
  }
  return false
}

const handleUpload = async () => {
  if (isPreparingImages.value) {
    message.info(t('home.preparingImages'))
    return
  }
  const filesToUpload = readyFiles.value
  if (filesToUpload.length === 0) {
    message.warning(t('home.selectAtLeastOne'))
    return
  }

  stopPolling.value = false
  uploading.value = true
  showResult.value = true
  records.value = []
  progressRowCount.value = 0
  uploadProgress.value = { uploaded: 0, total: 0, active: false }
  networkUnstable.value = false
  lastPartialFetchCount = 0

  try {
    const result = await submitBackgroundRecognition(
      filesToUpload.map((file) => file.raw),
      {
        shouldAbort: () => stopPolling.value,
        onProgress: async (p) => {
          currentTaskId.value = p.taskId
          persistBgTaskId(p.taskId)
          if (p.phase !== 'refresh') {
            progressRowCount.value = p.rowCount || 0
            networkUnstable.value = !!p.networkRetry
          }
          if (p.phase === 'uploading') {
            uploadProgress.value = {
              uploaded: p.uploaded || 0,
              total: p.total || 0,
              active: true,
            }
          } else if (p.phase !== 'refresh') {
            uploadProgress.value = { ...uploadProgress.value, active: false }
          }
        if (p.status === 'processing' || p.phase === 'refresh') {
          progressRowCount.value = p.rowCount || progressRowCount.value
        }
        },
      },
    )

    if (result.aborted) {
      return
    }

    finishRecognitionSuccess(result)
  } catch (error) {
    if (String(error?.message || '').includes(t('errors.taskAccessDenied'))) {
      clearBgTaskId()
    }
    if (!stopPolling.value) {
      const taskId = error?.taskId || currentTaskId.value
      console.error('Upload error:', error)
      if (taskId) {
        currentTaskId.value = taskId
        persistBgTaskId(taskId)
        showResult.value = true
        showRecognitionFailure(taskId, error)
      } else {
        showHomeUploadError(error)
      }
    }
  } finally {
    uploading.value = false
    uploadProgress.value = { uploaded: 0, total: 0, active: false }
    networkUnstable.value = false
  }
}

const handleConfirm = () => {
  if (!currentTaskId.value) { message.warning(t('home.selectAtLeastOne')); return }
  router.push(`/tasks/${currentTaskId.value}`)
}

const handleClear = () => {
  clearBgTaskId()
  resetState()
}

const deleteRecord = (record) => {
  if (record) {
    record.isDeleted = !record.isDeleted
    if (record.isDeleted) { record._prevMark = record.SmartMark; record.SmartMark = '已删除' }
    else {
      if (record._prevMark) { record.SmartMark = record._prevMark; delete record._prevMark }
      else record.SmartMark = '正常'
    }
  }
}

const anomalyAlerts = computed(() => {
  return records.value
    .map((record) => {
      if (record.isDeleted) return null
      const reasons = getRecordAnomalyReasons(record)
      if (reasons.length === 0) return null
      const no = cellStr(record.NO) || '?'
      const name = cellStr(record.NOM_PRENOM) || '?'
      return { name: no + ' - ' + name, reasons: [...new Set(reasons)] }
    })
    .filter(Boolean)
})

const getRowClassName = (record, index) => {
  if (!record || record?.isDeleted) return 'deleted-row'
  const mark = getDisplaySmartMark(record)
  if (hasRequiredMissing(record)) return 'incomplete-row'
  if (markContains(mark, 'absent')) return 'absent-row'
  if (markContains(mark, 'blurred')) return 'blurred-row'
  return ''
}

const getAnomalyTagColor = (reason) => {
  const kind = anomalyReasonKind(reason)
  if (kind === 'absent' || kind === 'missing') return 'red'
  if (kind === 'blurred' || kind === 'duplicate') return 'orange'
  if (kind === 'handwriting') return 'blue'
  return 'default'
}

const getMarkColor = (mark) => {
  const m = cellStr(mark)
  if (!m) return 'default'
  const parts = m.split(/[;；,，]/).map((p) => p.trim()).filter(Boolean)
  for (const part of parts) {
    if (part === '未签字' || part === '未签字确认') return 'warning'
    if (part === '已签字' || part === '已签字确认') return 'success'
  }
  if (markContains(m, 'absent')) return 'error'
  if (markContains(m, 'blurred')) return 'warning'
  if (markContains(m, 'handwriting')) return 'processing'
  if (markContains(m, 'nightShift')) return 'purple'
  if (markContains(m, 'normal')) return 'success'
  return 'default'
}

const hasHandwrittenText = (value) => {
  const text = cellStr(value).toLowerCase()
  return text.includes('手写') || text.includes('handwritten') || text.includes('manuscrit') || text.includes('manuscrite') || text.includes('ecrit main') || text.includes('écrit main') || text.includes('ecrit a la main') || text.includes('écrit à la main')
}

const hasHandwrittenIdentity = (record) => {
  const anomalyText = Array.isArray(record?.anomalies) ? record.anomalies.join(' ') : ''
  return hasHandwrittenText(record?.NO) || hasHandwrittenText(record?.NOM_PRENOM) || hasHandwrittenText(record?.Mark) || hasHandwrittenText(record?.mark) || hasHandwrittenText(record?.smartMark) || hasHandwrittenText(anomalyText)
}

const getDisplaySmartMark = (record) => {
  const sourceMarks = [record?.SmartMark, record?.Mark, record?.mark, record?.smartMark].map(v => cellStr(v)).filter(Boolean)
  const raw = stripSignatureMarksFromSmartMark(
    [...new Set(sourceMarks.join(';').split(/[;；,，]/).map(v => v.trim()).filter(Boolean))].join(';')
  )
  const hasHandwritten = hasHandwrittenIdentity(record) || raw.includes('手写')
  if (!hasHandwritten || raw.includes('已删除') || raw.includes('未出勤')) return raw || '-'
  if (!raw || raw === '-' || raw === '正常') return '手写'
  if (raw.includes('手写')) return raw
  return `${raw};手写`
}
</script>

<style lang="scss" scoped>
// ═══════════════════════════════════════════════════════════
// Home Page — Atelier v4
// ═══════════════════════════════════════════════════════════

.home {
  padding: $space-5;
  min-height: calc(100vh - #{$header-height});

  &.result-mode {
    height: calc(100vh - #{$header-height});
    padding: $space-5;
  }
}

// ── Hero Section ──
.hero {
  position: relative;
  overflow: hidden;
  border-radius: $radius-2xl;
  padding: $space-10 $space-8;
  background: linear-gradient(135deg, #4A58D9 0%, $primary 40%, #8B9AFF 70%, #B4C0FF 100%);
  background-size: 200% 200%;
  animation: gradientShift 8s ease infinite;
  margin-bottom: $space-5;
  color: white;

  &__content {
    position: relative;
    z-index: 2;
  }

  &__badge {
    display: inline-flex;
    align-items: center;
    gap: $space-2;
    padding: $space-1 $space-3;
    background: rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(10px);
    border-radius: $radius-full;
    font-size: $font-size-xs;
    font-weight: $font-weight-semibold;
    letter-spacing: 0.5px;
    text-transform: uppercase;
    margin-bottom: $space-4;
    border: 1px solid rgba(255, 255, 255, 0.25);
  }

  &__title {
    font-size: $font-size-4xl;
    font-weight: $font-weight-extrabold;
    line-height: $line-height-tight;
    margin: 0 0 $space-3;
    letter-spacing: -0.02em;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  }

  &__desc {
    font-size: $font-size-lg;
    opacity: 0.9;
    margin: 0;
    max-width: 560px;
    line-height: $line-height-relaxed;
  }

  // Decorative orbs
  &__deco {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 50%;
    pointer-events: none;
    z-index: 1;
  }

  &__orb {
    position: absolute;
    border-radius: 50%;
    opacity: 0.15;
    background: white;

    &--1 {
      width: 200px;
      height: 200px;
      top: -40px;
      right: -20px;
      animation: float 6s $ease-smooth infinite;
    }

    &--2 {
      width: 120px;
      height: 120px;
      bottom: -30px;
      right: 120px;
      animation: float 4s $ease-smooth infinite 1s;
    }

    &--3 {
      width: 80px;
      height: 80px;
      top: 30px;
      right: 200px;
      opacity: 0.1;
      animation: float 5s $ease-smooth infinite 0.5s;
    }
  }
}

// ── Initial Mode ──
.home__initial {
  display: flex;
  flex-direction: column;
  gap: $space-5;
  width: 100%;
  max-width: 100%;
  margin: 0;
}

.country-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-4;
  padding: $space-3 $space-4;
  border-radius: $radius-lg;
  background: $bg-surface;
  border: 1px solid $border-light;

  &__main {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: $space-2;
    min-width: 0;
  }

  &__label {
    font-size: $font-size-sm;
    color: $text-secondary;
  }

  &__value {
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  &__tags {
    margin-left: $space-1;
  }
}

// ── Upload Card ──
.upload-card {
  background: $bg-surface;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  border: 1px solid rgba($border, 0.5);
  padding: $space-6;
  animation: fadeUp 0.5s $ease-out 0.25s both;

  &__header {
    display: flex;
    align-items: center;
    gap: $space-4;
    margin-bottom: $space-5;
    padding-bottom: $space-5;
    border-bottom: 1px solid $border-light;
  }

  &__icon {
    width: 44px;
    height: 44px;
    border-radius: $radius-lg;
    background: linear-gradient(135deg, $primary-light 0%, $primary-lighter 100%);
    color: $primary;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__title {
    font-size: $font-size-xl;
    font-weight: $font-weight-bold;
    color: $text-strong;
    margin: 0 0 2px;
    line-height: $line-height-tight;
  }

  &__subtitle {
    font-size: $font-size-sm;
    color: $text-tertiary;
    margin: 0;
  }
}

// ── Dropzone ──
.dropzone {
  &__upload {
    width: 100%;

    :deep(.ant-upload-select) {
      width: 100% !important;
      height: 180px !important;
      margin: 0 0 $space-3 0 !important;
      border: 2px dashed rgba($primary, 0.25) !important;
      border-radius: $radius-xl !important;
      background: linear-gradient(135deg, $primary-lighter 0%, $bg-muted 100%) !important;
      transition: all $duration-base $ease-smooth !important;
      cursor: pointer;

      &:hover {
        border-color: $primary !important;
        background: linear-gradient(135deg, $primary-light 0%, $primary-lighter 100%) !important;
        box-shadow: $shadow-glow;
        transform: translateY(-1px);
      }
    }
  }

  &__trigger {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: $space-3;
  }

  &__icon-ring {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: $primary-gradient-deep;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: $shadow-glow;
    transition: transform $duration-base $ease-bounce;

    .dropzone__upload :deep(.ant-upload-select):hover & {
      transform: scale(1.08);
    }
  }

  &__text {
    font-size: $font-size-md;
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  &__hint {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}

// ── Upload Summary ──
.upload-summary {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-3 $space-4;
  background: $bg-brand-subtle;
  border-radius: $radius-md;
  margin-bottom: $space-5;
  animation: scaleIn 0.25s $ease-bounce;

  &__icon {
    color: $primary;
    display: flex;
    align-items: center;
  }

  &__text {
    font-size: $font-size-md;
    font-weight: $font-weight-medium;
    color: $primary;
  }

  &__preparing {
    font-size: $font-size-sm;
    color: $text-secondary;
  }

  &__size {
    margin-left: auto;
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}

// ── Action Buttons ──
.upload-actions {
  display: flex;
  gap: $space-3;
  margin-top: $space-5;
}

.btn-recognize {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  height: 46px;
  padding: 0 $space-6;
  border: none;
  border-radius: $radius-lg;
  background: $primary-gradient;
  color: white;
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  box-shadow: $shadow-glow;
  transition: all $duration-base $ease-smooth;

  &:hover:not(:disabled) {
    box-shadow: $shadow-glow-lg;
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    box-shadow: none;
  }

  &--loading {
    pointer-events: none;
  }

  &--sm {
    height: 38px;
    font-size: $font-size-base;
    padding: 0 $space-4;
  }
}

.btn-spinner {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.btn-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  height: 46px;
  padding: 0 $space-5;
  border: 1px solid $border;
  border-radius: $radius-lg;
  background: $bg-surface;
  color: $text-secondary;
  font-size: $font-size-md;
  font-weight: $font-weight-medium;
  cursor: pointer;
  transition: all $duration-base $ease-smooth;

  &:hover:not(:disabled) {
    border-color: $border-hover;
    color: $text-primary;
    background: $bg-hover;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &--sm {
    height: 38px;
    font-size: $font-size-base;
    padding: 0 $space-3;
  }
}

// ═══════════════════════════════════════════════════════════
// Result Mode
// ═══════════════════════════════════════════════════════════

.home__result {
  display: flex;
  gap: $space-5;
  height: 100%;
  animation: fadeUp 0.4s $ease-out;
}

// ── Sidebar ──
.sidebar {
  width: 300px;
  flex-shrink: 0;
  animation: slideInLeft 0.4s $ease-out;

  &__card {
    height: 100%;
    background: $bg-surface;
    border-radius: $radius-xl;
    box-shadow: $shadow-card;
    border: 1px solid rgba($border, 0.5);
    display: flex;
    flex-direction: column;
    padding: $space-5;
  }

  &__header {
    display: flex;
    align-items: center;
    gap: $space-3;
    margin-bottom: $space-5;
    padding-bottom: $space-4;
    border-bottom: 1px solid $border-light;
  }

  &__icon {
    width: 36px;
    height: 36px;
    border-radius: $radius-md;
    background: linear-gradient(135deg, $primary-light 0%, $primary-lighter 100%);
    color: $primary;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__title {
    font-size: $font-size-lg;
    font-weight: $font-weight-semibold;
    color: $text-strong;
    margin: 0;
  }

  &__actions {
    display: flex;
    flex-direction: column;
    gap: $space-3;
  }

  &__processing {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: $space-4;
    padding: $space-6;
    background: $bg-brand-subtle;
    border-radius: $radius-lg;
    margin-top: $space-4;

    p {
      font-size: $font-size-sm;
      color: $text-secondary;
      margin: 0;
      text-align: center;
    }
  }

  &__progress {
    font-weight: 600;
    color: $primary !important;
  }

  &__network-hint {
    color: #d48806 !important;
  }

  &__bg-hint {
    font-size: $font-size-xs !important;
    line-height: 1.5;
    max-width: 220px;
  }

  &__upload-bar {
    width: 100%;
    max-width: 200px;
    height: 6px;
    border-radius: 999px;
    background: rgba($primary, 0.15);
    overflow: hidden;
  }

  &__upload-bar-fill {
    height: 100%;
    border-radius: inherit;
    background: $primary;
    transition: width 0.25s $ease-out;
  }
}

.processing-ring {
  width: 40px;
  height: 40px;
  border: 3px solid $primary-light;
  border-top-color: $primary;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.btn-cancel-recognize {
  margin-top: $space-2;
  padding: $space-2 $space-4;
  font-size: $font-size-sm;
  color: $text-secondary;
  background: $bg-surface;
  border: 1px solid $border;
  border-radius: $radius-md;
  cursor: pointer;

  &:hover {
    color: $text-primary;
    border-color: $primary;
  }
}

// ── Results Panel ──
.results {
  flex: 1;
  min-width: 0;
  animation: slideInRight 0.4s $ease-out 0.1s both;

  &__card {
    height: 100%;
    background: $bg-surface;
    border-radius: $radius-xl;
    box-shadow: $shadow-card;
    border: 1px solid rgba($border, 0.5);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: $space-5;
    border-bottom: 1px solid $border-light;
    flex-shrink: 0;
  }

  &__header-left {
    display: flex;
    align-items: center;
    gap: $space-3;
  }

  &__header-icon {
    width: 36px;
    height: 36px;
    border-radius: $radius-md;
    background: $bg-brand-subtle;
    color: $primary;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__title {
    font-size: $font-size-lg;
    font-weight: $font-weight-semibold;
    color: $text-strong;
    margin: 0;
  }

  &__count {
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    background: $primary-light;
    color: $primary;
    padding: $space-1 $space-3;
    border-radius: $radius-full;
    border: 1px solid rgba($primary, 0.15);
  }

  &__footer {
    padding: $space-4 $space-5;
    border-top: 1px solid $border-light;
    text-align: center;
    flex-shrink: 0;
  }
}

// ── Anomaly Section ──
.anomaly-section {
  margin: $space-4 $space-5 0;
}

.anomaly-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-3 $space-4;
  background: linear-gradient(135deg, $warning-light 0%, #FFFAE0 100%);
  border: 1px solid rgba($warning, 0.2);
  border-radius: $radius-lg;
  cursor: pointer;
  transition: all $duration-fast $ease-smooth;

  &:hover {
    border-color: rgba($warning, 0.35);
  }

  &__left {
    display: flex;
    align-items: center;
    gap: $space-2;
    color: $warning-dark;
  }

  &__title {
    font-size: $font-size-base;
    font-weight: $font-weight-semibold;
  }

  &__toggle {
    font-size: $font-size-xs;
    color: $text-tertiary;
  }
}

.anomaly-list {
  max-height: 200px;
  overflow-y: auto;
  margin-top: $space-3;
  padding: $space-2 0;
}

.anomaly-item {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-2 $space-3;
  border-radius: $radius-md;
  font-size: $font-size-sm;
  transition: background $duration-fast;

  &:hover {
    background: $bg-hover;
  }

  &__idx {
    color: $text-tertiary;
    font-weight: $font-weight-medium;
    min-width: 30px;
    font-variant-numeric: tabular-nums;
  }

  &__name {
    color: $text-primary;
    font-weight: $font-weight-medium;
    min-width: 120px;
  }

  &__tags {
    display: flex;
    gap: $space-1;
    flex-wrap: wrap;
  }
}

// ── Data Table ──
.table-wrap {
  flex: 1;
  overflow: hidden;
  padding: 0 $space-4;

  .data-table {
    height: 100%;

    :deep(.ant-table) {
      border-radius: $radius-lg;
      overflow: hidden;
    }

    :deep(.ant-table-tbody > tr > td) {
      color: $text-primary;
      border-bottom: 1px solid $border-light;
      transition: background $duration-fast;
    }

    :deep(.ant-table-tbody > tr:hover > td) {
      background: $bg-hover;
    }

    :deep(.ant-table-body) {
      &::-webkit-scrollbar { width: 4px; }
      &::-webkit-scrollbar-thumb { background: rgba($text-tertiary, 0.25); border-radius: 4px; }
    }
  }

  .mark-tag {
    border-radius: $radius-sm;
    font-size: $font-size-xs;
    padding: 1px $space-2;
  }
}

.inline-anomaly-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $space-1;
  align-items: center;

  :deep(.ant-tag) {
    margin-right: 0;
    line-height: 20px;
  }
}

.cell-muted {
  color: $text-tertiary;
  font-size: $font-size-sm;
}

// ── Empty State ──
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $space-12;

  &__icon {
    width: 80px;
    height: 80px;
    border-radius: 50%;
    background: $bg-muted;
    color: $text-tertiary;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: $space-5;
    opacity: 0.6;
  }

  p {
    font-size: $font-size-md;
    color: $text-tertiary;
    margin: 0;
    text-align: center;
  }
}

// ── Confirm Button ──
.btn-confirm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $space-2;
  height: 46px;
  padding: 0 $space-8;
  border: none;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, $success 0%, #4DD98A 100%);
  color: white;
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba($success, 0.25);
  transition: all $duration-base $ease-smooth;

  &:hover {
    box-shadow: 0 6px 24px rgba($success, 0.35);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
}

</style>

<style lang="scss">
// Unscoped overrides for Ant table rows
.data-table {
  .ant-table-tbody > tr.deleted-row:hover > td,
  .ant-table-tbody > tr.absent-row:hover > td {
    background-color: $danger-light !important;
  }

  .ant-table-tbody > tr.incomplete-row:hover > td {
    background-color: $warning-light !important;
  }
}
</style>
