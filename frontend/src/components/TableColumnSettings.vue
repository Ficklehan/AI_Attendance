<template>
  <a-popover
    trigger="click"
    placement="bottomRight"
    overlay-class-name="column-settings-overlay"
  >
    <template #content>
      <div class="column-settings-panel">
        <div class="column-settings-panel__head">
          <SettingOutlined />
          <span>{{ t('table.columnSettings') }}</span>
        </div>

        <div class="column-settings-panel__section">
          <div class="column-settings-panel__section-title">{{ t('table.visibleColumns') }}</div>
          <p class="column-settings-panel__hint">{{ t('table.visibleColumnsHint') }}</p>
          <div class="column-settings-panel__list">
            <label
              v-for="col in columns"
              :key="`vis-${getColumnKey(col)}`"
              class="column-settings-panel__item"
            >
              <a-checkbox
                :checked="isVisible(col)"
                :disabled="isVisibilityLocked(col)"
                @change="(e) => onVisibilityChange(col, e.target.checked)"
              />
              <span class="column-settings-panel__label">{{ formatColumnTitle(col.title) }}</span>
            </label>
          </div>
        </div>

        <div class="column-settings-panel__section">
          <div class="column-settings-panel__section-title">{{ t('table.freezeColumns') }}</div>
          <p class="column-settings-panel__hint">{{ t('table.freezeColumnsHint') }}</p>
          <div class="column-settings-panel__list">
            <label
              v-for="(col, idx) in freezeColumns"
              :key="`freeze-${getColumnKey(col)}`"
              class="column-settings-panel__item"
            >
              <a-checkbox
                :checked="isFrozen(col, idx)"
                @change="(e) => onFreezeToggle(idx, e.target.checked)"
              />
              <span class="column-settings-panel__label">{{ formatColumnTitle(col.title) }}</span>
            </label>
          </div>
        </div>

        <div class="column-settings-panel__actions">
          <a-button size="small" @click="showAll">{{ t('table.showAllColumns') }}</a-button>
          <a-button size="small" @click="clearFreeze">{{ t('table.freezeReset') }}</a-button>
        </div>
      </div>
    </template>
    <a-button class="column-settings-trigger">
      <template #icon><SettingOutlined /></template>
      {{ t('table.columnSettings') }}
      <span v-if="badgeCount" class="column-settings-trigger__badge">{{ badgeCount }}</span>
    </a-button>
  </a-popover>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { SettingOutlined } from '@ant-design/icons-vue'
import { formatColumnTitle, getColumnKey, getFreezeableColumns } from '@/utils/columnDisplay'

const props = defineProps({
  columns: { type: Array, default: () => [] },
  hiddenKeys: { type: Array, default: () => [] },
  frozenKeys: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:hiddenKeys', 'update:frozenKeys', 'visibility-change', 'show-all', 'clear-freeze'])

const { t } = useI18n()

const hiddenSet = computed(() => new Set(props.hiddenKeys))

const visibleColumns = computed(() => props.columns.filter((col) => {
  const key = getColumnKey(col)
  return !key || !hiddenSet.value.has(key)
}))

const freezeColumns = computed(() => getFreezeableColumns(visibleColumns.value))

const freezeOrderedKeys = computed(() => freezeColumns.value.map(getColumnKey).filter(Boolean))

const badgeCount = computed(() => props.hiddenKeys.length + props.frozenKeys.length)

function isVisible(col) {
  const key = getColumnKey(col)
  return !key || !hiddenSet.value.has(key)
}

function isVisibilityLocked(col) {
  if (!isVisible(col)) return false
  return visibleColumns.value.length <= 1 && isVisible(col)
}

function onVisibilityChange(col, checked) {
  const key = getColumnKey(col)
  if (!key) return
  emit('visibility-change', key, checked)
  const nextHidden = new Set(props.hiddenKeys)
  if (checked) {
    nextHidden.delete(key)
  } else {
    nextHidden.add(key)
  }
  emit('update:hiddenKeys', [...nextHidden])
}

function isFrozen(_col, idx) {
  const key = freezeOrderedKeys.value[idx]
  return key ? props.frozenKeys.includes(key) : false
}

function onFreezeToggle(idx, checked) {
  if (checked) {
    emit('update:frozenKeys', freezeOrderedKeys.value.slice(0, idx + 1))
    return
  }
  emit('update:frozenKeys', freezeOrderedKeys.value.slice(0, idx))
}

function showAll() {
  emit('show-all')
  emit('update:hiddenKeys', [])
}

function clearFreeze() {
  emit('clear-freeze')
  emit('update:frozenKeys', [])
}
</script>

<style scoped lang="scss">
.column-settings-trigger {
  position: relative;
}

.column-settings-trigger__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 9px;
  background: rgba(22, 119, 255, 0.12);
  color: #1677ff;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.column-settings-panel {
  width: min(300px, calc(100vw - 32px));
}

.column-settings-panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #101828;
}

.column-settings-panel__section + .column-settings-panel__section {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #f0f0f0;
}

.column-settings-panel__section-title {
  font-size: 13px;
  font-weight: 600;
  color: #344054;
  margin-bottom: 4px;
}

.column-settings-panel__hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #667085;
}

.column-settings-panel__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.column-settings-panel__item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.column-settings-panel__label {
  font-size: 13px;
  color: #344054;
  line-height: 1.35;
}

.column-settings-panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}
</style>

<style lang="scss">
.column-settings-overlay {
  .ant-popover-inner {
    padding: 14px 16px !important;
    border-radius: 10px !important;
  }
}
</style>
