<template>
  <span class="cell-copy-paste" @mousedown.stop>
    <a-tooltip :title="$t('common.copy')">
      <button
        type="button"
        class="cell-copy-paste__btn"
        :class="{ 'is-copied': copied }"
        :disabled="!canCopy"
        :aria-label="$t('common.copy')"
        @click.stop="onCopyClick"
      >
        <CheckOutlined v-if="copied" />
        <CopyOutlined v-else />
      </button>
    </a-tooltip>
    <a-tooltip v-if="canPaste" :title="$t('common.pasteOver')">
      <button
        type="button"
        class="cell-copy-paste__btn is-paste-ready"
        :aria-label="$t('common.pasteOver')"
        @click.stop="$emit('paste')"
      >
        <SnippetsOutlined />
      </button>
    </a-tooltip>
  </span>
</template>

<script setup>
import { ref } from 'vue'
import { CopyOutlined, SnippetsOutlined, CheckOutlined } from '@ant-design/icons-vue'

defineProps({
  canCopy: { type: Boolean, default: false },
  canPaste: { type: Boolean, default: false },
})

const emit = defineEmits(['copy', 'paste'])
const copied = ref(false)
let copiedTimer = null

const onCopyClick = () => {
  emit('copy')
  copied.value = true
  if (copiedTimer) clearTimeout(copiedTimer)
  copiedTimer = setTimeout(() => {
    copied.value = false
  }, 1200)
}
</script>

<style lang="scss" scoped>
.cell-copy-paste {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 1px;
}

.cell-copy-paste__btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 3px;
  background: #fff;
  color: $text-secondary;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;

  &:hover:not(:disabled),
  &:focus-visible:not(:disabled) {
    color: $primary;
    border-color: rgba($primary, 0.35);
    background: rgba($primary, 0.08);
  }

  &:disabled {
    cursor: default;
    opacity: 0.28;
    border-color: transparent;
    background: transparent;
  }

  &.is-copied {
    color: $success;
    border-color: rgba($success, 0.35);
  }

  &.is-paste-ready {
    color: $primary;
    border-color: rgba($primary, 0.4);
    background: rgba($primary, 0.08);
  }
}
</style>
