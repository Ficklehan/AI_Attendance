<template>
  <div
    class="image-preview-viewer"
    :class="{
      'image-preview-viewer--compact': compact,
      'image-preview-viewer--dock': layout === 'dock',
    }"
  >
    <div class="ipv-head" v-if="showHeader">
      <div class="ipv-title">
        <span>{{ title || $t('tasks.imagePreview') }}</span>
        <span v-if="imageList.length > 1" class="ipv-title-count">
          {{ currentIndex + 1 }} / {{ imageList.length }}
        </span>
      </div>
      <div class="ipv-head-actions">
        <slot name="head-actions" />
      </div>
    </div>

    <div
      v-if="imageList.length > 1"
      class="ipv-thumbs"
      :class="{ 'ipv-thumbs--modal': layout === 'modal' }"
    >
      <button
        v-for="(url, thumbIndex) in imageList"
        :key="`${url}-${thumbIndex}`"
        type="button"
        class="ipv-thumb"
        :class="{ 'ipv-thumb--active': thumbIndex === currentIndex }"
        :title="`${thumbIndex + 1} / ${imageList.length}`"
        @click="selectImage(thumbIndex)"
      >
        <img :src="url" :alt="`${thumbIndex + 1}`" draggable="false" />
      </button>
    </div>

    <div class="ipv-body" :class="{ 'ipv-body--dock': layout === 'dock' }">
      <div
        v-if="imageList.length > 1 && layout !== 'dock'"
        class="ipv-nav-side"
      >
        <a-button
          class="ipv-nav-side-btn"
          :disabled="currentIndex <= 0"
          @mousedown.stop
          @click.stop="goPrev"
        >
          <LeftOutlined />
        </a-button>
      </div>

      <div
        ref="viewportRef"
        class="ipv-viewport"
        :style="viewportStyle"
        @wheel.prevent="onWheel"
        @mousedown="onPointerDown"
        @mousemove="onPointerMove"
        @mouseup="onPointerUp"
        @mouseleave="onPointerUp"
        @dblclick="onDoubleClick"
      >
        <div v-if="loading || orienting" class="ipv-orienting">
          <a-spin size="small" />
          <span>{{ loading ? $t('common.loading') : $t('taskEdit.autoOrienting') }}</span>
        </div>
        <img
          v-if="currentSrc"
          ref="imgRef"
          :key="currentSrc"
          :src="currentSrc"
          class="ipv-image"
          :class="{ 'ipv-image--pending': loading }"
          :style="imageStyle"
          draggable="false"
          :alt="$t('tasks.imagePreview')"
          @load="onImageLoad"
          @error="onImageError"
        />
        <a-spin v-else class="ipv-loading" />

        <template v-if="imageList.length > 1 && layout === 'dock'">
          <a-button
            class="ipv-float-nav ipv-float-nav--left"
            shape="circle"
            size="small"
            :disabled="currentIndex <= 0"
            @mousedown.stop
            @click.stop="goPrev"
          >
            <LeftOutlined />
          </a-button>
          <a-button
            class="ipv-float-nav ipv-float-nav--right"
            shape="circle"
            size="small"
            :disabled="currentIndex >= imageList.length - 1"
            @mousedown.stop
            @click.stop="goNext"
          >
            <RightOutlined />
          </a-button>
        </template>
      </div>

      <div
        v-if="imageList.length > 1 && layout !== 'dock'"
        class="ipv-nav-side ipv-nav-side--right"
      >
        <a-button
          class="ipv-nav-side-btn"
          :disabled="currentIndex >= imageList.length - 1"
          @mousedown.stop
          @click.stop="goNext"
        >
          <RightOutlined />
        </a-button>
      </div>
    </div>

    <div class="ipv-toolbar" :class="{ 'ipv-toolbar--dock': layout === 'dock' }">
      <a-space wrap :size="6">
        <a-tooltip :title="$t('taskEdit.zoomOut')">
          <a-button size="small" @click="zoomOut">
            <template #icon><ZoomOutOutlined /></template>
            <span v-if="layout !== 'dock'">{{ $t('taskEdit.zoomOut') }}</span>
          </a-button>
        </a-tooltip>
        <a-tooltip :title="$t('taskEdit.zoomIn')">
          <a-button size="small" @click="zoomIn">
            <template #icon><ZoomInOutlined /></template>
            <span v-if="layout !== 'dock'">{{ $t('taskEdit.zoomIn') }}</span>
          </a-button>
        </a-tooltip>
        <a-tooltip :title="$t('taskEdit.zoomReset')">
          <a-button size="small" @click="resetView">
            <span v-if="layout === 'dock'">1:1</span>
            <span v-else>{{ $t('taskEdit.zoomReset') }}</span>
          </a-button>
        </a-tooltip>
        <a-tooltip :title="$t('taskEdit.rotate')">
          <a-button size="small" @click="rotateLeft">
            <template #icon><RotateLeftOutlined /></template>
            <span v-if="layout !== 'dock'">{{ $t('taskEdit.rotate') }}</span>
          </a-button>
        </a-tooltip>
        <a-button
          v-if="autoOrientEnabled && layout !== 'dock'"
          size="small"
          :loading="orienting"
          @click="rerunAutoOrient"
        >
          {{ $t('taskEdit.autoOrient') }}
        </a-button>
        <a-switch
          v-if="showAutoOrientToggle"
          :checked="autoOrientEnabled"
          size="small"
          :checked-children="$t('taskEdit.autoOrientOn')"
          :un-checked-children="$t('taskEdit.autoOrientOff')"
          @change="(checked) => emit('update:autoOrientEnabled', checked)"
        />
        <span class="ipv-scale-hint">{{ scalePercent }}%</span>
      </a-space>
      <span v-if="showGestureHint" class="ipv-hint">{{ $t('taskEdit.previewGestureHint') }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, toRef, watch, onMounted, onUnmounted } from 'vue'
