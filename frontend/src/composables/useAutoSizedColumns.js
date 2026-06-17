import { computed, unref } from 'vue'
import { autoSizeTableColumns, sumTableScrollX } from '@/utils/tableAutoColumns'

function isAutoSizeEnabled(options) {
  if (options.enabled === undefined) return true
  return unref(options.enabled)
}

/**
 * 在列定义与数据变化时自动估算列宽。
 * @param {import('vue').MaybeRefOrGetter<Array>} columnsSource
 * @param {import('vue').MaybeRefOrGetter<Array>} recordsSource
 * @param {object} [options] 传给 autoSizeTableColumns；`enabled: false` 时冻结上次结果，避免锁定列宽后仍全表重算
 */
export function useAutoSizedColumns(columnsSource, recordsSource, options = {}) {
  let lastSizedColumns = null

  const columns = computed(() => {
    const cols = unref(columnsSource) || []
    if (!cols.length) {
      if (!isAutoSizeEnabled(options) && lastSizedColumns?.length) return lastSizedColumns
      return cols
    }
    if (!isAutoSizeEnabled(options)) {
      return lastSizedColumns?.length ? lastSizedColumns : cols
    }
    const rows = unref(recordsSource) || []
    lastSizedColumns = autoSizeTableColumns(cols, rows, options)
    return lastSizedColumns
  })

  const scrollX = computed(() => sumTableScrollX(columns.value))

  return { columns, scrollX }
}
