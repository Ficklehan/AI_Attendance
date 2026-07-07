<template>
  <div
    class="table-sortable-header"
    :class="[
      alignClass,
      {
        'table-sortable-header--has-filter': hasFilter,
        'table-sortable-header--has-sort': sortable,
        'table-sortable-header--compact': compact,
      },
    ]"
  >
    <div v-if="sortable" class="table-sortable-header__leading">
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

    <div class="table-sortable-header__title" :title="titleText">
      <span class="table-sortable-header__title-text">{{ title }}</span>
    </div>

    <div v-if="hasFilter" class="table-sortable-header__trailing">
      <slot name="extra" />
    </div>

    <button
      v-if="resizable"
      type="button"
      class="table-sortable-header__resize-handle"
      :aria-label="t('table.resizeColumn')"
      @pointerdown.stop.prevent="emit('resize-start', $event)"
    />
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
  compact: { type: Boolean, default: false },
  resizable: { type: Boolean, default: false },
})

const emit = defineEmits(['sort', 'resize-start'])
const slots = useSlots()
const { t } = useI18n()

const sortable = computed(() => columnIsSortable(props.column))
const hasFilter = computed(() => typeof slots.extra === 'function')

const alignClass = computed(() => {
  const align = props.column?.align || 'left'
  return `table-sortable-header--align-${align}`
})

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
  z-index: 1;
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
  justify-content: flex-start;
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
  padding: 0 calc($icon-size + $icon-gap) 0 4px;
}

.table-sortable-header--has-sort:not(.table-sortable-header--has-filter) .table-sortable-header__title {
  padding: 0 4px 0 calc($icon-size + $icon-gap);
}

.table-sortable-header__title-text {
  display: block;
  max-width: 100%;
  white-space: nowrap;
  text-align: left;
  line-height: 1.35;
  font-weight: 600;
  font-size: 12px;
  color: #344054;
  user-select: none;
}

.table-sortable-header--align-center .table-sortable-header__title {
  justify-content: center;
}

.table-sortable-header--align-center .table-sortable-header__title-text {
  text-align: center;
}

.table-sortable-header--align-right .table-sortable-header__title {
  justify-content: flex-end;
}

.table-sortable-header--align-right .table-sortable-header__title-text {
  text-align: right;
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

.table-sortable-header__trailing :deep(.table-header-filter-btn) {
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

$table-compact-icon-size: 18px;

.table-sortable-header--compact {
  min-height: 30px;
  gap: 2px;
  align-items: center;

  .table-sortable-header__leading,
  .table-sortable-header__trailing {
    position: static;
    top: auto;
    left: auto;
    right: auto;
    transform: none;
    flex: 0 0 auto;
    margin-top: 0;
  }

  .table-sortable-header__title {
    position: static;
    top: auto;
    left: auto;
    right: auto;
    transform: none;
    flex: 1 1 auto;
    width: auto;
    min-width: 0;
    padding: 0 !important;
    pointer-events: auto;
    justify-content: flex-start;
  }

  .table-sortable-header__title-text {
    white-space: normal;
    word-break: break-word;
    line-height: 1.2;
    font-size: 11px;
    text-align: left;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &.table-sortable-header--align-center .table-sortable-header__title,
  &.table-sortable-header--align-right .table-sortable-header__title {
    justify-content: flex-start;
  }

  &.table-sortable-header--align-center .table-sortable-header__title-text,
  &.table-sortable-header--align-right .table-sortable-header__title-text {
    text-align: left;
  }

  .table-sortable-header__icon-btn {
    width: $table-compact-icon-size;
    height: $table-compact-icon-size;
    border-radius: 4px;
  }

  .table-sortable-header__sort-arrows {
    font-size: 8px;
    line-height: 0.62;
  }

  .table-sortable-header__trailing :deep(.table-header-filter-btn) {
    width: $table-compact-icon-size !important;
    height: $table-compact-icon-size !important;
    min-width: $table-compact-icon-size !important;
    border-radius: 4px !important;

    .anticon {
      font-size: 11px;
    }
  }
}

.table-sortable-header__resize-handle {
  position: absolute;
  top: 0;
  right: -4px;
  z-index: 3;
  width: 8px;
  height: 100%;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: col-resize;
  touch-action: none;

  &::after {
    content: '';
    position: absolute;
    top: 18%;
    bottom: 18%;
    left: 50%;
    width: 2px;
    transform: translateX(-50%);
    border-radius: 1px;
    background: transparent;
    transition: background 0.15s ease;
  }

  &:hover::after,
  &:focus-visible::after {
    background: rgba(22, 119, 255, 0.55);
  }
}
</style>
