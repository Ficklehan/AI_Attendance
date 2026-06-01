<template>
  <a-popover
    trigger="click"
    placement="bottomLeft"
    overlay-class-name="table-header-filter-overlay"
    :open="open"
    @openChange="(v) => $emit('openChange', v)"
  >
    <template #content>
      <div class="table-header-filter-panel">
        <div class="table-header-filter-panel__head">
          <FilterOutlined class="table-header-filter-panel__icon" />
          <span class="table-header-filter-panel__title">{{ title }}</span>
        </div>
        <div class="table-header-filter-panel__field">
          <slot name="field">
            <a-input
              :value="keyword"
              :placeholder="placeholder || t('tasks.searchContent')"
              allow-clear
              class="table-header-filter-panel__input"
              @update:value="(v) => $emit('update:keyword', v)"
              @keyup.enter="$emit('apply')"
            />
          </slot>
        </div>
        <div class="table-header-filter-panel__actions">
          <a-button size="small" @click="$emit('reset')">{{ t('common.reset') }}</a-button>
          <a-button size="small" type="primary" @click="$emit('apply')">{{ t('common.search') }}</a-button>
        </div>
      </div>
    </template>
    <a-tooltip :title="t('common.filterColumn')" placement="top">
      <a-button
        type="text"
        size="small"
        class="table-header-filter-btn"
        :class="{ 'table-header-filter-btn--active': active }"
      >
        <FilterOutlined />
      </a-button>
    </a-tooltip>
  </a-popover>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { FilterOutlined } from '@ant-design/icons-vue'

defineProps({
  title: { type: String, default: '' },
  open: { type: Boolean, default: false },
  active: { type: Boolean, default: false },
  keyword: { type: String, default: '' },
  placeholder: { type: String, default: '' },
})

defineEmits(['openChange', 'update:keyword', 'reset', 'apply'])

const { t } = useI18n()
</script>
