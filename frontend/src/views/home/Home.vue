<template>
  <div class="home">
    <div class="home__initial">
      <section class="intro-hero">
        <div class="intro-hero__stage">
          <div class="intro-hero__copy">
            <h1 class="intro-hero__title">{{ $t('home.uploadTitle') }}</h1>
            <p class="intro-hero__desc">{{ $t('home.uploadDesc') }}</p>
            <ol class="intro-hero__steps">
              <li v-for="(step, index) in guideSteps" :key="index">
                <span class="intro-hero__step-num">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="intro-hero__step-title">{{ step.title }}</span>
              </li>
            </ol>
          </div>
          <div class="intro-hero__art">
            <HomeHeroIllustration />
          </div>
        </div>
        <div class="intro-hero__meta country-context">
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
          <a-space size="small">
            <a-button type="link" size="small" @click="$router.push('/print-sheet')">
              {{ $t('home.printSheet') }}
            </a-button>
            <a-button type="link" size="small" @click="openWorkingCountryPicker">
              {{ $t('home.changeCountry') }}
            </a-button>
          </a-space>
        </div>
      </section>

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

        <div v-if="uploading" class="upload-progress">
          <div class="processing-ring"></div>
          <p v-if="uploadProgress.active" class="upload-progress__title">
            {{ $t('home.uploadingProgress', { uploaded: uploadProgress.uploaded, total: uploadProgress.total }) }}
          </p>
          <p v-else class="upload-progress__title">{{ $t('home.recognizing') }}</p>
          <div v-if="uploadProgress.active && uploadProgress.total > 0" class="upload-progress__bar">
            <div
              class="upload-progress__bar-fill"
              :style="{ width: `${Math.round((uploadProgress.uploaded / uploadProgress.total) * 100)}%` }"
            />
          </div>
          <p v-if="progressRowCount > 0" class="upload-progress__rows">
            {{ $t('home.progressRows', { count: progressRowCount }) }}
          </p>
          <p v-if="fileList.length > 0" class="upload-progress__files">
            {{ $t('home.selectedCount', { count: fileList.length }) }}
            · {{ $t('home.totalSize', { size: totalSizeDisplay }) }}
          </p>
          <p v-if="networkUnstable" class="upload-progress__network">{{ $t('home.networkUnstable') }}</p>
          <p class="upload-progress__hint">{{ $t('home.progressThenDetail') }}</p>
          <p class="upload-progress__bg">{{ $t('home.backgroundRecognitionHint') }}</p>
          <div class="upload-progress__actions">
            <button type="button" class="btn-cancel-recognize" @click="cancelRecognition">
              {{ $t('home.runInBackground') }}
            </button>
            <button type="button" class="btn-cancel-recognize" @click="handleClear">
              {{ $t('home.uploadAgain') }}
            </button>
          </div>
        </div>

        <template v-else>
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

          <div class="upload-actions">
            <button
              class="btn-recognize"
              :class="{ 'btn-recognize--loading': isPreparingImages }"
              :disabled="!canStartRecognize"
              @click="handleUpload"
            >
              <svg v-if="!isPreparingImages" width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M12 16V4M12 4L8 8.5M12 4L16 8.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M4 14V18C4 19.1046 4.89543 20 6 20H18C19.1046 20 20 19.1046 20 18V14" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <span v-if="isPreparingImages" class="btn-spinner"></span>
              {{ isPreparingImages ? $t('home.compressingImage') : $t('home.startRecognize') }}
            </button>
            <button class="btn-clear" @click="handleClear">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                <path d="M3 6H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                <path d="M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M5 6L6 20C6 20.5523 6.44772 21 7 21H17C17.5523 21 18 20.5523 18 20L19 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              {{ $t('home.clear') }}
            </button>
          </div>
        </template>
      </div>
    </div>

    <ImagePreviewModal
      v-model:open="previewVisible"
      v-model:index="previewIndex"
      :images="previewImageUrls"
      :image-names="previewImageNames"
      :title="previewTitle"
      :auto-orient-enabled="false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, h } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal as aModal } from 'ant-design-vue'
