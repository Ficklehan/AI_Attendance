<template>
  <component :is="tag" :class="cellClass" :style="cellStyle">
    <slot />
  </component>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  muted: { type: Boolean, default: false },
  cellClass: { type: [String, Object, Array], default: 'cell-text' },
})

const tag = computed(() => (props.muted ? 's' : 'span'))

const cellStyle = computed(() => (
  props.muted
    ? { color: '#73707F', fontStyle: 'italic' }
    : undefined
))
</script>
