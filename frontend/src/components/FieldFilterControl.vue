<template>
  <a-input
    v-if="filterType === 'text'"
    :value="textValue"
    :placeholder="placeholder || t('tasks.searchContent')"
    allow-clear
    class="field-filter-control"
    @update:value="onTextChange"
    @keyup.enter="$emit('submit')"
  />
  <a-select
    v-else-if="filterType === 'status'"
    :value="textValue || undefined"
    :placeholder="placeholder || t('tasks.filterStatus')"
    allow-clear
    class="field-filter-control"
    @update:value="onTextChange"
  >
    <a-select-option v-for="opt in options" :key="opt.value" :value="opt.value">
      {{ opt.label }}
    </a-select-option>
  </a-select>
  <a-range-picker
    v-else-if="filterType === 'datetime'"
    :value="dateRangeValue"
    show-time
    format="YYYY-MM-DD HH:mm:ss"
    value-format="YYYY-MM-DD HH:mm:ss"
    :placeholder="[t('tasks.filterRangeStart'), t('tasks.filterRangeEnd')]"
    class="field-filter-control field-filter-control--range"
    @update:value="onDateTimeRangeChange"
  />
  <a-range-picker
    v-else-if="filterType === 'date'"
    :value="dateRangeValue"
    format="YYYY-MM-DD"
    value-format="YYYY-MM-DD"
    :placeholder="[t('tasks.filterRangeStart'), t('tasks.filterRangeEnd')]"
    class="field-filter-control field-filter-control--range"
    @update:value="onDateRangeChange"
  />
  <div v-else-if="filterType === 'time'" class="field-filter-control field-filter-control--time">
    <a-time-picker
      :value="timeFromValue"
      format="HH:mm"
      value-format="HH:mm"
      :placeholder="t('tasks.filterRangeStart')"
      allow-clear
      @update:value="(v) => onTimeChange('from', v)"
    />
    <span class="field-filter-control__sep">—</span>
    <a-time-picker
      :value="timeToValue"
      format="HH:mm"
      value-format="HH:mm"
      :placeholder="t('tasks.filterRangeEnd')"
      allow-clear
      @update:value="(v) => onTimeChange('to', v)"
    />
  </div>
  <a-select
    v-else-if="filterType === 'multiselect'"
    :value="multiValue"
    mode="multiple"
    :placeholder="placeholder || t('tasks.filterMultiSelect')"
    allow-clear
    class="field-filter-control"
    :max-tag-count="2"
    @update:value="onMultiChange"
  >
    <a-select-option v-for="opt in options" :key="opt.value" :value="opt.value">
      {{ opt.label }}
    </a-select-option>
  </a-select>
  <a-input
    v-else
    :value="textValue"
    :placeholder="placeholder || t('tasks.searchContent')"
    allow-clear
    class="field-filter-control"
    @update:value="onTextChange"
    @keyup.enter="$emit('submit')"
  />
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { emptyFilterValue } from '@/utils/fieldFilterValue'

const props = defineProps({
  filterType: { type: String, default: 'text' },
  modelValue: { type: [String, Array, Object], default: '' },
  placeholder: { type: String, default: '' },
  options: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'submit'])

const { t } = useI18n()

const textValue = computed(() => (typeof props.modelValue === 'string' ? props.modelValue : ''))

const multiValue = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []))

const rangeValue = computed(() => (
  props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue)
    ? props.modelValue
    : emptyFilterValue(props.filterType)
))

const dateRangeValue = computed(() => {
  const { from, to } = rangeValue.value
  if (!from && !to) return null
  return [from || null, to || null]
})

const timeFromValue = computed(() => rangeValue.value.from || null)
const timeToValue = computed(() => rangeValue.value.to || null)

function onTextChange(v) {
  emit('update:modelValue', v ?? '')
}

function onMultiChange(v) {
  emit('update:modelValue', Array.isArray(v) ? v : [])
}

function onDateRangeChange(v) {
  const arr = Array.isArray(v) ? v : []
  emit('update:modelValue', { from: arr[0] || '', to: arr[1] || '' })
}

function onDateTimeRangeChange(v) {
  onDateRangeChange(v)
}

function onTimeChange(key, v) {
  emit('update:modelValue', { ...rangeValue.value, [key]: v || '' })
}
</script>

<style scoped lang="scss">
.field-filter-control {
  width: 100%;
}

.field-filter-control--range {
  :deep(.ant-picker) {
    width: 100%;
  }
}

.field-filter-control--time {
  display: flex;
  align-items: center;
  gap: 8px;

  :deep(.ant-picker) {
    flex: 1;
    min-width: 0;
  }
}

.field-filter-control__sep {
  color: rgba(0, 0, 0, 0.45);
  flex-shrink: 0;
}
</style>
