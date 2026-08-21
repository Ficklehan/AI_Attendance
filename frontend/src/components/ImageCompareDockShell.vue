<template>
  <div ref="dockColumnRef" class="image-compare-layout__dock">
    <div
      class="image-compare-layout__resizer"
      role="separator"
      aria-orientation="vertical"
      :aria-label="$t('taskEdit.previewResizeHint')"
      :title="$t('taskEdit.previewResizeHint')"
      @pointerdown="startResize"
    >
      <span class="image-compare-layout__resizer-grip" aria-hidden="true" />
    </div>

    <ImagePreviewDock
      v-model:open="dockOpen"
      v-model:index="dockIndex"
      :images="images"
      :loading="loading"
      :title="title"
      @fullscreen="emit('fullscreen')"
    />
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ImagePreviewDock from '@/components/ImagePreviewDock.vue'
import { useImageDockResize } from '@/composables/useImageDockResize'
import { useImageDockViewportHeight } from '@/composables/useImageDockViewportHeight'

const DOCK_WIDTH_KEY = 'clockai.imagePreviewDockWidth.v7'
const LEGACY_DOCK_WIDTH_KEY = 'attendance.imagePreviewDockWidth.v7'
if (typeof sessionStorage !== 'undefined' && !sessionStorage.getItem(DOCK_WIDTH_KEY)) {
  const legacy = sessionStorage.getItem(LEGACY_DOCK_WIDTH_KEY)
  if (legacy) sessionStorage.setItem(DOCK_WIDTH_KEY, legacy)
}

const props = defineProps({
  open: { type: Boolean, default: false },
  index: { type: Number, default: 0 },
  images: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
})

const emit = defineEmits(['update:open', 'update:index', 'fullscreen'])

const dockColumnRef = ref(null)
const dockOpen = ref(props.open)
const dockIndex = ref(props.index)

const { startResize, bindDock, initWidth, teardown } = useImageDockResize(DOCK_WIDTH_KEY)
const { sync: syncDockHeight, bind: bindDockHeight, unbind: unbindDockHeight } = useImageDockViewportHeight(dockColumnRef)

watch(
  () => props.open,
  (value) => {
    dockOpen.value = value
  },
)

watch(
  () => props.index,
  (value) => {
    if (value !== dockIndex.value) {
      dockIndex.value = value
    }
  },
)

watch(dockOpen, (value) => {
  emit('update:open', value)
})

watch(dockIndex, (value) => {
  if (value !== props.index) {
    emit('update:index', value)
  }
})

const syncDockLayout = async () => {
  await nextTick()
  if (dockColumnRef.value) {
    bindDockHeight()
    syncDockHeight()
    bindDock(dockColumnRef.value)
    requestAnimationFrame(() => {
      syncDockHeight()
      requestAnimationFrame(syncDockHeight)
    })
  } else {
    initWidth()
  }
}

watch(
  () => props.open,
  (value) => {
    if (value) {
      syncDockLayout()
    } else {
      unbindDockHeight()
    }
  },
  { immediate: true },
)

onMounted(() => {
  if (props.open) {
    syncDockLayout()
  }
})

onBeforeUnmount(() => {
  unbindDockHeight()
  teardown()
})
</script>