import { compressImage, getImageHash, getFileSizeDisplay } from '@/utils/image'
import HomeHeroIllustration from '@/components/HomeHeroIllustration.vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import { useCountryStore } from '@/stores/country'
import { getTaskProgress } from '@/api/task'
import {
  submitBackgroundRecognition,
  pollRecognitionUntilDone,
  retryTaskRecognition,
  persistBgTaskId,
  clearBgTaskId,
  getPersistedBgTaskId,
} from '@/utils/backgroundRecognition'
import { useWorkingCountryPicker } from '@/composables/useWorkingCountryPicker'
import { formatCountryLabel } from '@/utils/countryLabels'
import { translateErrorMessage, showHomeUploadError } from '@/utils/translateError'

const router = useRouter()
const { requestOpenCountryPicker } = useWorkingCountryPicker()
const { t, locale } = useI18n()
const countryStore = useCountryStore()

const localizedWorkingCountryLabel = computed(() => {
  void locale.value
  const code = countryStore.workingCountry || 'default'
  const found = (countryStore.options || []).find((item) => item.code === code)
  if (found) return formatCountryLabel(found.code, found.flag, found.name)
  return countryStore.workingCountryLabel
})

const fileList = ref([])
const uploading = ref(false)
const currentTaskId = ref(null)
const processedHashes = ref(new Set())
const previewVisible = ref(false)
const previewIndex = ref(0)
const previewTitle = ref('')
const previewImageUrls = ref([])
const previewImageNames = ref([])
const stopPolling = ref(false)
const progressRowCount = ref(0)
const uploadProgress = ref({ uploaded: 0, total: 0, active: false })
const networkUnstable = ref(false)

const applyProgress = (p) => {
  if (p.taskId) {
    currentTaskId.value = p.taskId
    persistBgTaskId(p.taskId)
  }
  if (p.phase !== 'refresh') {
    progressRowCount.value = p.rowCount || progressRowCount.value || 0
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
}

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

const finishRecognitionSuccess = (result) => {
  clearUploadSelection()
  currentTaskId.value = result.taskId
  if (result.records.length > 0) {
    clearBgTaskId()
    message.success(t('home.recognizeSuccess', { count: result.records.length }))
    router.push(`/tasks/${result.taskId}`)
    return
  }
  message.warning(t('home.noRecordsFound'))
}

const showRecognitionFailure = (taskId, error) => {
  clearUploadSelection()
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
    content: h('div', [
      h('p', { style: { marginBottom: '0' } }, reason),
      h('p', {
        style: {
          marginTop: '12px',
          marginBottom: '0',
          color: 'rgba(0,0,0,0.55)',
          fontSize: '13px',
          lineHeight: '1.6',
        },
      }, t('home.recognitionFailureHint')),
    ]),
    okText: t('home.recognitionRetry'),
    cancelText: t('home.recognitionReupload'),
    centered: true,
    width: 480,
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
  currentTaskId.value = taskId
  persistBgTaskId(taskId)
  progressRowCount.value = 0
  try {
    const result = await retryTaskRecognition(taskId, {
      shouldAbort: () => stopPolling.value,
      onProgress: applyProgress,
    })
    if (result.aborted) return
    finishRecognitionSuccess(result)
  } catch (error) {
    if (String(error?.message || '').includes(t('errors.taskAccessDenied'))) {
      clearBgTaskId()
    }
    if (!stopPolling.value) {
      console.error('Recognition error:', error)
      showRecognitionFailure(error?.taskId || taskId, error)
    }
  } finally {
    uploading.value = false
    networkUnstable.value = false
    uploadProgress.value = { uploaded: 0, total: 0, active: false }
  }
}

const resumeBackgroundPolling = async (taskId) => {
  stopPolling.value = false
  uploading.value = true
  currentTaskId.value = taskId
  progressRowCount.value = 0
  try {
    const result = await pollRecognitionUntilDone(taskId, {
      shouldAbort: () => stopPolling.value,
      onProgress: applyProgress,
    })
    if (result.aborted) return
    finishRecognitionSuccess(result)
  } catch (error) {
    if (String(error?.message || '').includes(t('errors.taskAccessDenied'))) {
      clearBgTaskId()
    }
    if (!stopPolling.value) {
      showRecognitionFailure(error?.taskId || taskId, error)
    }
  } finally {
    uploading.value = false
    networkUnstable.value = false
  }
}

const guideSteps = computed(() => [
  { title: t('home.guideStep1Title'), desc: t('home.guideStep1Desc') },
  { title: t('home.guideStep2Title'), desc: t('home.guideStep2Desc') },
  { title: t('home.guideStep3Title'), desc: t('home.guideStep3Desc') },
])

const resetState = () => {
  stopPolling.value = true
  clearUploadSelection()
  uploading.value = false
  currentTaskId.value = null
  previewVisible.value = false
  previewIndex.value = 0
  previewTitle.value = ''
  progressRowCount.value = 0
  uploadProgress.value = { uploaded: 0, total: 0, active: false }
  networkUnstable.value = false
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
  if (!persistedTaskId) return
  try {
    const progressRes = await getTaskProgress(persistedTaskId)
    const status = progressRes.data?.status
    if (status === 'processing') {
      void resumeBackgroundPolling(persistedTaskId)
    } else if (status === 'failed') {
      currentTaskId.value = persistedTaskId
      showRecognitionFailure(persistedTaskId, { message: t('home.recognitionError') })
    } else {
      clearBgTaskId()
    }
  } catch {
    clearBgTaskId()
  }
})