import {
  LeftOutlined,
  RightOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  RotateLeftOutlined,
} from '@ant-design/icons-vue'
import { useImagePreviewViewer } from '@/composables/useImagePreviewViewer'

const props = defineProps({
  images: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  index: { type: Number, default: undefined },
  title: { type: String, default: '' },
  active: { type: Boolean, default: true },
  loading: { type: Boolean, default: false },
  autoOrientEnabled: { type: Boolean, default: true },
  showAutoOrientToggle: { type: Boolean, default: true },
  showHeader: { type: Boolean, default: true },
  showGestureHint: { type: Boolean, default: true },
  compact: { type: Boolean, default: false },
  layout: { type: String, default: 'modal' },
  viewportMinHeight: { type: String, default: '52vh' },
  viewportMaxHeight: { type: String, default: '72vh' },
})

const emit = defineEmits(['update:autoOrientEnabled', 'update:index'])

const {
  viewportRef,
  imgRef,
  currentIndex,
  currentSrc,
  scalePercent,
  imageStyle,
  orienting,
  images: imageList,
  onImageLoad,
  onImageError,
  zoomIn,
  zoomOut,
  resetView,
  rotateLeft,
  goPrev,
  goNext,
  goToIndex,
  onWheel,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onDoubleClick,
  rerunAutoOrient,
  fitToViewport,
} = useImagePreviewViewer({
  images: toRef(props, 'images'),
  initialIndex: toRef(props, 'initialIndex'),
  index: toRef(props, 'index'),
  active: toRef(props, 'active'),
  autoOrientEnabled: toRef(props, 'autoOrientEnabled'),
  viewportMinHeight: toRef(props, 'viewportMinHeight'),
  fitMode: computed(() => (props.layout === 'dock' ? 'dock' : 'modal')),
})

const viewportStyle = computed(() => {
  if (props.layout === 'dock') {
    return {
      width: '100%',
      flex: '1 1 0',
      minHeight: '0',
      maxHeight: '100%',
    }
  }
  return {
    minHeight: props.viewportMinHeight,
    maxHeight: props.viewportMaxHeight,
  }
})

watch(
  () => props.active,
  (value) => {
    if (value) {
      fitToViewport({ preserveRotation: true })
    }
  },
)

const selectImage = (thumbIndex) => {
  goToIndex(thumbIndex, { force: true })
}

const isControlledIndex = computed(() => props.index !== undefined && props.index !== null)

watch(
  currentIndex,
  (value) => {
    if (!isControlledIndex.value) return
    if (props.index !== value) {
      emit('update:index', value)
    }
  },
  { flush: 'sync' },
)

let viewportResizeObserver = null

onMounted(() => {
  if (props.layout !== 'dock') return
  viewportResizeObserver = new ResizeObserver(() => {
    if (props.active) {
      fitToViewport({ preserveRotation: true })
    }
  })
  if (viewportRef.value) {
    viewportResizeObserver.observe(viewportRef.value)
  }
})

onUnmounted(() => {
  viewportResizeObserver?.disconnect()
  viewportResizeObserver = null
})

