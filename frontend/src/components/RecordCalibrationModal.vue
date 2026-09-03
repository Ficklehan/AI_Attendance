<template>
  <a-modal
    :open="open"
    :title="$t('calibration.title')"
    :width="720"
    :confirm-loading="submitting"
    :ok-text="$t('calibration.submit')"
    :cancel-text="$t('common.cancel')"
    destroy-on-close
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <p class="calib-hint">{{ $t('calibration.hint') }}</p>

    <div v-if="historyEntries.length > 0" class="calib-history">
      <div class="calib-history__title">{{ $t('calibration.historyTitle') }}</div>
      <div
        v-for="(entry, idx) in historyEntries"
        :key="idx"
        class="calib-history__item"
      >
        <div class="calib-history__meta">
          <span class="calib-history__who">{{ entry.byName || entry.by || '—' }}</span>
          <span class="calib-history__at">{{ formatHistoryTime(entry.at) }}</span>
        </div>
        <div v-if="entry.reason" class="calib-history__reason">{{ entry.reason }}</div>
        <ul v-if="formatHistoryChanges(entry, fieldLabel).length" class="calib-history__changes">
          <li v-for="line in formatHistoryChanges(entry, fieldLabel)" :key="line.field">
            <span class="calib-history__field">{{ line.label }}</span>
            <span class="calib-history__diff">
              <span class="from">{{ line.from }}</span>
              <span class="arrow">→</span>
              <span class="to">{{ line.to }}</span>
            </span>
          </li>
        </ul>
      </div>
    </div>

    <a-form layout="vertical" class="calib-form">
      <a-row :gutter="12">
        <a-col v-for="field in CALIBRATABLE_FIELDS" :key="field" :span="12">
          <a-form-item :label="fieldLabel(field)" class="calib-field-item">
            <a-input-number
              v-if="field === 'PAUSE'"
              v-model:value="draft[field]"
              :controls="false"
              style="width: 100%"
            />
            <ClockTimeField
              v-else-if="field === 'ARRIVEE' || field === 'DEPAR'"
              :value="draft[field]"
              @update:value="(v) => { draft[field] = v }"
              @commit="(v) => { draft[field] = v }"
            />
            <a-input v-else v-model:value="draft[field]" allow-clear />
            <div class="calib-original-line">
              <span class="calib-original-label">{{ $t('calibration.originalValue') }}</span>
              <span class="calib-original-value">{{ formatOriginalDisplay(field) }}</span>
            </div>
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item
        :label="$t('calibration.reason')"
        required
        :validate-status="reasonTouched && !reason.trim() ? 'error' : ''"
        :help="reasonTouched && !reason.trim() ? $t('calibration.reasonRequired') : ''"
      >
        <a-textarea
          v-model:value="reason"
          :rows="3"
          :maxlength="500"
          show-count
          :placeholder="$t('calibration.reasonPlaceholder')"
          @blur="reasonTouched = true"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import ClockTimeField from '@/components/ClockTimeField.vue'
import {
  CALIBRATABLE_FIELDS,
  FIELD_LABEL_KEYS,
  normalizeCalibValue,
} from '@/constants/calibratableFields'
import {
  parseCalibrationHistory,
  formatCalibDisplayValue,
  formatHistoryChanges,
} from '@/utils/calibrationHistory'

const props = defineProps({
  open: { type: Boolean, default: false },
  record: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'submit'])

const { t } = useI18n()
const reason = ref('')
const reasonTouched = ref(false)
const original = reactive({})
const draft = reactive({})

const fieldLabel = (field) => t(FIELD_LABEL_KEYS[field] || field)

const historyEntries = computed(() => {
  if (!props.record) return []
  return [...parseCalibrationHistory(props.record)].reverse()
})

const formatOriginalDisplay = (field) =>
  formatCalibDisplayValue(original[field])

const formatHistoryTime = (at) => {
  if (!at) return '—'
  const s = String(at).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

const resetDraft = () => {
  reason.value = ''
  reasonTouched.value = false
  CALIBRATABLE_FIELDS.forEach((f) => {
    const v = props.record?.[f]
    original[f] = v === undefined || v === null ? '' : v
    draft[f] = f === 'PAUSE' && (v === '' || v === null || v === undefined) ? null : v
  })
}

watch(
  () => [props.open, props.record],
  ([open]) => {
    if (open && props.record) resetDraft()
  },
  { immediate: true },
)

const buildUpdates = () => {
  const updates = {}
  CALIBRATABLE_FIELDS.forEach((field) => {
    const from = normalizeCalibValue(original[field])
    const to = normalizeCalibValue(draft[field])
    if (from !== to) {
      updates[field] = draft[field]
    }
  })
  return updates
}

const handleSubmit = () => {
  reasonTouched.value = true
  if (!reason.value.trim()) {
    message.warning(t('calibration.reasonRequired'))
    return
  }
  const updates = buildUpdates()
  if (Object.keys(updates).length === 0) {
    message.warning(t('calibration.noChanges'))
    return
  }
  emit('submit', {
    rowKey: props.record?._rowKey,
    updates,
    reason: reason.value.trim(),
  })
}

const handleCancel = () => {
  emit('update:open', false)
}
</script>

<style scoped lang="scss">
.calib-hint {
  margin: 0 0 12px;
  color: var(--text-secondary, #6b7280);
  font-size: 13px;
}

.calib-field-item {
  :deep(.ant-form-item-control-input-content) {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
}

.calib-original-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
  line-height: 1.4;
}

.calib-original-label {
  font-size: 12px;
  color: #9ca3af;
}

.calib-original-value {
  font-size: 12px;
  color: #dc2626;
  font-weight: 500;
  word-break: break-all;
}

.calib-history {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;

  &__title {
    font-weight: 600;
    font-size: 13px;
    margin-bottom: 10px;
    color: #9a3412;
  }

  &__item {
    padding: 10px 0;
    border-bottom: 1px dashed #fdba74;

    &:last-child {
      border-bottom: none;
      padding-bottom: 0;
    }

    &:first-of-type {
      padding-top: 0;
    }
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 12px;
    font-size: 12px;
    margin-bottom: 4px;
  }

  &__who {
    font-weight: 600;
    color: #111827;
  }

  &__at {
    color: #6b7280;
  }

  &__reason {
    font-size: 12px;
    color: #374151;
    margin-bottom: 6px;
    line-height: 1.5;
  }

  &__changes {
    list-style: none;
    margin: 0;
    padding: 0;
    font-size: 12px;
  }

  &__changes li {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 3px 0;
  }

  &__field {
    color: #6b7280;
    min-width: 72px;
  }

  &__diff .from {
    color: #dc2626;
    text-decoration: line-through;
  }

  &__diff .arrow {
    margin: 0 4px;
    color: #9ca3af;
  }

  &__diff .to {
    color: #059669;
    font-weight: 500;
  }
}
</style>
