<template>
  <a-modal
    :open="open"
    :footer="null"
    :width="'min(96vw, 1200px)'"
    :title="title || undefined"
    centered
    destroy-on-close
    class="image-preview-modal"
    :mask-closable="true"
    @update:open="onOpenUpdate"
  >
    <ImagePreviewViewer
      :images="images"
      v-bind="viewerBindings"
      :title="title"
      :active="open"
      :auto-orient-enabled="autoOrientEnabled"
      :show-header="false"
      @update:auto-orient-enabled="autoOrientEnabled = $event"
      @update:index="onIndexUpdate"
    />
  </a-modal>
</template>

<script setup>
import { ref, watch, computed, onMounted, onUnmounted } from 'vue'
import ImagePreviewViewer from '@/components/ImagePreviewViewer.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  images: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  index: { type: Number, default: undefined },
  title: { type: String, default: '' },
})

const emit = defineEmits(['update:open', 'update:index'])

const autoOrientEnabled = ref(true)

const viewerBindings = computed(() => {
  if (props.index !== undefined && props.index !== null) {
    return { index: props.index }
  }
  return { initialIndex: props.initialIndex }
})

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
</style>