watch(viewportRef, (el, prev) => {
  if (!viewportResizeObserver || props.layout !== 'dock') return
  if (prev) viewportResizeObserver.unobserve(prev)
  if (el) viewportResizeObserver.observe(el)
})
</script>

<style lang="scss" scoped>
.image-preview-viewer {
  display: flex;
  flex-direction: column;
  min-height: 0;

  &--compact {
    .ipv-body {
      flex: 1 1 auto;
      min-height: 0;
    }

    .ipv-viewport {
      flex: 1 1 auto;
      min-height: 0;
      max-height: none;
    }
  }

  &--dock {
    flex: 1 1 0;
    height: 100%;
    max-height: 100%;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;

    .ipv-head {
      flex: 0 0 auto;
      background: linear-gradient(180deg, #fafbfc 0%, $bg-surface 100%);
    }

    .ipv-thumbs {
      flex: 0 0 auto;
    }

    .ipv-body {
      flex: 1 1 0;
      min-height: 0;
      overflow: hidden;
    }

    .ipv-viewport {
      flex: 1 1 0;
      min-height: 0 !important;
      max-height: 100%;
      height: auto;
      margin: 0 4px;
      border-radius: $radius-md;
      border: 1px solid rgba($border, 0.45);
    }

    .ipv-toolbar {
      flex: 0 0 auto;
      flex-shrink: 0;
      border-top: 1px solid rgba($border, 0.45);
      background: $bg-surface;
    }

    .ipv-thumb {
      width: 44px;
      height: 44px;
    }
  }
}

.ipv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px 8px;
  border-bottom: 1px solid rgba($border, 0.6);
}

.image-preview-viewer--dock .ipv-head {
  padding: 6px 8px 4px;
}

.ipv-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: $font-weight-semibold;
  font-size: $font-size-sm;
  color: $text-strong;
}

.ipv-title-count {
  font-size: $font-size-xs;
  color: $text-secondary;
  font-weight: $font-weight-normal;
}

.ipv-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ipv-body {
  display: flex;
  align-items: stretch;
  gap: 8px;
  flex: 1 1 auto;
  min-height: 0;
  padding: 10px 10px 0;

  &--dock {
    padding: 6px 6px 0;
  }
}

.ipv-nav-side {
  display: flex;
  align-items: center;
  flex-shrink: 0;

  &--right {
    justify-content: flex-end;
  }
}

.ipv-nav-side-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ipv-viewport {
  position: relative;
  flex: 1;
  min-height: 52vh;
  background: $bg-muted;
  border-radius: $radius-md;
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

.ipv-float-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
  pointer-events: auto;
  opacity: 0.9;
  box-shadow: $shadow-card;

  &--left {
    left: 8px;
  }

  &--right {
    right: 8px;
  }

  &:hover {
    opacity: 1;
  }
}

.ipv-orienting {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.45);
  color: $text-secondary;
  font-size: $font-size-xs;
  pointer-events: none;
}

.ipv-image {
  max-width: none;
  max-height: none;
  transform-origin: center center;
  will-change: transform;
  pointer-events: none;

  &--pending {
    visibility: hidden;
  }
}

.ipv-loading {
  margin: auto;
}

.ipv-toolbar {
  margin-top: 8px;
  padding: 0 10px 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px;

  &--dock {
    margin-top: 4px;
    padding: 0 6px 6px;
  }
}

.ipv-scale-hint {
  font-size: $font-size-xs;
  color: $text-secondary;
  min-width: 40px;
}

.ipv-hint {
  font-size: $font-size-sm;
  color: $text-tertiary;
}

.ipv-thumbs {
  display: flex;
  gap: 6px;
  padding: 6px 8px 0;
  overflow-x: auto;
  flex-shrink: 0;

  &--modal {
    padding: 0 10px 8px;
  }

  &::-webkit-scrollbar {
    height: 4px;
  }
}

.ipv-thumb {
  flex: 0 0 auto;
  width: 52px;
  height: 52px;
  padding: 0;
  border: 2px solid rgba($border, 0.8);
  border-radius: $radius-md;
  background: $bg-muted;
  cursor: pointer;
  overflow: hidden;
  transition: border-color $duration-fast, box-shadow $duration-fast;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
    pointer-events: none;
  }

  &:hover {
    border-color: rgba($primary, 0.55);
  }

  &--active {
    border-color: $primary;
    box-shadow: 0 0 0 2px rgba($primary, 0.18);
  }
}
</style>
