import { onUnmounted } from 'vue'

const STICKY_TOP_OFFSET = 68
const DEFAULT_BOTTOM_RESERVE = 12
const SUBMIT_BAR_BOTTOM_RESERVE = 88
const MIN_DOCK_HEIGHT = 280

/**
 * 根据 dock 在视口中的实际 top 动态计算高度，保证不超出可视区域（含底部提交栏）。
 */
export function useImageDockViewportHeight(dockRef) {
  let raf = 0
  let bound = false

  const resolveBottomReserve = (el) => {
    if (el?.closest('.has-sticky-submit')) {
      return SUBMIT_BAR_BOTTOM_RESERVE
    }
    return DEFAULT_BOTTOM_RESERVE
  }

  const sync = () => {
    const el = dockRef.value
    if (!el) return

    cancelAnimationFrame(raf)
    raf = requestAnimationFrame(() => {
      const rect = el.getBoundingClientRect()
      const top = Math.max(rect.top, STICKY_TOP_OFFSET)
      const bottom = resolveBottomReserve(el)
      const height = Math.max(MIN_DOCK_HEIGHT, Math.floor(window.innerHeight - top - bottom))

      el.style.setProperty('--image-dock-height', `${height}px`)
      el.style.height = `${height}px`
      el.style.maxHeight = `${height}px`
    })
  }

  const onViewportChange = () => {
    sync()
  }

  const bind = () => {
    if (bound) return
    bound = true
    sync()
    window.addEventListener('resize', onViewportChange, { passive: true })
    window.addEventListener('scroll', onViewportChange, { passive: true })
  }

  const unbind = () => {
    if (!bound) return
    bound = false
    window.removeEventListener('resize', onViewportChange)
    window.removeEventListener('scroll', onViewportChange)
    cancelAnimationFrame(raf)
  }

  onUnmounted(() => {
    unbind()
  })

  return { sync, bind, unbind }
}
