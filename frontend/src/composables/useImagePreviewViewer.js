import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { detectTableHeaderRotation, invalidateOrientationCache, warmupOcrWorker } from '@/utils/imageAutoOrient'

const MIN_USER_SCALE = 0.25
const MAX_USER_SCALE = 5
const ZOOM_STEP = 1.2
const AUTO_ORIENT_TIMEOUT_MS = 12000

export function useImagePreviewViewer(options = {}) {
  const images = computed(() => options.images?.value ?? options.images ?? [])
  const initialIndex = computed(() => options.initialIndex?.value ?? options.initialIndex ?? 0)
  const controlledIndex = computed(() => {
    if (options.index == null) return null
    const raw = options.index.value ?? options.index
    return raw === undefined || raw === null ? null : raw
  })
  const active = computed(() => options.active?.value ?? options.active ?? true)
  const autoOrientEnabled = computed(() => options.autoOrientEnabled?.value ?? options.autoOrientEnabled ?? true)
  const viewportMinHeight = computed(() => options.viewportMinHeight?.value ?? options.viewportMinHeight ?? '52vh')
  const fitMode = computed(() => options.fitMode?.value ?? options.fitMode ?? 'modal')

  const viewportRef = ref(null)
  const imgRef = ref(null)
  const currentIndex = ref(0)
  const baseScale = ref(1)
  const userScale = ref(1)
  const rotation = ref(0)
  const offsetX = ref(0)
  const offsetY = ref(0)
  const dragging = ref(false)
  const dragStart = ref({ x: 0, y: 0, ox: 0, oy: 0 })
  const pointerDownAt = ref(null)
  const orienting = ref(false)
  const manualRotationKeys = ref(new Set())

  const currentSrc = computed(() => images.value[currentIndex.value] || '')
  const scalePercent = computed(() => Math.round(baseScale.value * userScale.value * 100))
  const imageStyle = computed(() => ({
    transform: `translate(${offsetX.value}px, ${offsetY.value}px) rotate(${rotation.value}deg) scale(${baseScale.value * userScale.value})`,
  }))

  const resetPanZoom = () => {
    userScale.value = 1
    offsetX.value = 0
    offsetY.value = 0
  }

  const fitToViewport = (fitOptions = {}) => {
    const { preserveRotation = false } = fitOptions
    const viewport = viewportRef.value
    const img = imgRef.value
    if (!viewport || !img || !img.naturalWidth) return

    const vw = viewport.clientWidth
    const vh = viewport.clientHeight
    const nw = img.naturalWidth
    const nh = img.naturalHeight
    const rot = rotation.value % 180 !== 0
    const w = rot ? nh : nw
    const h = rot ? nw : nh
    const isDock = fitMode.value === 'dock'
    const padding = isDock ? 4 : 24
    const scaleW = (vw - padding) / w
    const scaleH = (vh - padding) / h
    let scale
    if (isDock) {
      scale = Math.min(scaleW, scaleH)
      scale = Math.min(scale, 2.5)
    } else {
      scale = Math.min(scaleW, scaleH, 1)
    }
    baseScale.value = scale > 0 ? scale : 1
    resetPanZoom()
    if (!preserveRotation) {
      rotation.value = 0
    }
  }

  const markManualRotation = () => {
    const key = `${currentIndex.value}:${currentSrc.value}`
    const next = new Set(manualRotationKeys.value)
    next.add(key)
    manualRotationKeys.value = next
  }

  const shouldAutoOrient = () => {
    if (!autoOrientEnabled.value) return false
    const src = currentSrc.value
    if (typeof src === 'string' && src.startsWith('blob:')) return false
    const key = `${currentIndex.value}:${currentSrc.value}`
    return !manualRotationKeys.value.has(key)
  }

  const withTimeout = (promise, timeoutMs) => Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error('auto-orient timeout')), timeoutMs)
    }),
  ])

  let orientToken = 0
  const runAutoOrient = async () => {
    if (!shouldAutoOrient() || !currentSrc.value) {
      orienting.value = false
      nextTick(() => fitToViewport({ preserveRotation: true }))
      return
    }
    const token = ++orientToken
    orienting.value = true
    try {
      const { rotation: detected, confidence } = await withTimeout(
        detectTableHeaderRotation(currentSrc.value),
        AUTO_ORIENT_TIMEOUT_MS,
      )
      if (token !== orientToken) return
      if (confidence !== 'none') {
        rotation.value = detected
      }
    } catch {
      /* keep current rotation */
    } finally {
      if (token === orientToken) {
        orienting.value = false
        nextTick(() => fitToViewport({ preserveRotation: true }))
      }
    }
  }

  const onImageLoad = () => {
    nextTick(() => fitToViewport({ preserveRotation: true }))
    if (shouldAutoOrient()) {
      runAutoOrient()
      return
    }
    orienting.value = false
  }

  const onImageError = (event) => {
    orienting.value = false
    orientToken += 1
    event.target.src =
      'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBkb21pbmFudC1iYXNlbGluZT0ibWlkZGxlIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmJmIiBmb250LXNpemU9IjE2Ij7lm77niYfliqDovb3lpLHotKU88L3RleHQ+PC9zdmc+'
    nextTick(() => fitToViewport({ preserveRotation: true }))
  }

  const clampUserScale = (value) => Math.max(MIN_USER_SCALE, Math.min(MAX_USER_SCALE, value))

  const zoomIn = () => {
    userScale.value = clampUserScale(userScale.value * ZOOM_STEP)
  }

  const zoomOut = () => {
    userScale.value = clampUserScale(userScale.value / ZOOM_STEP)
  }

  const resetView = () => {
    rotation.value = 0
    markManualRotation()
    nextTick(() => fitToViewport())
  }

  const rotateLeft = () => {
    rotation.value = (rotation.value - 90 + 360) % 360
    markManualRotation()
    nextTick(() => fitToViewport({ preserveRotation: true }))
  }

  const goToIndex = (index, options = {}) => {
    const { preserveRotation = false, force = false } = options
    const next = Math.min(
      Math.max(index, 0),
      Math.max(images.value.length - 1, 0),
    )
    if (!force && next === currentIndex.value && currentSrc.value === images.value[next]) {
      return
    }
    const srcChanged = currentSrc.value !== images.value[next]
    currentIndex.value = next
    if (!preserveRotation) {
      rotation.value = 0
      resetPanZoom()
    }
    nextTick(() => {
      fitToViewport({ preserveRotation })
      if (srcChanged && autoOrientEnabled.value) {
        runAutoOrient()
      }
    })
  }

  const goPrev = () => {
    if (currentIndex.value > 0) {
      goToIndex(currentIndex.value - 1)
    }
  }

  const goNext = () => {
    if (currentIndex.value < images.value.length - 1) {
      goToIndex(currentIndex.value + 1)
    }
  }

  const onWheel = (event) => {
    const delta = event.deltaY > 0 ? 1 / ZOOM_STEP : ZOOM_STEP
    userScale.value = clampUserScale(userScale.value * delta)
  }

  const tryViewportNavigate = (event) => {
    if (!event || images.value.length <= 1) return
    const viewport = viewportRef.value
    if (!viewport) return
    const target = event.target
    if (target instanceof Element && target.closest('.ipv-float-nav, .ipv-nav-side-btn, .ipv-thumb, .ant-btn')) {
      return
    }
    const rect = viewport.getBoundingClientRect()
    if (!rect.width) return
    const ratio = (event.clientX - rect.left) / rect.width
    if (ratio < 0.33) {
      goPrev()
    } else if (ratio > 0.67) {
      goNext()
    }
  }

  const onPointerDown = (event) => {
    if (event.button !== 0) return
    const target = event.target
    if (target instanceof Element && target.closest('.ipv-float-nav, .ipv-nav-side-btn, .ant-btn')) {
      return
    }
    pointerDownAt.value = { x: event.clientX, y: event.clientY }
    dragging.value = true
    dragStart.value = {
      x: event.clientX,
      y: event.clientY,
      ox: offsetX.value,
      oy: offsetY.value,
    }
  }

  const onPointerMove = (event) => {
    if (!dragging.value) return
    offsetX.value = dragStart.value.ox + (event.clientX - dragStart.value.x)
    offsetY.value = dragStart.value.oy + (event.clientY - dragStart.value.y)
  }

  const onPointerUp = (event) => {
    const start = pointerDownAt.value
    if (start && event?.clientX != null) {
      const dx = Math.abs(event.clientX - start.x)
      const dy = Math.abs(event.clientY - start.y)
      if (dx < 6 && dy < 6) {
        tryViewportNavigate(event)
      }
    }
    dragging.value = false
    pointerDownAt.value = null
  }

  const onDoubleClick = () => {
    if (userScale.value > 1.05) {
      userScale.value = 1
      offsetX.value = 0
      offsetY.value = 0
    } else {
      userScale.value = clampUserScale(2)
    }
  }

  const syncIndex = () => {
    const target = controlledIndex.value != null ? controlledIndex.value : initialIndex.value
    if (currentIndex.value !== target) {
      goToIndex(target, { force: true })
    }
  }

  const ensureImageVisible = () => {
    nextTick(() => {
      const img = imgRef.value
      const src = currentSrc.value
      if (!img || !src) return
      if (img.src !== src) {
        img.src = src
      }
      if (img.complete && img.naturalWidth > 0) {
        onImageLoad()
      }
    })
  }

  const handleKeydown = (event) => {
    if (!active.value) return
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      goPrev()
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      goNext()
    }
  }

  const onWindowResize = () => {
    if (active.value) {
      fitToViewport({ preserveRotation: true })
    }
  }

  watch(
    () => active.value,
    (value) => {
      if (value) {
        syncIndex()
        ensureImageVisible()
        document.addEventListener('keydown', handleKeydown)
      } else {
        document.removeEventListener('keydown', handleKeydown)
      }
    },
    { immediate: true },
  )

  watch(
    () => [active.value, currentSrc.value],
    ([isActive]) => {
      if (isActive) {
        ensureImageVisible()
      }
    },
  )

  watch(
    () => initialIndex.value,
    () => {
      if (active.value && controlledIndex.value == null) {
        syncIndex()
      }
    },
  )

  watch(
    controlledIndex,
    (value) => {
      if (value == null || !active.value) return
      if (value !== currentIndex.value) {
        goToIndex(value, { force: true })
      }
    },
  )

  watch(
    images,
    (newImages, oldImages) => {
      if (!active.value) return
      if (newImages === oldImages && currentSrc.value) return
      const target = controlledIndex.value != null ? controlledIndex.value : initialIndex.value
      const safeTarget = Math.min(
        Math.max(target, 0),
        Math.max(newImages.length - 1, 0),
      )
      currentIndex.value = safeTarget
      rotation.value = 0
      resetPanZoom()
      orienting.value = false
      nextTick(() => {
        fitToViewport()
        ensureImageVisible()
      })
    },
    { flush: 'sync' },
  )

  watch(
    () => autoOrientEnabled.value,
    (enabled) => {
      if (enabled && active.value) {
        runAutoOrient()
      }
    },
  )

  onMounted(() => {
    window.addEventListener('resize', onWindowResize)
    if (autoOrientEnabled.value) {
      warmupOcrWorker()
    }
  })

  onUnmounted(() => {
    document.removeEventListener('keydown', handleKeydown)
    window.removeEventListener('resize', onWindowResize)
  })

  const rerunAutoOrient = () => {
    const key = `${currentIndex.value}:${currentSrc.value}`
    const next = new Set(manualRotationKeys.value)
    next.delete(key)
    manualRotationKeys.value = next
    rotation.value = 0
    invalidateOrientationCache(currentSrc.value)
    runAutoOrient()
  }

  return {
    viewportRef,
    imgRef,
    currentIndex,
    currentSrc,
    scalePercent,
    imageStyle,
    orienting,
    viewportMinHeight,
    onImageLoad,
    onImageError,
    zoomIn,
    zoomOut,
    resetView,
    rotateLeft,
    goPrev,
    goNext,
    goToIndex,
    onWheel,
    onPointerDown,
    onPointerMove,
    onPointerUp,
    onDoubleClick,
    fitToViewport,
    rerunAutoOrient,
    images,
  }
}
