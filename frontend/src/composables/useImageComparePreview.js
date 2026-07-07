import { onBeforeUnmount, onMounted, ref } from 'vue'

const DOCK_MIN_WIDTH = 900

export function useImageComparePreview() {
  const dockOpen = ref(false)
  const fullscreenOpen = ref(false)
  const isWideEnough = ref(true)

  const syncViewport = () => {
    isWideEnough.value = window.innerWidth >= DOCK_MIN_WIDTH
    if (!isWideEnough.value && dockOpen.value) {
      fullscreenOpen.value = true
      dockOpen.value = false
    }
  }

  const openPreview = () => {
    syncViewport()
    if (isWideEnough.value) {
      dockOpen.value = true
      fullscreenOpen.value = false
      return
    }
    fullscreenOpen.value = true
    dockOpen.value = false
  }

  const closePreview = () => {
    dockOpen.value = false
    fullscreenOpen.value = false
  }

  const openFullscreen = () => {
    fullscreenOpen.value = true
    dockOpen.value = false
  }

  onMounted(() => {
    syncViewport()
    window.addEventListener('resize', syncViewport)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', syncViewport)
  })

  return {
    dockOpen,
    fullscreenOpen,
    isWideEnough,
    openPreview,
    closePreview,
    openFullscreen,
  }
}
