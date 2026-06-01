<template>
  <a-modal
    :open="open"
    :footer="null"
    :width="'min(96vw, 1200px)'"
    centered
    destroy-on-close
    class="image-preview-modal"
    :mask-closable="true"
    @update:open="onOpenUpdate"
  >
    <template #title>
      <div class="ipm-title">
        <span>{{ title || $t('tasks.imagePreview') }}</span>
        <span v-if="images.length > 1" class="ipm-title-count">{{ currentIndex + 1 }} / {{ images.length }}</span>
      </div>
    </template>

    <div class="ipm-body">
      <div v-if="images.length > 1" class="ipm-nav-side">
        <a-button
          class="ipm-nav-side-btn"
          :disabled="currentIndex <= 0"
          @click="goPrev"
        >
          <LeftOutlined />
        </a-button>
      </div>

      <div
        ref="viewportRef"
        class="ipm-viewport"
        @wheel.prevent="onWheel"
        @mousedown="onPointerDown"
        @mousemove="onPointerMove"
        @mouseup="onPointerUp"
        @mouseleave="onPointerUp"
        @dblclick="onDoubleClick"
      >
        <img
          v-if="currentSrc"
          ref="imgRef"
          :src="currentSrc"
          class="ipm-image"
          :style="imageStyle"
          draggable="false"
          :alt="$t('tasks.imagePreview')"
          @load="onImageLoad"
          @error="onImageError"
        />
        <a-spin v-else class="ipm-loading" />
      </div>

      <div v-if="images.length > 1" class="ipm-nav-side ipm-nav-side--right">
        <a-button
          class="ipm-nav-side-btn"
          :disabled="currentIndex >= images.length - 1"
          @click="goNext"
        >
          <RightOutlined />
        </a-button>
      </div>
    </div>

    <div class="ipm-toolbar">
      <a-space wrap :size="8">
        <a-button size="small" @click="zoomOut">
          <template #icon><ZoomOutOutlined /></template>
          {{ $t('taskEdit.zoomOut') }}
        </a-button>
        <a-button size="small" @click="zoomIn">
          <template #icon><ZoomInOutlined /></template>
          {{ $t('taskEdit.zoomIn') }}
        </a-button>
        <a-button size="small" @click="resetView">
          {{ $t('taskEdit.zoomReset') }}
        </a-button>
        <a-button size="small" @click="rotateLeft">
          <template #icon><RotateLeftOutlined /></template>
          {{ $t('taskEdit.rotate') }}
        </a-button>
        <span class="ipm-scale-hint">{{ scalePercent }}%</span>
      </a-space>
      <span class="ipm-hint">{{ $t('taskEdit.previewGestureHint') }}</span>
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  LeftOutlined,
  RightOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  RotateLeftOutlined,
} from '@ant-design/icons-vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  images: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  title: { type: String, default: '' },
})

const emit = defineEmits(['update:open'])

const { t } = useI18n()

const viewportRef = ref(null)
const imgRef = ref(null)
const currentIndex = ref(0)
const baseScale = ref(1)
const userScale = ref(1)
const rotation = ref(0)
const offsetX = ref(0)
const offsetY = ref(0)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })
const MIN_USER_SCALE = 0.25
const MAX_USER_SCALE = 5
const ZOOM_STEP = 1.2

const currentSrc = computed(() => props.images[currentIndex.value] || '')

const scalePercent = computed(() => Math.round(baseScale.value * userScale.value * 100))

const imageStyle = computed(() => ({
  transform: `translate(${offsetX.value}px, ${offsetY.value}px) rotate(${rotation.value}deg) scale(${baseScale.value * userScale.value})`,
}))

