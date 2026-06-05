import { computed, ref, unref, watch } from 'vue'
import {
  applyColumnFreeze,
  applyColumnVisibility,
  enforceFrozenPrefix,
  getColumnKey,
  getConfigurableColumns,
  getFreezeableColumns,
  loadColumnFreeze,
  loadHiddenColumns,
  saveColumnFreeze,
  saveHiddenColumns,
} from '@/utils/columnDisplay'

/**
 * 表格列显示与冻结（localStorage 持久化）。
 * @param {string} storageId 各列表唯一 ID
 * @param {import('vue').MaybeRefOrGetter<Array>} columnsSource
 * @param {{ defaultFrozen?: string[], defaultHidden?: string[], preserveRightFixed?: boolean, minVisible?: number }} [options]
 */
export function useColumnFreeze(storageId, columnsSource, options = {}) {
  const hiddenKeys = ref(loadHiddenColumns(storageId, options.defaultHidden || []))
  const frozenKeys = ref(loadColumnFreeze(storageId, options.defaultFrozen || []))

  const configurableColumns = computed(() => getConfigurableColumns(unref(columnsSource) || []))

  const visibleColumns = computed(() => applyColumnVisibility(
    unref(columnsSource) || [],
    hiddenKeys.value,
    { minVisible: options.minVisible ?? 1 },
  ))

  const freezeableColumns = computed(() => getFreezeableColumns(visibleColumns.value))

  const orderedFreezeableKeys = computed(() => freezeableColumns.value.map(getColumnKey).filter(Boolean))

  function reconcileFrozenKeys() {
    const hidden = new Set(hiddenKeys.value)
    const pruned = frozenKeys.value.filter((key) => !hidden.has(key))
    frozenKeys.value = enforceFrozenPrefix(orderedFreezeableKeys.value, pruned)
  }

  watch([orderedFreezeableKeys, hiddenKeys], reconcileFrozenKeys, { immediate: true, deep: true })

  watch(hiddenKeys, (keys) => {
    saveHiddenColumns(storageId, keys)
  }, { deep: true })

  watch(frozenKeys, (keys) => {
    saveColumnFreeze(storageId, keys)
  }, { deep: true })

  const displayColumns = computed(() => applyColumnFreeze(
    visibleColumns.value,
    frozenKeys.value,
    options,
  ))

  function setHiddenKeys(keys) {
    hiddenKeys.value = [...new Set((keys || []).map(String))]
    reconcileFrozenKeys()
  }

  function setColumnVisible(columnKey, visible) {
    const key = String(columnKey || '')
    if (!key) return
    const allKeys = configurableColumns.value.map(getColumnKey).filter(Boolean)
    if (!visible) {
      const nextHidden = [...new Set([...hiddenKeys.value, key])]
      const wouldRemain = allKeys.filter((k) => !nextHidden.includes(k)).length
      if (wouldRemain < (options.minVisible ?? 1)) return
      hiddenKeys.value = nextHidden
    } else {
      hiddenKeys.value = hiddenKeys.value.filter((k) => k !== key)
    }
    reconcileFrozenKeys()
  }

  function showAllColumns() {
    hiddenKeys.value = []
    reconcileFrozenKeys()
  }

  function setFrozenKeys(keys) {
    frozenKeys.value = enforceFrozenPrefix(orderedFreezeableKeys.value, keys)
  }

  function clearFrozenKeys() {
    frozenKeys.value = []
  }

  const hiddenCount = computed(() => hiddenKeys.value.length)
  const frozenCount = computed(() => frozenKeys.value.length)

  return {
    hiddenKeys,
    frozenKeys,
    hiddenCount,
    frozenCount,
    displayColumns,
    frozenColumns: displayColumns,
    configurableColumns,
    freezeableColumns,
    visibleColumns,
    setHiddenKeys,
    setColumnVisible,
    showAllColumns,
    setFrozenKeys,
    clearFrozenKeys,
  }
}
