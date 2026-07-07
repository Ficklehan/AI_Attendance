const DEFAULT_WIDTH = 44
const MIN_WIDTH = 30
const MAX_WIDTH = 58

export function useImageDockResize(storageKey) {
  let resizing = false
  let layoutEl = null
  let dockEl = null
  let resizerEl = null
  let pointerId = null

  const readStoredWidth = () => {
    const raw = Number(sessionStorage.getItem(storageKey))
    if (!Number.isFinite(raw)) return DEFAULT_WIDTH
    return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, raw))
  }

  let widthPercent = readStoredWidth()

  const clampPercent = (percent) => Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, percent))

  const getTrackWidth = () => {
    if (!layoutEl) return 0
    const rect = layoutEl.getBoundingClientRect()
    const styles = getComputedStyle(layoutEl)
    const gap = parseFloat(styles.columnGap || styles.gap) || 0
    return Math.max(rect.width - gap, 1)
  }

  const applyWidth = (percent) => {
    widthPercent = clampPercent(percent)
    const widthValue = `${widthPercent}%`

    if (dockEl) {
      dockEl.style.flex = `0 0 ${widthValue}`
      dockEl.style.width = widthValue
      dockEl.style.maxWidth = 'none'
      dockEl.style.minWidth = '0'
      dockEl.style.setProperty('--image-dock-width', widthValue)
    }

    document.documentElement.style.setProperty('--image-dock-width', widthValue)
  }

  const onPointerMove = (event) => {
    if (!resizing || !layoutEl || !dockEl) return
    if (pointerId != null && event.pointerId !== pointerId) return

    const rect = layoutEl.getBoundingClientRect()
    const trackWidth = getTrackWidth()
    const dockWidthPx = Math.max(0, rect.right - event.clientX)
    const percent = clampPercent((dockWidthPx / trackWidth) * 100)
    applyWidth(percent)
  }

  const stopResize = (event) => {
    if (!resizing) return
    if (event && pointerId != null && event.pointerId !== pointerId) return

    resizing = false
    if (resizerEl?.releasePointerCapture && pointerId != null) {
      try {
        resizerEl.releasePointerCapture(pointerId)
      } catch {
        /* ignore */
      }
    }
    pointerId = null
    resizerEl = null
    sessionStorage.setItem(storageKey, String(Math.round(widthPercent)))
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', stopResize)
    document.removeEventListener('pointercancel', stopResize)
  }

  const startResize = (event) => {
    if (event.button !== 0 && event.pointerType === 'mouse') return

    resizerEl = event.currentTarget
    dockEl = resizerEl?.closest?.('.image-compare-layout__dock')
    layoutEl = resizerEl?.closest?.('.image-compare-layout')
    if (!dockEl || !layoutEl) return

    resizing = true
    pointerId = event.pointerId
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'

    if (resizerEl?.setPointerCapture) {
      try {
        resizerEl.setPointerCapture(event.pointerId)
      } catch {
        /* ignore */
      }
    }

    document.addEventListener('pointermove', onPointerMove)
    document.addEventListener('pointerup', stopResize)
    document.addEventListener('pointercancel', stopResize)
    event.preventDefault()
    event.stopPropagation()
    onPointerMove(event)
  }

  const onWindowResize = () => {
    if (dockEl && layoutEl) {
      applyWidth(widthPercent)
    }
  }

  const bindDock = (dock) => {
    dockEl = dock || dockEl
    if (!dockEl) return
    layoutEl = dockEl.closest('.image-compare-layout')
    applyWidth(readStoredWidth())
    window.addEventListener('resize', onWindowResize)
  }

  const initWidth = () => {
    widthPercent = readStoredWidth()
    applyWidth(widthPercent)
  }

  const teardown = () => {
    window.removeEventListener('resize', onWindowResize)
    document.removeEventListener('pointermove', onPointerMove)
    document.removeEventListener('pointerup', stopResize)
    document.removeEventListener('pointercancel', stopResize)
    if (resizing) {
      resizing = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    layoutEl = null
    dockEl = null
    resizerEl = null
    pointerId = null
  }

  return {
    startResize,
    stopResize,
    bindDock,
    initWidth,
    teardown,
  }
}
