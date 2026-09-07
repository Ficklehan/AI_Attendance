<template>
  <div v-if="urls.length" class="task-image-chips">
    <span class="task-image-chips__title">
      <FileImageOutlined />
      {{ $t('taskEdit.originalImage') }}
      <em>({{ urls.length }}{{ $t('tasks.images') }})</em>
    </span>
    <button
      v-for="idx in visibleIndexes"
      :key="`chip-${idx}`"
      type="button"
      class="task-image-chips__chip"
      :class="{ 'is-active': activeIndex === idx }"
      :title="chipTitle(idx)"
      @click="$emit('select', idx)"
    >
      <img
        v-if="urls[idx]"
        :src="urls[idx]"
        alt=""
        class="task-image-chips__thumb"
        @error="onThumbError"
      >
      <span>{{ $t('taskEdit.imageIndex', { n: idx + 1 }) }}</span>
    </button>
    <a-popover
      v-if="hiddenCount > 0"
      v-model:open="pickerOpen"
      trigger="click"
      placement="bottomLeft"
      overlay-class-name="task-image-chips-popover"
    >
      <template #title>{{ $t('taskEdit.pickImageTitle') }}</template>
      <template #content>
        <div class="task-image-chips__panel" role="listbox">
          <button
            v-for="(url, idx) in urls"
            :key="`pick-${idx}`"
            type="button"
            class="task-image-chips__option"
            :class="{ 'is-active': activeIndex === idx }"
            :title="chipTitle(idx)"
            @click="pick(idx)"
          >
            <img v-if="url" :src="url" alt="" class="task-image-chips__thumb task-image-chips__thumb--lg" @error="onThumbError">
            <span>{{ $t('taskEdit.imageIndex', { n: idx + 1 }) }}</span>
          </button>
        </div>
      </template>
      <button type="button" class="task-image-chips__more">
        {{ $t('taskEdit.moreImages', { count: hiddenCount }) }}
      </button>
    </a-popover>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { FileImageOutlined } from '@ant-design/icons-vue'
import { fileNameFromImageUrl } from '@/utils/imageUrl'

const VISIBLE_LIMIT = 3

const props = defineProps({
  urls: { type: Array, default: () => [] },
  activeIndex: { type: Number, default: -1 },
})

const emit = defineEmits(['select'])
const { t } = useI18n()
const pickerOpen = ref(false)

const visibleIndexes = computed(() => {
  const total = props.urls.length
  const limit = total > VISIBLE_LIMIT ? VISIBLE_LIMIT : total
  return Array.from({ length: limit }, (_, i) => i)
})

const hiddenCount = computed(() => Math.max(0, props.urls.length - VISIBLE_LIMIT))

const chipTitle = (idx) => {
  const name = fileNameFromImageUrl(props.urls[idx])
  const label = t('taskEdit.imageIndex', { n: idx + 1 })
  return name ? `${label} · ${name}` : label
}

const pick = (idx) => {
  pickerOpen.value = false
  emit('select', idx)
}

const onThumbError = (event) => {
  if (event?.target) event.target.style.display = 'none'
}
</script>

<style lang="scss" scoped>
.task-image-chips {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  min-width: 0;
  gap: 6px;
}

.task-image-chips__title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-sm;
  font-weight: $font-weight-semibold;
  color: $text-secondary;
  white-space: nowrap;

  em {
    font-style: normal;
    font-weight: $font-weight-normal;
    color: $text-tertiary;
  }
}

.task-image-chips__chip,
.task-image-chips__more,
.task-image-chips__option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 8px;
  border: 1px solid #d6e4ff;
  border-radius: 6px;
  background: #f0f7ff;
  color: #1677ff;
  font: inherit;
  font-size: 12px;
  font-weight: $font-weight-semibold;
  line-height: 1.2;
  white-space: nowrap;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    border-color: #1677ff;
    background: #e6f4ff;
  }

  &.is-active {
    border-color: #1677ff;
    background: #e6f4ff;
    box-shadow: inset 2px 0 0 #1677ff;
  }
}

.task-image-chips__more {
  color: $text-primary;
  background: #fff;
  border-color: $border;
}

.task-image-chips__thumb {
  width: 20px;
  height: 20px;
  border-radius: 3px;
  object-fit: cover;
  background: #fff;
  flex-shrink: 0;

  &--lg {
    width: 36px;
    height: 36px;
  }
}

.task-image-chips__panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(88px, 1fr));
  gap: 6px;
  max-width: 260px;
  max-height: 240px;
  overflow: auto;
}

.task-image-chips__option {
  justify-content: flex-start;
  min-height: 44px;
  width: 100%;
}
</style>
