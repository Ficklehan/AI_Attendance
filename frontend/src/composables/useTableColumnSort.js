import { ref, computed, unref } from 'vue'
import {
  withTableSorters,
  applySortToData,
  attachSortOrder,
  nextSortOrder,
  getColumnSortFn,
} from '@/utils/tableSort'

/**
 * 客户端表格排序：列定义带 sorter / sortOrder，数据经 sortRows 输出。
 */
export function useTableColumnSort(columnsSource, options = {}) {
  const sortState = ref({ columnKey: null, order: null })

  const columns = computed(() => {
    const base = withTableSorters(unref(columnsSource) || [], options)
    return attachSortOrder(base, sortState.value)
  })

  function onSorterToggle(column, order) {
    const key = column?.key || column?.dataIndex
    if (!key || !getColumnSortFn(column)) return
    const next = order !== undefined ? order : nextSortOrder(
      sortState.value.columnKey === key ? sortState.value.order : null,
    )
    sortState.value = { columnKey: next ? key : null, order: next }
  }

  function sortRows(rows) {
    return applySortToData(rows, sortState.value, columns.value)
  }

  function resetSort() {
    sortState.value = { columnKey: null, order: null }
  }

  return { columns, sortState, onSorterToggle, sortRows, resetSort }
}
