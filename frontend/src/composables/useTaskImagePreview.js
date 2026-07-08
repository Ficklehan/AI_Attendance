import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { resolveTaskImageUrls } from '@/utils/imageUrl'
import { useImageComparePreview } from '@/composables/useImageComparePreview'

/**
 * Shared task image preview flow (TaskList / TaskRecords / TaskEdit dock + fullscreen).
 */
export function useTaskImagePreview() {
  const { t } = useI18n()

  const previewImagesList = ref([])
  const previewCurrentIndex = ref(0)
  const previewFetching = ref(false)
  let previewLoadToken = 0

  const {
    dockOpen: previewDockOpen,
    fullscreenOpen: previewFullscreenOpen,
    openPreview: openPreviewPanel,
    openFullscreen: openPreviewFullscreen,
    closePreview,
  } = useImageComparePreview()

  const previewTaskImages = async (record, options = {}) => {
    const { index = 0 } = options
    const token = ++previewLoadToken
    const dockWasOpen = previewDockOpen.value
    if (dockWasOpen) {
      previewFetching.value = true
    }
    try {
      const urls = await resolveTaskImageUrls(record?.imageUrls, record?.fileKey)
      if (token !== previewLoadToken) return
      if (!urls.length) {
        message.warning(t('tasks.noImages'))
        return
      }
      previewCurrentIndex.value = Math.min(Math.max(index, 0), urls.length - 1)
      previewImagesList.value = urls
      if (!dockWasOpen) {
        openPreviewPanel()
      }
    } catch (error) {
      if (token === previewLoadToken) {
        message.error(error?.message || t('common.error'))
      }
    } finally {
      if (token === previewLoadToken) {
        previewFetching.value = false
      }
    }
  }

  const loadTaskImageUrls = async (record) => {
    const urls = await resolveTaskImageUrls(record?.imageUrls, record?.fileKey)
    previewImagesList.value = urls
    return urls
  }

  const openImagePreviewAt = (index = 0) => {
    if (!previewImagesList.value.length) return
    previewCurrentIndex.value = Math.min(
      Math.max(index, 0),
      previewImagesList.value.length - 1,
    )
    openPreviewPanel()
  }

  return {
    previewImagesList,
    previewCurrentIndex,
    previewFetching,
    previewDockOpen,
    previewFullscreenOpen,
    openPreviewPanel,
    openPreviewFullscreen,
    closePreview,
    previewTaskImages,
    loadTaskImageUrls,
    openImagePreviewAt,
  }
}
