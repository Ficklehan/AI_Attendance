<template>
  <div v-if="hint" class="field-change-hint">
    <span class="field-change-hint__text" :title="hint">{{ hint }}</span>
    <a-tooltip v-if="showRestore" :title="restoreTitle" :mouse-enter-delay="0" :mouse-leave-delay="0">
      <button
        type="button"
        class="field-change-hint__restore"
        :aria-label="restoreTitle"
        @click.stop="$emit('restore')"
      >
        <UndoOutlined />
      </button>
    </a-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { UndoOutlined } from '@ant-design/icons-vue'

defineProps({
  hint: { type: String, default: '' },
  showRestore: { type: Boolean, default: false },
})

defineEmits(['restore'])

const { t } = useI18n()
const restoreTitle = computed(() => {
  const text = t('taskEdit.restoreOriginal')
  return text && text !== 'taskEdit.restoreOriginal' ? text : '还原原值'
})
</script>

<style lang="scss" scoped>
.field-change-hint {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 1px;
  max-width: 100%;
  min-width: 0;
  font-size: 9px;
  line-height: 1.2;
  font-weight: 600;
  color: #a8071a;
}

.field-change-hint__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field-change-hint__restore {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 10px;
  height: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 9px;
  line-height: 1;
  cursor: pointer;

  :deep(.anticon) {
    font-size: 9px;
  }

  &:hover,
  &:focus-visible {
    opacity: 0.75;
  }
}
</style>
