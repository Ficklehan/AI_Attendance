import {
  ref,
  unref,
  shallowRef,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick,
} from 'vue'

const DEFAULT_BOTTOM_GAP = 24
const DEFAULT_MIN_HEIGHT = 240

/**
 * Ant Design Vue Table scroll.y：表体在表格内纵向滚动，表头固定。
 * 根据锚点距视口顶部的距离计算可用高度（mount / resize / 显式 remeasure，不在 scroll 时更新）。
 *
 * @param {import('vue').Ref<HTMLElement|null>} anchorRef 紧贴 <a-table> 的包裹元素
 * @param {import('vue').MaybeRefOrGetter<number|string|undefined>} scrollXRef 横向 scroll.x
 * @param {{ bottomGap?: number, minHeight?: number, reservedBottom?: number, enabled?: import('vue').MaybeRefOrGetter<boolean> }} [options]
 */
export function useTableBodyScrollY(anchorRef, scrollXRef, options = {}) {
  const bottomGap = options.bottomGap ?? DEFAULT_BOTTOM_GAP
  const minHeight = options.minHeight ?? DEFAULT_MIN_HEIGHT
  const reservedBottom = options.reservedBottom ?? 0
  const enabled = options.enabled

  const scrollY = ref(undefined)
  const tableScroll = shallowRef({ x: unref(scrollXRef) })

  let rafId = 0
  let scrollTimer = 0

  const onWindowScroll = () => {
    if (scrollTimer) window.clearTimeout(scrollTimer)
    scrollTimer = window.setTimeout(() => {
      scrollTimer = 0
      scheduleMeasure()
    }, 150)
  }

  const isEnabled = () => {
    if (enabled === undefined) return true
    return Boolean(typeof enabled === 'function' ? enabled() : unref(enabled))
  }

  const syncTableScroll = () => {
    const x = unref(scrollXRef)
    const y = isEnabled() && scrollY.value != null && scrollY.value > 0
      ? scrollY.value
      : undefined
    const cur = tableScroll.value
    if (cur.x === x && cur.y === y) return
    tableScroll.value = y != null ? { x, y } : { x }
  }

  const measure = () => {
    if (!isEnabled()) {
      if (scrollY.value !== undefined) {
        scrollY.value = undefined
        syncTableScroll()
      }
      return
    }
    const el = anchorRef.value
    if (!el) return
    const rect = el.getBoundingClientRect()
    const top = Math.max(0, rect.top)
    const next = Math.max(
      minHeight,
      Math.floor(window.innerHeight - top - bottomGap - reservedBottom),
    )
    if (next === scrollY.value) return
    scrollY.value = next
    syncTableScroll()
  }

  const scheduleMeasure = () => {
    if (rafId) cancelAnimationFrame(rafId)
    rafId = requestAnimationFrame(() => {
      rafId = 0
      measure()
    })
  }

  onMounted(() => {
    nextTick(() => scheduleMeasure())
    window.addEventListener('resize', scheduleMeasure, { passive: true })
    window.addEventListener('scroll', onWindowScroll, { passive: true })
  })

  onBeforeUnmount(() => {
    if (rafId) cancelAnimationFrame(rafId)
    if (scrollTimer) window.clearTimeout(scrollTimer)
    window.removeEventListener('resize', scheduleMeasure)
    window.removeEventListener('scroll', onWindowScroll)
  })

  watch(anchorRef, () => {
    nextTick(() => scheduleMeasure())
  })

  if (enabled !== undefined) {
    watch(
      () => (typeof enabled === 'function' ? enabled() : unref(enabled)),
      () => nextTick(() => scheduleMeasure()),
    )
  }

  watch(
    () => unref(scrollXRef),
    () => syncTableScroll(),
  )

  return { scrollY, tableScroll, measure: scheduleMeasure }
}