onBeforeUnmount(() => {
  stopPolling.value = true
  fileList.value.forEach(revokePreviewUrl)
})

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

const revokePreviewUrl = (file) => {
  if (file && file.url && file.url.startsWith('blob:')) URL.revokeObjectURL(file.url)
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
  if (uploading.value) return
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

const clearUploadSelection = () => {
  fileList.value.forEach(revokePreviewUrl)
  fileList.value = []
  processedHashes.value = new Set()
  if (previewVisible.value) {
    previewVisible.value = false
    previewIndex.value = 0
    previewTitle.value = ''
    previewImageUrls.value = []
    previewImageNames.value = []
  }
}

const handleFilePreview = (file) => {
  const target = fileList.value.find((item) => item.uid === file.uid)
    || fileList.value.find((item) => item.name === file.name)
    || file
  if (target.isPdf || (!target.url && !target.thumbUrl)) {
    message.info(t('home.pdfNoPreview'))
    return
  }
  const imageFiles = fileList.value.filter(
    (item) => !item.isPdf && (item.url || item.thumbUrl),
  )
  const index = imageFiles.findIndex((item) => item.uid === target.uid)
  if (index < 0) return
  const urls = imageFiles.map((item) => item.url || item.thumbUrl).filter(Boolean)
  if (!urls.length) return
  previewImageUrls.value = urls
  previewImageNames.value = imageFiles.map((item) => item.name || '')
  previewTitle.value = target.name || ''
  previewIndex.value = index
  previewVisible.value = true
}

const handleFileRemove = (file) => {
  if (uploading.value) return false
  const target = fileList.value.find((item) => item.uid === file.uid)
  if (target) {
    revokePreviewUrl(target)
    if (target.hash) {
      const nextHashes = new Set(processedHashes.value)
      nextHashes.delete(target.hash)
      processedHashes.value = nextHashes
    }
  }
  fileList.value = fileList.value.filter((item) => item.uid !== file.uid)
  if (previewVisible.value && previewTitle.value === file.name) {
    previewVisible.value = false
    previewIndex.value = 0
    previewTitle.value = ''
    previewImageUrls.value = []
    previewImageNames.value = []
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
  progressRowCount.value = 0
  uploadProgress.value = { uploaded: 0, total: 0, active: false }
  networkUnstable.value = false

  try {
    const result = await submitBackgroundRecognition(
      filesToUpload.map((file) => file.raw),
      {
        shouldAbort: () => stopPolling.value,
        onProgress: applyProgress,
      },
    )
    if (result.aborted) return
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
        showRecognitionFailure(taskId, error)
      } else {
        clearUploadSelection()
        showHomeUploadError(error)
      }
    }
  } finally {
    uploading.value = false
    uploadProgress.value = { uploaded: 0, total: 0, active: false }
    networkUnstable.value = false
  }
}

