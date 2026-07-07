import { computed, onUnmounted, ref, unref, watch } from 'vue'
import {
  applyUserColumnWidths,
  getColumnKey,
  loadColumnWidths,
  saveColumnWidths,
} from '@/utils/columnDisplay'
import { sumTableScrollX } from '@/utils/tableAutoColumns'

const DEFAULT_MIN_WIDTH = 48
const DEFAULT_MAX_WIDTH = 520

/**
 * 表格列宽：合并用户拖动结果（localStorage）并支持表头拖拽调整。
 * @param {string} storageId
 * @param {import('vue').MaybeRefOrGetter<Array>} columnsSource
 * @param {{ nonResizableKeys?: string[], minWidth?: number, maxWidth?: number, scrollBuffer?: number }} [options]
 */
export function useTableColumnResize(storageId, columnsSource, options = {}) {
  const nonResizable = new Set(options.nonResizableKeys || ['action'])
  const minWidth = options.minWidth ?? DEFAULT_MIN_WIDTH
  const maxWidth = options.maxWidth ?? DEFAULT_MAX_WIDTH

  const userWidths = ref(loadColumnWidths(storageId))
  let activeResize = null

  const clampWidth = (width) => Math.round(Math.min(maxWidth, Math.max(minWidth, width)))

  const columns = computed(() => applyUserColumnWidths(
    unref(columnsSource) || [],
    userWidths.value,
    nonResizable,
  ))

  const scrollX = computed(() => sumTableScrollX(columns.value, options.scrollBuffer))

  const isColumnResizable = (column) => {
    const key = getColumnKey(column)
    if (!key || nonResizable.has(key)) return false
    if (column?.fixed === 'right') return false
    if (column?.resizable === false) return false
    return true
  }

  const stopResize = () => {
    if (!activeResize) return
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', onPointerUp)
    document.removeEventListener('pointercancel', onPointerUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    activeResize = null
  }

  const onPointerMove = (event) => {
    if (!activeResize) return
    const delta = event.clientX - activeResize.startX
    const next = clampWidth(activeResize.startWidth + delta)
    userWidths.value = { ...userWidths.value, [activeResize.key]: next }
  }

  const onPointerUp = () => {
    stopResize()
  }

  const startColumnResize = (column, event) => {
    if (!isColumnResizable(column)) return
    const key = getColumnKey(column)
    const startWidth = userWidths.value[key] ?? column.width ?? column.minWidth ?? 96
    activeResize = { key, startX: event.clientX, startWidth }
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('pointermove', onPointerMove)
    document.addEventListener('pointerup', onPointerUp)
    document.addEventListener('pointercancel', onPointerUp)
    if (event.currentTarget?.setPointerCapture) {
      try {
        event.currentTarget.setPointerCapture(event.pointerId)
      } catch {
        /* ignore */
      }
    }
  }

  watch(userWidths, (widths) => {
    saveColumnWidths(storageId, widths)
  }, { deep: true })

  onUnmounted(stopResize)

  const clearColumnWidths = () => {
    userWidths.value = {}
    saveColumnWidths(storageId, {})
  }

  return {
    columns,
    scrollX,
    userWidths,
    isColumnResizable,
    startColumnResize,
    clearColumnWidths,
  }
}
