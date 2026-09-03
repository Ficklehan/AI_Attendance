<template>
  <div
    class="clock-time-field"
    :class="{ 'clock-time-field--embedded': embedded }"
    @paste.capture="onPaste"
  >
    <a-input
      :value="displayValue"
      :size="size"
      :bordered="bordered"
      :disabled="disabled"
      :placeholder="placeholder"
      :class="inputClass"
      :style="inputStyle"
      autocomplete="off"
      spellcheck="false"
      @focus="onInputFocus"
      @update:value="onType"
      @blur="onBlur"
      @keydown="onKeydown"
    >
      <template #suffix>
        <button
          type="button"
          class="clock-time-field__trigger"
          tabindex="-1"
          aria-label="HH:mm"
          :disabled="disabled"
          @mousedown.prevent
          @click.stop="openPicker"
        >
          <ClockCircleOutlined />
        </button>
      </template>
    </a-input>
    <div ref="pickerPopupHost" class="clock-time-field__popup-host" aria-hidden="true" />
    <a-time-picker
      class="clock-time-field__host"
      :value="canonicalValue"
      :open="pickerOpen"
      format="HH:mm"
      value-format="HH:mm"
      :allow-clear="false"
      :show-now="true"
      :input-read-only="true"
      :disabled="disabled"
      :get-popup-container="getPopupContainer"
      @update:open="onPickerOpenChange"
      @update:value="onPick"
      @select="onPanelSelect"
      @ok="onPick"
    >
      <template #suffixIcon><span /></template>
    </a-time-picker>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ClockCircleOutlined } from '@ant-design/icons-vue'
import {
  isCanonicalClockTime,
  normalizeClockTime,
} from '@/utils/recognizedTimeNormalizer'

const props = defineProps({
  value: { type: [String, Number], default: '' },
  placeholder: { type: String, default: '' },
  size: { type: String, default: 'middle' },
  bordered: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  embedded: { type: Boolean, default: false },
  inputClass: { type: [String, Array, Object], default: undefined },
  inputStyle: { type: [String, Object], default: undefined },
})

const emit = defineEmits(['update:value', 'focus', 'commit', 'input'])

const pickerOpen = ref(false)
const pickerPopupHost = ref(null)
const pendingClock = ref('')
const skipConfirmOnClose = ref(false)
const confirming = ref(false)

const displayValue = computed(() => (props.value == null ? '' : String(props.value)))

const canonicalValue = computed(() => {
  const raw = String(props.value ?? '').trim()
  return isCanonicalClockTime(raw) ? raw : undefined
})

const getPopupContainer = () => pickerPopupHost.value || document.body

const pickerDropdownRoot = () => pickerPopupHost.value?.querySelector('.ant-picker-dropdown') || null

const pad2 = (n) => String(n).padStart(2, '0')

const clockFromPickerDate = (date) => {
  if (date == null || date === '') return ''
  if (typeof date === 'string') {
    const next = normalizeClockTime(date)
    return isCanonicalClockTime(next) ? String(next) : ''
  }
  if (typeof date.format === 'function') {
    return date.format('HH:mm')
  }
  if (typeof date.hour === 'function' && typeof date.minute === 'function') {
    return `${pad2(date.hour())}:${pad2(date.minute())}`
  }
  if (date instanceof Date && !Number.isNaN(date.getTime())) {
    return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
  }
  return ''
}

const commitNormalized = (raw) => {
  const next = normalizeClockTime(raw)
  const value = next === undefined || next === null ? '' : String(next)
  emit('update:value', value)
  emit('commit', value)
}

const applyClock = (clock, fallbackRaw) => {
  if (isCanonicalClockTime(clock)) {
    emit('update:value', clock)
    emit('commit', clock)
    return
  }
  commitNormalized(fallbackRaw)
}

const clickPickerOk = () => {
  const root = pickerDropdownRoot()
  if (!root) return false
  const ok = root.querySelector('.ant-picker-ok button:not(:disabled), .ant-picker-ok .ant-btn:not(:disabled)')
  if (!ok) return false
  ok.click()
  return true
}

const confirmLikeOk = (fallbackRaw) => {
  if (confirming.value) return
  if (pickerOpen.value && clickPickerOk()) return
  confirming.value = true
  skipConfirmOnClose.value = true
  const clock = pendingClock.value
  pendingClock.value = ''
  pickerOpen.value = false
  applyClock(clock, fallbackRaw)
  queueMicrotask(() => {
    confirming.value = false
    skipConfirmOnClose.value = false
  })
}

const closePickerWithoutConfirm = () => {
  skipConfirmOnClose.value = true
  pendingClock.value = ''
  pickerOpen.value = false
  queueMicrotask(() => {
    skipConfirmOnClose.value = false
  })
}

const onType = (next) => {
  closePickerWithoutConfirm()
  emit('update:value', next)
  emit('input', next)
}

const onInputFocus = () => {
  emit('focus')
  openPicker()
}

