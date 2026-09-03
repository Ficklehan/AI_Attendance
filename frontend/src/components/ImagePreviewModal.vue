<template>
  <a-modal
    :open="open"
    :footer="null"
    :width="fullscreen ? '100vw' : 'min(96vw, 1200px)'"
    :title="title || undefined"
    :centered="!fullscreen"
    destroy-on-close
    class="image-preview-modal"
    :wrap-class-name="fullscreen ? 'image-preview-modal-wrap--fullscreen' : ''"
    :mask-closable="true"
    @update:open="onOpenUpdate"
  >
    <ImagePreviewViewer
      v-if="open"
      :key="viewerKey"
      :images="images"
      v-bind="viewerBindings"
      :title="title"
      :active="open"
      :auto-orient-enabled="autoOrientEnabled"
      :show-auto-orient-toggle="false"
      :show-header="false"
      :compact="fullscreen"
      :viewport-min-height="viewportMinHeight"
      :viewport-max-height="viewportMaxHeight"
      :image-names="imageNames"
      @update:index="onIndexUpdate"
    />
  </a-modal>
</template>

<script setup>
import { watch, computed, onMounted, onUnmounted } from 'vue'
import ImagePreviewViewer from '@/components/ImagePreviewViewer.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  images: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  index: { type: Number, default: undefined },
  title: { type: String, default: '' },
  autoOrientEnabled: { type: Boolean, default: true },
  fullscreen: { type: Boolean, default: true },
  imageNames: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:open', 'update:index'])

const viewerKey = computed(() => {
  const first = props.images?.[0] || ''
  return `${props.open ? 'open' : 'closed'}-${props.index ?? props.initialIndex}-${first}`
})

const autoOrientEnabled = computed(() => props.autoOrientEnabled)

const viewerBindings = computed(() => {
  if (props.index !== undefined && props.index !== null) {
    return { index: props.index }
  }
  return { initialIndex: props.initialIndex }
})

const viewportMinHeight = computed(() => (
  props.fullscreen ? 'calc(100vh - 148px)' : '52vh'
))

const viewportMaxHeight = computed(() => (
  props.fullscreen ? 'calc(100vh - 148px)' : '72vh'
))

const onIndexUpdate = (value) => {
  emit('update:index', value)
}

const onOpenUpdate = (value) => {
  emit('update:open', value)
}

const handleKeydown = (event) => {
  if (!props.open) return
  if (event.key === 'Escape') {
    emit('update:open', false)
  }
}

watch(
  () => props.open,
  (value) => {
    if (value) {
      document.addEventListener('keydown', handleKeydown)
    } else {
      document.removeEventListener('keydown', handleKeydown)
    }
  },
)

onMounted(() => {
  if (props.open) {
    document.addEventListener('keydown', handleKeydown)
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style lang="scss">
.image-preview-modal {
  .ant-modal-body {
    padding-top: 8px;
  }
}

.image-preview-modal-wrap--fullscreen {
  overflow: hidden;

  .ant-modal {
    top: 0;
    max-width: 100vw;
    padding: 12px 16px 16px;
    margin: 0 auto;
  }

  .ant-modal-content {
    height: calc(100vh - 28px);
    display: flex;
    flex-direction: column;
  }

  .ant-modal-body {
    flex: 1 1 auto;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding-bottom: 12px;
  }
}
</style>
