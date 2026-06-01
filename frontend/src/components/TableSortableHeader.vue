<template>
  <div
    class="table-sortable-header"
    :class="{
      'table-sortable-header--has-filter': hasFilter,
      'table-sortable-header--has-sort': sortable,
    }"
  >
    <div v-if="hasFilter" class="table-sortable-header__leading">
      <slot name="extra" />
    </div>

    <div class="table-sortable-header__title" :title="titleText">
      <span class="table-sortable-header__title-text">{{ title }}</span>
    </div>

    <div v-if="sortable" class="table-sortable-header__trailing">
      <a-tooltip :title="t('common.sortColumn')" placement="top">
        <button
          type="button"
          class="table-sortable-header__icon-btn"
          :class="{ 'is-active': !!column.sortOrder }"
          :aria-label="sortAriaLabel"
          @click.stop="handleSorterClick"
        >
          <span class="table-sortable-header__sort-arrows" aria-hidden="true">
            <CaretUpOutlined
              class="table-sortable-header__sort-up"
              :class="{ active: column.sortOrder === 'ascend' }"
            />
            <CaretDownOutlined
              class="table-sortable-header__sort-down"
              :class="{ active: column.sortOrder === 'descend' }"
            />
          </span>
        </button>
      </a-tooltip>
    </div>
  </div>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import { useI18n } from 'vue-i18n'
import { CaretUpOutlined, CaretDownOutlined } from '@ant-design/icons-vue'
import { columnIsSortable, nextSortOrder } from '@/utils/tableSort'

const props = defineProps({
  column: { type: Object, required: true },
  title: { type: [String, Number, Object], default: '' },
})

const emit = defineEmits(['sort'])
const slots = useSlots()
const { t } = useI18n()

const sortable = computed(() => columnIsSortable(props.column))
const hasFilter = computed(() => typeof slots.extra === 'function')

const titleText = computed(() => {
  const v = props.title
  if (v == null) return ''
  return typeof v === 'string' ? v : String(v)
})

const sortAriaLabel = computed(() => {
  if (props.column.sortOrder === 'ascend') return t('common.sortAsc')
  if (props.column.sortOrder === 'descend') return t('common.sortDesc')
  return t('common.sortColumn')
})

const handleSorterClick = () => {
  if (!sortable.value) return
  emit('sort', props.column, nextSortOrder(props.column.sortOrder ?? null))
}
</script>

<style scoped lang="scss">
$icon-size: 24px;
$icon-gap: 4px;

.table-sortable-header {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  min-height: 34px;
  height: 100%;
}

.table-sortable-header__leading,
.table-sortable-header__trailing {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  display: flex;
  align-items: center;
}

.table-sortable-header__leading {
  left: 0;
}

.table-sortable-header__trailing {
  right: 0;
}

.table-sortable-header__title {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  pointer-events: none;
}

.table-sortable-header__title-text {
  pointer-events: auto;
}

.table-sortable-header--has-filter.table-sortable-header--has-sort .table-sortable-header__title {
  padding: 0 calc($icon-size + $icon-gap);
}

.table-sortable-header--has-filter:not(.table-sortable-header--has-sort) .table-sortable-header__title {
  padding: 0 4px 0 calc($icon-size + $icon-gap);
}

.table-sortable-header--has-sort:not(.table-sortable-header--has-filter) .table-sortable-header__title {
  padding: 0 calc($icon-size + $icon-gap) 0 4px;
}

.table-sortable-header__title-text {
  display: block;
  max-width: 100%;
  white-space: nowrap;
  text-align: center;
  line-height: 1.35;
  font-weight: 600;
  font-size: 12px;
  color: #344054;
  user-select: none;
}

.table-sortable-header__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: $icon-size;
  height: $icon-size;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
  line-height: 1;
  transition: background 0.15s ease, color 0.15s ease;

  &:hover {
    background: rgba(15, 23, 42, 0.06);
    color: #475467;
  }

  &.is-active {
    background: rgba(22, 119, 255, 0.1);
    color: #1677ff;
  }
}

.table-sortable-header__sort-arrows {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  font-size: 10px;
  line-height: 0.68;
  pointer-events: none;
}

.table-sortable-header__sort-up.active,
.table-sortable-header__sort-down.active {
  color: #1677ff;
}

.table-sortable-header__leading :deep(.table-header-filter-btn) {
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  width: $icon-size !important;
  height: $icon-size !important;
  min-width: $icon-size !important;
  padding: 0 !important;
  border: none !important;
  border-radius: 6px !important;
  background: transparent !important;
  color: #98a2b3 !important;
  box-shadow: none !important;

  &:hover {
    background: rgba(15, 23, 42, 0.06) !important;
    color: #475467 !important;
  }

  &.table-header-filter-btn--active {
    background: rgba(22, 119, 255, 0.1) !important;
    color: #1677ff !important;
  }

  .anticon {
    font-size: 13px;
  }
}
</style>