const resetPanZoom = () => {
  userScale.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

/** @param {{ preserveRotation?: boolean }} options */
const fitToViewport = (options = {}) => {
  const { preserveRotation = false } = options
  const viewport = viewportRef.value
  const img = imgRef.value
  if (!viewport || !img || !img.naturalWidth) return

  const vw = viewport.clientWidth
  const vh = viewport.clientHeight
  const nw = img.naturalWidth
  const nh = img.naturalHeight
  const rot = rotation.value % 180 !== 0
  const w = rot ? nh : nw
  const h = rot ? nw : nh
  const padding = 24
  const scale = Math.min((vw - padding) / w, (vh - padding) / h, 1)
  baseScale.value = scale > 0 ? scale : 1
  resetPanZoom()
  if (!preserveRotation) {
    rotation.value = 0
  }
}

const onImageLoad = () => {
  nextTick(() => fitToViewport())
}

const onImageError = (event) => {
  event.target.src =
    'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBkb21pbmFudC1iYXNlbGluZT0ibWlkZGxlIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmJmIiBmb250LXNpemU9IjE2Ij7lm77niYfliqDovb3lpLHotKU88L3RleHQ+PC9zdmc+'
}

const clampUserScale = (value) => Math.max(MIN_USER_SCALE, Math.min(MAX_USER_SCALE, value))

const zoomIn = () => {
  userScale.value = clampUserScale(userScale.value * ZOOM_STEP)
}

const zoomOut = () => {
  userScale.value = clampUserScale(userScale.value / ZOOM_STEP)
}

const resetView = () => {
  rotation.value = 0
  fitToViewport()
}

const rotateLeft = () => {
  rotation.value = (rotation.value - 90 + 360) % 360
  nextTick(() => fitToViewport({ preserveRotation: true }))
}

const goPrev = () => {
  if (currentIndex.value > 0) {
    currentIndex.value -= 1
    rotation.value = 0
    nextTick(() => fitToViewport())
  }
}

const goNext = () => {
  if (currentIndex.value < props.images.length - 1) {
    currentIndex.value += 1
    rotation.value = 0
    nextTick(() => fitToViewport())
  }
}

const onWheel = (event) => {
  const delta = event.deltaY > 0 ? 1 / ZOOM_STEP : ZOOM_STEP
  userScale.value = clampUserScale(userScale.value * delta)
}

const onPointerDown = (event) => {
  if (event.button !== 0) return
  dragging.value = true
  dragStart.value = {
    x: event.clientX,
    y: event.clientY,
    ox: offsetX.value,
    oy: offsetY.value,
  }
}

const onPointerMove = (event) => {
  if (!dragging.value) return
  offsetX.value = dragStart.value.ox + (event.clientX - dragStart.value.x)
  offsetY.value = dragStart.value.oy + (event.clientY - dragStart.value.y)
}

const onPointerUp = () => {
  dragging.value = false
}

const onDoubleClick = () => {
  if (userScale.value > 1.05) {
    userScale.value = 1
    offsetX.value = 0
    offsetY.value = 0
  } else {
    userScale.value = clampUserScale(2)
  }
}

const onOpenUpdate = (value) => {
  emit('update:open', value)
}

const handleKeydown = (event) => {
  if (!props.open) return
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    goPrev()
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    goNext()
  } else if (event.key === 'Escape') {
    emit('update:open', false)
  }
}

watch(
  () => props.open,
  (value) => {
    if (value) {
      currentIndex.value = Math.min(
        Math.max(props.initialIndex, 0),
        Math.max(props.images.length - 1, 0)
      )
      rotation.value = 0
      document.addEventListener('keydown', handleKeydown)
      nextTick(() => fitToViewport())
    } else {
      document.removeEventListener('keydown', handleKeydown)
    }
  }
)

watch(
  () => props.initialIndex,
  (value) => {
    if (props.open) {
      currentIndex.value = value
      nextTick(() => fitToViewport())
    }
  }
)

const onWindowResize = () => {
  if (props.open) {
    fitToViewport({ preserveRotation: true })
  }
}

onMounted(() => {
  window.addEventListener('resize', onWindowResize)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', onWindowResize)
})
</script>

<style lang="scss" scoped>
.ipm-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ipm-title-count {
  font-size: $font-size-sm;
  color: $text-secondary;
  font-weight: $font-weight-normal;
}

.ipm-body {
  display: flex;
  align-items: stretch;
  gap: 8px;
  min-height: 52vh;
}

.ipm-nav-side {
  display: flex;
  align-items: center;
  flex-shrink: 0;

  &--right {
    justify-content: flex-end;
  }
}

.ipm-nav-side-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ipm-viewport {
  flex: 1;
  min-height: 52vh;
  max-height: 72vh;
  background: $bg-muted;
  border-radius: $radius-lg;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  user-select: none;
  touch-action: none;

  &:active {
    cursor: grabbing;
  }
}

.ipm-image {
  max-width: none;
  max-height: none;
  transform-origin: center center;
  will-change: transform;
  pointer-events: none;
}

.ipm-loading {
  margin: auto;
}

.ipm-toolbar {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ipm-scale-hint {
  font-size: $font-size-sm;
  color: $text-secondary;
  min-width: 48px;
}

.ipm-hint {
  font-size: $font-size-sm;
  color: $text-tertiary;
}
</style>

<style lang="scss">
.image-preview-modal {
  .ant-modal-body {
    padding-top: 8px;
  }
}
</style>
