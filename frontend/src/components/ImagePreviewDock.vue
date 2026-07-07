<template>
  <aside
    v-if="open"
    class="image-preview-dock"
  >
    <div class="image-preview-dock__panel surface-card">
      <ImagePreviewViewer
        :images="images"
        :index="index"
        :loading="loading"
        :title="title"
        :active="open"
        :auto-orient-enabled="autoOrientEnabled"
        :show-auto-orient-toggle="true"
        :show-gesture-hint="false"
        layout="dock"
        @update:auto-orient-enabled="autoOrientEnabled = $event"
        @update:index="emit('update:index', $event)"
      >
        <template #head-actions>
          <a-tooltip :title="$t('taskEdit.previewFullscreen')">
            <a-button type="text" size="small" @click="emit('fullscreen')">
              <FullscreenOutlined />
            </a-button>
          </a-tooltip>
          <a-tooltip :title="$t('common.close')">
            <a-button type="text" size="small" @click="close">
              <CloseOutlined />
            </a-button>
          </a-tooltip>
        </template>
      </ImagePreviewViewer>
    </div>
  </aside>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { CloseOutlined, FullscreenOutlined } from '@ant-design/icons-vue'
import ImagePreviewViewer from '@/components/ImagePreviewViewer.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  images: { type: Array, default: () => [] },
  index: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
})

const emit = defineEmits(['update:open', 'update:index', 'fullscreen'])

const autoOrientEnabled = ref(true)

function close() {
  emit('update:open', false)
}

const handleKeydown = (event) => {
  if (!props.open) return
  if (event.key === 'Escape') {
    close()
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
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style lang="scss" scoped>
.image-preview-dock {
  position: relative;
  width: 100%;
  height: 100%;
  max-height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;

  &__panel {
    flex: 1 1 0;
    min-width: 0;
    min-height: 0;
    max-height: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border-radius: $radius-xl;
    border: 1px solid rgba($border, 0.55);
    box-shadow: $shadow-card;
    overflow: hidden;
    background: $bg-surface;
  }
}
</style>
