<template>
  <a-modal
    :open="open"
    :title="$t('taskDelete.title')"
    :width="520"
    :confirm-loading="submitting"
    :ok-text="$t('common.delete')"
    :cancel-text="$t('common.cancel')"
    ok-type="danger"
    destroy-on-close
    :mask-closable="false"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-alert
      v-if="confirmed"
      type="error"
      show-icon
      :message="$t('taskDelete.confirmedImpactTitle')"
      :description="$t('taskDelete.confirmedImpactDesc')"
      class="delete-impact-alert"
    />
    <a-alert
      v-else
      type="warning"
      show-icon
      :message="$t('taskDelete.simpleConfirmTitle')"
      :description="$t('taskDelete.simpleConfirmDesc')"
      class="delete-impact-alert"
    />

    <div v-if="taskId" class="delete-task-meta">
      <span class="delete-task-meta__label">{{ $t('tasks.taskId') }}</span>
      <span class="delete-task-meta__value">{{ taskId }}</span>
    </div>
    <div v-if="batchCount > 1" class="delete-task-meta">
      <span class="delete-task-meta__label">{{ $t('taskDelete.batchCount') }}</span>
      <span class="delete-task-meta__value">{{ batchCount }}</span>
    </div>

    <a-form v-if="confirmed" layout="vertical" class="delete-form">
      <a-form-item
        :label="$t('taskDelete.reason')"
        required
        :validate-status="reasonTouched && !reason.trim() ? 'error' : ''"
        :help="reasonTouched && !reason.trim() ? $t('taskDelete.reasonRequired') : ''"
      >
        <a-textarea
          v-model:value="reason"
          :rows="3"
          :maxlength="500"
          show-count
          :placeholder="$t('taskDelete.reasonPlaceholder')"
          @blur="reasonTouched = true"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  taskId: { type: String, default: '' },
  confirmed: { type: Boolean, default: false },
  batchCount: { type: Number, default: 1 },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'submit', 'cancel'])

const { t } = useI18n()
const reason = ref('')
const reasonTouched = ref(false)

watch(
  () => props.open,
  (visible) => {
    if (visible) {
      reason.value = ''
      reasonTouched.value = false
    }
  }
)

const handleCancel = () => {
  emit('update:open', false)
  emit('cancel')
}

const handleSubmit = () => {
  if (!props.confirmed) {
    emit('submit', '')
    return
  }
  reasonTouched.value = true
  const trimmed = reason.value.trim()
  if (!trimmed) {
    message.warning(t('taskDelete.reasonRequired'))
    return
  }
  emit('submit', trimmed)
}
</script>

<style lang="scss" scoped>
.delete-impact-alert {
  margin-bottom: 16px;
}

.delete-task-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;

  &__label {
    color: $text-secondary;
    flex-shrink: 0;
  }

  &__value {
    color: $text-strong;
    word-break: break-all;
  }
}
</style>
