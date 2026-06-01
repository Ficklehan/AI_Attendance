<template>
  <a-popover
    v-if="truncated"
    trigger="click"
    placement="topLeft"
    overlay-class-name="truncated-tag-overlay"
  >
    <template #content>
      <div class="truncated-tag-popover">
        <div v-if="popoverTitle" class="truncated-tag-popover__title">{{ popoverTitle }}</div>
        <pre class="truncated-tag-popover__body">{{ popoverBody }}</pre>
      </div>
    </template>
    <a-tag :color="color" :size="size" class="truncated-tag truncated-tag--clickable">
      {{ shortLabel }}
    </a-tag>
  </a-popover>
  <a-tag v-else :color="color" :size="size" class="truncated-tag">
    {{ text }}
  </a-tag>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatTagPopoverBody, shortenTagLabel } from '@/utils/tagDisplay'

const props = defineProps({
  text: {
    type: String,
    default: '',
  },
  color: {
    type: String,
    default: 'default',
  },
  size: {
    type: String,
    default: 'small',
  },
  maxLength: {
    type: Number,
    default: 26,
  },
})

const { t } = useI18n()

const textValue = computed(() => String(props.text || '').trim())

const shortLabel = computed(() => shortenTagLabel(textValue.value, props.maxLength).short)

const truncated = computed(() => shortenTagLabel(textValue.value, props.maxLength).truncated)

const popoverBody = computed(() => formatTagPopoverBody(textValue.value))

const popoverTitle = computed(() => {
  if (!truncated.value) return ''
  return t('common.clickToViewFull')
})
</script>

<style scoped lang="scss">
.truncated-tag {
  margin-right: 0;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.truncated-tag--clickable {
  cursor: pointer;
}
</style>

<style lang="scss">
.truncated-tag-overlay {
  .ant-popover-inner {
    max-width: min(420px, 92vw);
  }

  .truncated-tag-popover__title {
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: 8px;
  }

  .truncated-tag-popover__body {
    margin: 0;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 13px;
    line-height: 1.5;
    font-family: inherit;
    max-height: 280px;
    overflow: auto;
  }
}
</style>
