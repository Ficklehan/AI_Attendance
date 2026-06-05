<template>
  <div class="dimension-picker">
    <div class="dimension-picker__toolbar">
      <a-input
        v-model:value="keyword"
        allow-clear
        :placeholder="searchPlaceholder || $t('settings.roles.searchOptions')"
        class="dimension-picker__search"
      >
        <template #prefix>
          <SearchOutlined />
        </template>
      </a-input>
      <a-space :size="4" wrap class="dimension-picker__actions">
        <a-button size="small" type="link" :disabled="!filteredOptions.length" @click="selectAll">
          {{ $t('settings.roles.selectAll') }}
        </a-button>
        <a-button size="small" type="link" :disabled="!modelValue.length" @click="clearAll">
          {{ $t('settings.roles.clearAll') }}
        </a-button>
        <a-button size="small" type="link" :disabled="!filteredOptions.length" @click="invertSelection">
          {{ $t('settings.roles.invertSelection') }}
        </a-button>
      </a-space>
    </div>

    <div class="dimension-picker__summary">
      {{ $t('settings.roles.selectedCount', { count: modelValue.length, total: options.length }) }}
    </div>

    <div class="dimension-picker__list" :style="{ maxHeight: `${maxHeight}px` }">
      <a-checkbox-group
        v-if="filteredOptions.length"
        :value="modelValue"
        class="dimension-picker__group"
        @change="onChange"
      >
        <a-row :gutter="[8, 8]">
          <a-col
            v-for="opt in filteredOptions"
            :key="opt.value"
            :xs="24"
            :sm="12"
            :md="8"
          >
            <a-checkbox :value="opt.value" class="dimension-picker__item">
              <span class="dimension-picker__label" :title="opt.label">{{ opt.label }}</span>
            </a-checkbox>
          </a-col>
        </a-row>
      </a-checkbox-group>
      <a-empty
        v-else
        :description="keyword ? $t('settings.roles.noMatch') : $t('settings.roles.noOptions')"
        :image="false"
        class="dimension-picker__empty"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { SearchOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
  options: {
    type: Array,
    default: () => [],
  },
  searchPlaceholder: {
    type: String,
    default: '',
  },
  maxHeight: {
    type: Number,
    default: 200,
  },
})

const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()
const keyword = ref('')

const filteredOptions = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter((opt) => {
    const label = String(opt.label || '').toLowerCase()
    const value = String(opt.value || '').toLowerCase()
    return label.includes(q) || value.includes(q)
  })
})

const filteredValues = computed(() => filteredOptions.value.map((opt) => opt.value))

const onChange = (values) => {
  emit('update:modelValue', values)
}

const selectAll = () => {
  const merged = new Set([...props.modelValue, ...filteredValues.value])
  emit('update:modelValue', Array.from(merged))
}

const clearAll = () => {
  if (!keyword.value.trim()) {
    emit('update:modelValue', [])
    return
  }
  const removeSet = new Set(filteredValues.value)
  emit('update:modelValue', props.modelValue.filter((v) => !removeSet.has(v)))
}

const invertSelection = () => {
  const current = new Set(props.modelValue)
  const next = new Set(props.modelValue)
  filteredValues.value.forEach((value) => {
    if (current.has(value)) {
      next.delete(value)
    } else {
      next.add(value)
    }
  })
  emit('update:modelValue', Array.from(next))
}

watch(
  () => props.options,
  () => {
    keyword.value = ''
  }
)
</script>

<style scoped lang="scss">
.dimension-picker {
  border: 1px solid var(--border-color, #e8e8e8);
  border-radius: 8px;
  background: var(--bg-muted, #fafafa);
  overflow: hidden;
}

.dimension-picker__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color, #e8e8e8);
  background: #fff;
}

.dimension-picker__search {
  flex: 1;
  min-width: 180px;
}

.dimension-picker__actions {
  flex-shrink: 0;
}

.dimension-picker__summary {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--text-secondary, #5c5c5c);
  border-bottom: 1px solid var(--border-color, #f0f0f0);
  background: #fff;
}

.dimension-picker__list {
  overflow-y: auto;
  padding: 10px 12px;
  background: #fff;
}

.dimension-picker__group {
  width: 100%;
}

.dimension-picker__item {
  width: 100%;
  margin: 0 !important;
  padding: 4px 6px;
  border-radius: 6px;
  transition: background 0.15s;

  &:hover {
    background: rgba(22, 119, 255, 0.06);
  }

  :deep(.ant-checkbox + span) {
    width: calc(100% - 20px);
    padding-inline-end: 0;
  }
}

.dimension-picker__label {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.dimension-picker__empty {
  margin: 12px 0;
}
</style>
