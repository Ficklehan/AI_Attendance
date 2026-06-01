import { computed, unref } from 'vue'
import { autoSizeTableColumns, sumTableScrollX } from '@/utils/tableAutoColumns'

/**
 * 在列定义与数据变化时自动估算列宽。
 * @param {import('vue').MaybeRefOrGetter<Array>} columnsSource
 * @param {import('vue').MaybeRefOrGetter<Array>} recordsSource
 * @param {object} [options] 传给 autoSizeTableColumns
 */
export function useAutoSizedColumns(columnsSource, recordsSource, options = {}) {
  const columns = computed(() => {
    const cols = unref(columnsSource) || []
    const rows = unref(recordsSource) || []
    if (!cols.length) return cols
    return autoSizeTableColumns(cols, rows, options)
  })

  const scrollX = computed(() => sumTableScrollX(columns.value))

  return { columns, scrollX }
}
