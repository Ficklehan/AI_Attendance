<template>
  <span
    class="copyable-cell"
    :class="{ 'copyable-cell--block': block }"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <span class="copyable-cell__content" :title="displayText || undefined">
      <slot>{{ displayText }}</slot>
    </span>
    <a-tooltip v-if="canCopy" :title="$t('common.copy')">
      <button
        type="button"
        class="copyable-cell__btn"
        :class="{ 'copyable-cell__btn--visible': hovered || copied }"
        :aria-label="$t('common.copy')"
        @click.stop="handleCopy"
      >
        <CopyOutlined />
      </button>
    </a-tooltip>
  </span>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { CopyOutlined } from '@ant-design/icons-vue'
import { copyTextToClipboard } from '@/utils/clipboard'
import { formatTableCellText } from '@/utils/tableCopy'

const props = defineProps({
  text: {
    type: [String, Number],
    default: '',
  },
  block: {
    type: Boolean,
    default: false,
  },
})

const { t } = useI18n()
const hovered = ref(false)
const copied = ref(false)

const displayText = computed(() => formatTableCellText(props.text))
const canCopy = computed(() => displayText.value !== '')

let copiedTimer = null

const handleCopy = async () => {
  if (!canCopy.value) return
  const ok = await copyTextToClipboard(displayText.value)
  if (!ok) {
    message.error(t('common.copyFailed'))
    return
  }
  message.success(t('common.copied'))
  copied.value = true
  if (copiedTimer) clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 1200)
}
</script>

<style lang="scss" scoped>
.copyable-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  min-width: 0;
  vertical-align: middle;

  &--block {
    display: flex;
    width: 100%;
  }

  &__content {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    user-select: text;
  }

  &__btn {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    padding: 0;
    border: none;
    border-radius: $radius-sm;
    background: transparent;
    color: $text-tertiary;
    cursor: pointer;
    opacity: 0;
    transition: opacity $duration-fast $ease-smooth, color $duration-fast $ease-smooth,
      background $duration-fast $ease-smooth;

    &--visible {
      opacity: 1;
    }

    &:hover {
      color: $primary;
      background: rgba($primary, 0.08);
    }
  }
}
</style>
