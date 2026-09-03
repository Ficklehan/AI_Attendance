<template>
  <div v-if="items.length" class="task-file-list" :class="{ 'task-file-list--inline': inline }">
    <div v-if="showHeader" class="task-file-list__head">
      <FileImageOutlined />
      <span class="task-file-list__title">{{ headerTitle }}</span>
      <span v-if="items.length > 1" class="task-file-list__count">
        ({{ items.length }}{{ $t('tasks.images') }})
      </span>
    </div>
    <div class="task-file-list__body">
      <button
        v-for="(item, idx) in items"
        :key="item.key || idx"
        type="button"
        class="task-file-item"
        @click="openPreview(idx)"
      >
        <FileImageOutlined class="task-file-item__icon" />
        <span class="task-file-item__name" :title="item.name">{{ item.name }}</span>
        <span class="task-file-item__action">{{ actionLabel }}</span>
      </button>
    </div>
  </div>
  <span v-else-if="showEmpty" class="task-file-list__empty">{{ emptyText || $t('taskEdit.noOriginalImages') }}</span>

  <ImagePreviewModal
    v-model:open="previewVisible"
    :images="previewUrls"
    :image-names="previewNames"
    :initial-index="previewIndex"
    :auto-orient-enabled="false"
  />
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { FileImageOutlined } from '@ant-design/icons-vue'
import ImagePreviewModal from '@/components/ImagePreviewModal.vue'
import { buildTaskImageItems } from '@/utils/taskImages'

const props = defineProps({
  imageUrls: { type: [String, Array], default: null },
  fileKey: { type: String, default: '' },
  inline: { type: Boolean, default: false },
  showHeader: { type: Boolean, default: false },
  headerTitle: { type: String, default: '' },
  showEmpty: { type: Boolean, default: false },
  emptyText: { type: String, default: '' },
})

const { t } = useI18n()

const previewVisible = ref(false)
const previewIndex = ref(0)

const items = computed(() => buildTaskImageItems(props.imageUrls, props.fileKey))
const previewUrls = computed(() => items.value.map((item) => item.url))
const previewNames = computed(() => items.value.map((item) => item.name || ''))

const headerTitle = computed(() => props.headerTitle || t('taskEdit.originalImage'))
const actionLabel = computed(() => (props.inline ? t('tasks.view') : t('tasks.viewImage')))

const openPreview = (index) => {
  if (!items.value.length) return
  previewIndex.value = Math.min(Math.max(index, 0), items.value.length - 1)
  previewVisible.value = true
}
</script>

<style lang="scss" scoped>
.task-file-list {
  &--inline {
    .task-file-list__body {
      gap: 2px;
    }

    .task-file-item {
      padding: 2px 0;
      border: none;
      background: transparent;

      &:hover {
        background: transparent;
        box-shadow: none;

        .task-file-item__name {
          color: $primary;
        }
      }
    }
  }

  &__head {
    display: flex;
    align-items: center;
    gap: $space-2;
    margin-bottom: $space-2;
    font-size: $font-size-sm;
    color: $text-secondary;
  }

  &__title {
    font-weight: $font-weight-semibold;
    color: $text-strong;
  }

  &__count {
    color: $text-tertiary;
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: $space-1;
  }

  &__empty {
    font-size: $font-size-sm;
    color: $text-tertiary;
  }
}

.task-file-item {
  display: flex;
  align-items: center;
  gap: $space-2;
  width: 100%;
  padding: $space-2 $space-3;
  border: 1px solid $border-light;
  border-radius: $radius-md;
  background: $bg-surface;
  cursor: pointer;
  text-align: left;
  transition: border-color $duration-fast, background $duration-fast;

  &:hover {
    border-color: rgba($primary, 0.35);
    background: $primary-light;
  }

  &__icon {
    flex-shrink: 0;
    color: $primary;
    font-size: 16px;
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    color: $text-strong;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__action {
    flex-shrink: 0;
    font-size: $font-size-sm;
    color: $primary;
  }
}
</style>
