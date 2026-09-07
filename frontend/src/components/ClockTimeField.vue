<template>
  <div
    class="clock-time-field"
    :class="rootClass"
    @paste.capture="onPaste"
  >
    <div
      class="clock-time-field__box"
      :class="boxClass"
      :style="inputStyle"
      :title="placeholder || undefined"
      @mousedown="onBoxMouseDown"
    >
      <input
        ref="keyInput"
        class="clock-time-field__key"
        :disabled="disabled"
        inputmode="numeric"
        autocomplete="off"
        spellcheck="false"
        aria-label="HH:mm"
        @focus="onInputFocus"
        @blur="onBlur"
        @keydown="onKeydown"
      >
      <span
        class="clock-time-field__seg"
        :class="{ 'is-active': focused && draft.segment === 'hour' }"
        @mousedown.prevent="selectSegment('hour')"
      >{{ hourText }}</span>
      <span class="clock-time-field__colon">:</span>
      <span
        class="clock-time-field__seg"
        :class="{ 'is-active': focused && draft.segment === 'minute' }"
        @mousedown.prevent="selectSegment('minute')"
      >{{ minuteText }}</span>
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
    </div>
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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ClockCircleOutlined } from '@ant-design/icons-vue'
import {
  applyClockBackspace,
  applyClockDigit,
  commitClockDraft,
  draftFromCanonicalClock,
  draftFromClockValue,
  draftFromPastedText,
  moveClockSegment,
  segmentText,
} from '@/utils/clockTimeSegments'
import { isCanonicalClockTime } from '@/utils/recognizedTimeNormalizer'

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

const keyInput = ref(null)
const pickerPopupHost = ref(null)
const focused = ref(false)
const draft = ref(draftFromClockValue(props.value))
const pickerOpen = ref(false)
const pendingClock = ref('')
const skipConfirmOnClose = ref(false)
const confirming = ref(false)

const hourText = computed(() => segmentText(draft.value.hour))
const minuteText = computed(() => segmentText(draft.value.minute))

const canonicalValue = computed(() => {
  const raw = String(props.value ?? '').trim()
  return isCanonicalClockTime(raw) ? raw : undefined
})

const rootClass = computed(() => ({
  'clock-time-field--embedded': props.embedded,
  'clock-time-field--focused': focused.value,
}))

const boxClass = computed(() => [
  props.inputClass,
  {
    'is-focused': focused.value,
    'is-disabled': props.disabled,
    'is-bordered': props.bordered && !props.embedded,
    [`is-${props.size}`]: true,
  },
])

const getPopupContainer = () => pickerPopupHost.value || document.body

const pickerDropdownRoot = () => pickerPopupHost.value?.querySelector('.ant-picker-dropdown') || null

const pad2 = (n) => String(n).padStart(2, '0')