const openPicker = () => {
  if (props.disabled) return
  pendingClock.value = canonicalValue.value || pendingClock.value || ''
  pickerOpen.value = true
}

const onPickerOpenChange = (open) => {
  if (open) {
    skipConfirmOnClose.value = false
    pickerOpen.value = true
    if (!pendingClock.value) pendingClock.value = canonicalValue.value || ''
    return
  }
  if (skipConfirmOnClose.value || confirming.value) {
    pickerOpen.value = false
    return
  }
  if (clickPickerOk()) {
    pickerOpen.value = false
    return
  }
  confirmLikeOk(props.value)
}

const onPanelSelect = (date) => {
  const clock = clockFromPickerDate(date)
  if (clock) pendingClock.value = clock
}

const onPick = (next) => {
  if (confirming.value) return
  confirming.value = true
  skipConfirmOnClose.value = true
  const clock = clockFromPickerDate(next)
  pendingClock.value = ''
  pickerOpen.value = false
  applyClock(clock, next)
  queueMicrotask(() => {
    confirming.value = false
    skipConfirmOnClose.value = false
  })
}

const onPaste = (event) => {
  const text = event.clipboardData?.getData('text') || event.clipboardData?.getData('text/plain') || ''
  const next = normalizeClockTime(text)
  if (!isCanonicalClockTime(next)) return
  event.preventDefault()
  event.stopPropagation()
  closePickerWithoutConfirm()
  emit('update:value', next)
  emit('commit', next)
}

const isPickerDropdownTarget = (node) => {
  if (!node || typeof node.closest !== 'function') return false
  return Boolean(node.closest('.clock-time-field__popup-host, .ant-picker-dropdown'))
}

const onBlur = (event) => {
  const related = event?.relatedTarget
  if (isPickerDropdownTarget(related)) return
  const typed = event?.target?.value
  if (pickerOpen.value) {
    confirmLikeOk(typed == null ? props.value : typed)
    return
  }
  if (confirming.value || skipConfirmOnClose.value) return
  commitNormalized(typed == null ? props.value : typed)
}

const onKeydown = (event) => {
  if (event.key === 'Escape' && pickerOpen.value) {
    event.preventDefault()
    closePickerWithoutConfirm()
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    event.stopPropagation()
    if (pickerOpen.value) {
      confirmLikeOk(event?.target?.value ?? props.value)
      return
    }
    if (confirming.value || skipConfirmOnClose.value) return
    commitNormalized(event?.target?.value ?? props.value)
    if (event.target && typeof event.target.blur === 'function') event.target.blur()
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'F4') {
    event.preventDefault()
    openPicker()
  }
}

const onDocumentKeydown = (event) => {
  if (!pickerOpen.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closePickerWithoutConfirm()
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    event.stopPropagation()
    confirmLikeOk(props.value)
  }
}

watch(pickerOpen, (open) => {
  if (open) {
    document.addEventListener('keydown', onDocumentKeydown, true)
    return
  }
  document.removeEventListener('keydown', onDocumentKeydown, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onDocumentKeydown, true)
})

defineExpose({ openPicker })
</script>

<style lang="scss" scoped>
.clock-time-field {
  position: relative;
  width: 100%;
  min-width: 0;

  :deep(.ant-input-affix-wrapper) {
    display: inline-flex;
    align-items: center;
    width: 100%;
    min-width: 0;
    padding: 0 4px 0 4px;
    border: 1px solid transparent;
    border-radius: 4px;
    background: #fff;

    &.ant-input-affix-wrapper-focused,
    &:focus-within {
      border-color: #d9d9d9;
    }

    > input.ant-input {
      border: 0 !important;
      box-shadow: none !important;
      outline: none !important;
      background: transparent !important;
      appearance: none;
    }
  }

  :deep(.ant-input-suffix) {
    margin-inline-start: 0;
    padding: 0;
  }
}

.clock-time-field--embedded {
  :deep(.ant-input-affix-wrapper) {
    padding: 1px 3px 1px 2px !important;
    background: transparent;
    box-shadow: none;
  }

  :deep(.ant-input-affix-wrapper > input.ant-input) {
    font-size: 12px;
    padding: 0 2px !important;
  }
}

.clock-time-field__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: rgba(0, 0, 0, 0.35);
  font-size: 11px;
  line-height: 1;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: rgba(0, 0, 0, 0.65);
  }

  &:disabled {
    cursor: default;
    opacity: 0.4;
  }
}

.clock-time-field__popup-host {
  position: absolute;
  inset: 0 auto auto 0;
  width: 0;
  height: 0;
  overflow: visible;
  pointer-events: none;

  :deep(.ant-picker-dropdown) {
    pointer-events: auto;
  }
}

.clock-time-field__host {
  position: absolute !important;
  left: 0;
  top: 0;
  width: 0 !important;
  min-width: 0 !important;
  height: 0 !important;
  margin: 0;
  padding: 0;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  visibility: hidden;
  border: 0 !important;

  :deep(.ant-picker-suffix),
  :deep(.ant-picker-input) {
    display: none;
  }
}
</style>