const openWorkingCountryPicker = () => {
  requestOpenCountryPicker()
}

const handleClear = () => {
  const hasContent = fileList.value.length > 0 || uploading.value
  if (!hasContent) {
    resetState()
    return
  }
  aModal.confirm({
    title: t('home.clearConfirmTitle'),
    content: t('home.clearConfirmContent'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: () => {
      clearBgTaskId()
      resetState()
    },
  })
}
</script>

<style lang="scss" scoped>
// ═══════════════════════════════════════════════════════════
// Home Page — Atelier v4
// ═══════════════════════════════════════════════════════════

.home {
  padding: $space-5;
  min-height: calc(100vh - #{$header-height});
}

.intro-hero {
  overflow: hidden;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba($border, 0.8);
  box-shadow: $shadow-card;

  &__stage {
    position: relative;
    display: grid;
    grid-template-columns: minmax(280px, 0.92fr) minmax(320px, 1.08fr);
    min-height: 176px;
    background:
      linear-gradient(115deg, #FBF9FF 0%, #F7F4F0 48%, #EEF1FF 100%);
  }

  &__copy {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 8px;
    padding: 22px 8px 20px 28px;
    min-width: 0;
  }

  &__title {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: -0.035em;
    line-height: 1.15;
    color: $text-strong;
  }

  &__desc {
    margin: 0;
    max-width: 36em;
    font-size: 13px;
    line-height: 1.5;
    color: $text-secondary;
  }

  &__steps {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 18px;
    margin: 6px 0 0;
    padding: 0;
    list-style: none;
  }

  &__steps li {
    display: flex;
    align-items: baseline;
    gap: 6px;
    min-width: 0;
  }

  &__step-num {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.04em;
    color: $primary;
    font-variant-numeric: tabular-nums;
  }

  &__step-title {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
  }

  &__art {
    position: relative;
    min-height: 176px;
    pointer-events: none;
  }

  &__art :deep(svg) {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
  }

  &__meta {
    margin: 0;
    border-radius: 0;
    border: 0;
    border-top: 1px solid $border-light;
    background: #fff;
    padding: 8px 20px 8px 28px;
  }

  @media (max-width: 900px) {
    &__stage {
      grid-template-columns: 1fr;
      min-height: 0;
    }

    &__copy {
      padding: 18px 20px 8px;
    }

    &__art {
      min-height: 168px;
    }
  }
}

// ── Initial Mode ──
.home__initial {
  display: flex;
  flex-direction: column;
  gap: $space-3;
  width: 100%;
  max-width: 100%;
  margin: 0;
}

.country-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $space-4;
  padding: $space-2 $space-4;
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

  &--primary {
    border-color: rgba($primary, 0.22);
    border-top-color: $primary;
  }
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

.upload-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $space-3;
  min-height: 180px;
  margin: 0;
  padding: $space-8 $space-5;
  background: linear-gradient(135deg, $primary-lighter 0%, $bg-muted 100%);
  border: 2px dashed rgba($primary, 0.25);
  border-radius: $radius-xl;
  text-align: center;

  p {
    margin: 0;
  }

  &__title {
    font-size: $font-size-md;
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  &__rows {
    font-weight: 600;
    color: $primary;
  }

  &__network {
    color: #d48806;
  }

  &__files {
    font-size: $font-size-sm;
    color: $text-secondary;
  }

  &__hint,
  &__bg {
    font-size: $font-size-sm;
    line-height: 1.5;
    color: $text-secondary;
    max-width: 420px;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: $space-2;
    margin-top: $space-1;
  }

  &__bar {
    width: 100%;
    max-width: 280px;
    height: 6px;
    border-radius: 999px;
    background: rgba($primary, 0.15);
    overflow: hidden;
  }

  &__bar-fill {
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
  margin-top: $space-1;
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

</style>