const clockFromPickerDate = (date) => {
  if (date == null || date === '') return ''
  if (typeof date === 'string') {
    const parsed = draftFromPastedText(date) || draftFromCanonicalClock(date)
    return parsed ? parsed.clock : ''
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

const focusKeyInput = () => {
  if (props.disabled) return
  nextTick(() => {
    keyInput.value?.focus()
  })
}

const applyDraft = (next, { emitComplete = true } = {}) => {
  draft.value = next
  if (!emitComplete || next.hour.length !== 2 || next.minute.length !== 2) return
  const clock = `${next.hour}:${next.minute}`
  emit('update:value', clock)
  emit('input', clock)
}

const commitDraftValue = () => {
  const value = commitClockDraft(draft.value)
  const synced = draftFromClockValue(value)
  synced.segment = draft.value.segment === 'minute' ? 'minute' : 'hour'
  draft.value = synced
  emit('update:value', value)
  emit('commit', value)
  return value
}

const clickPickerOk = () => {
  const root = pickerDropdownRoot()
  if (!root) return false
  const ok = root.querySelector('.ant-picker-ok button:not(:disabled), .ant-picker-ok .ant-btn:not(:disabled)')
  if (!ok) return false
  ok.click()
  return true
}

const confirmLikeOk = () => {
  if (confirming.value) return
  if (pickerOpen.value && clickPickerOk()) return
  confirming.value = true
  skipConfirmOnClose.value = true
  const clock = pendingClock.value
  pendingClock.value = ''
  pickerOpen.value = false
  if (isCanonicalClockTime(clock)) {
    applyDraft(draftFromCanonicalClock(clock), { emitComplete: false })
    emit('update:value', clock)
    emit('commit', clock)
  } else {
    commitDraftValue()
  }
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

const onBoxMouseDown = (event) => {
  if (props.disabled) return
  if (event.target.closest('.clock-time-field__trigger')) return
  event.preventDefault()
  if (!event.target.closest('.clock-time-field__seg')) {
    draft.value = moveClockSegment(draft.value, 'hour')
  }
  focusKeyInput()
}

const selectSegment = (segment) => {
  if (props.disabled) return
  draft.value = moveClockSegment(draft.value, segment)
  focusKeyInput()
}

const onInputFocus = () => {
  focused.value = true
  emit('focus')
}

const openPicker = () => {
  if (props.disabled) return
  pendingClock.value = commitClockDraft(draft.value) || canonicalValue.value || pendingClock.value || ''
  pickerOpen.value = true
  focusKeyInput()
}

const onPickerOpenChange = (open) => {
  if (open) {
    skipConfirmOnClose.value = false
    pickerOpen.value = true
    if (!pendingClock.value) pendingClock.value = commitClockDraft(draft.value) || canonicalValue.value || ''
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
  confirmLikeOk()
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
  if (isCanonicalClockTime(clock)) {
    applyDraft(draftFromCanonicalClock(clock), { emitComplete: false })
    emit('update:value', clock)
    emit('commit', clock)
  } else {
    commitDraftValue()
  }
  queueMicrotask(() => {
    confirming.value = false
    skipConfirmOnClose.value = false
  })
}

const onPaste = (event) => {
  const text = event.clipboardData?.getData('text') || event.clipboardData?.getData('text/plain') || ''
  const pasted = draftFromPastedText(text)
  if (!pasted) return
  event.preventDefault()
  event.stopPropagation()
  closePickerWithoutConfirm()
  applyDraft(pasted, { emitComplete: false })
  emit('update:value', pasted.clock)
  emit('commit', pasted.clock)
}

const isPickerDropdownTarget = (node) => {
  if (!node || typeof node.closest !== 'function') return false
  return Boolean(node.closest('.clock-time-field__popup-host, .ant-picker-dropdown'))
}

const onBlur = (event) => {
  const related = event?.relatedTarget
  if (isPickerDropdownTarget(related)) {
    focusKeyInput()
    return
  }
  focused.value = false
  if (pickerOpen.value) {
    confirmLikeOk()
    return
  }
  if (confirming.value || skipConfirmOnClose.value) return
  commitDraftValue()
}

const onKeydown = (event) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    closePickerWithoutConfirm()
    draft.value = draftFromClockValue(props.value)
    keyInput.value?.blur()
    return
  }
  if (event.key === 'Enter') {
    event.preventDefault()
    event.stopPropagation()
    if (pickerOpen.value) {
      confirmLikeOk()
      return
    }
    if (confirming.value || skipConfirmOnClose.value) return
    commitDraftValue()
    keyInput.value?.blur()
    return
  }
  if (event.key === 'Tab') {
    if (!event.shiftKey && draft.value.segment === 'hour') {
      event.preventDefault()
      draft.value = moveClockSegment(draft.value, 'minute')
      return
    }
    if (event.shiftKey && draft.value.segment === 'minute') {
      event.preventDefault()
      draft.value = moveClockSegment(draft.value, 'hour')
    }
    return
  }
  if (event.key === 'ArrowRight' || event.key === ':') {
    event.preventDefault()
    draft.value = moveClockSegment(draft.value, 'minute')
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    draft.value = moveClockSegment(draft.value, 'hour')
    return
  }
  if (event.key === 'Backspace') {
    event.preventDefault()
    closePickerWithoutConfirm()
    applyDraft(applyClockBackspace(draft.value), { emitComplete: false })
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'F4') {
    event.preventDefault()
    openPicker()
    return
  }
  if (/^\d$/.test(event.key)) {
    event.preventDefault()
    closePickerWithoutConfirm()
    applyDraft(applyClockDigit(draft.value, event.key))
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
    confirmLikeOk()
  }
}

watch(() => props.value, (value) => {
  if (focused.value || confirming.value) return
  draft.value = draftFromClockValue(value)
})

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
}

.clock-time-field__box {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  min-height: 22px;
  padding: 0 4px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: #fff;
  color: inherit;
  font-size: inherit;
  line-height: 1.2;
  cursor: text;

  &.is-bordered,
  &.is-focused {
    border-color: #d9d9d9;
  }

  &.is-disabled {
    cursor: default;
    opacity: 0.65;
  }
}

.clock-time-field--embedded .clock-time-field__box {
  padding: 1px 3px 1px 2px;
  background: transparent;
  box-shadow: none;
  font-size: 12px;
}

.clock-time-field__key {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: 0;
  border: 0;
  opacity: 0;
  pointer-events: none;
}

.clock-time-field__seg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.15em;
  padding: 0 1px;
  border-radius: 2px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;

  &.is-active {
    color: #fff;
    background: $primary;
  }
}

.clock-time-field__colon {
  margin: 0 1px;
  color: inherit;
  opacity: 0.65;
}

.clock-time-field__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
  margin-left: auto;
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
