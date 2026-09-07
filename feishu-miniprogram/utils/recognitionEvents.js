const { apiCall } = require('./request')
const { isApiSuccess, getApiData } = require('./response')
const {
  applyRecognitionEvents,
  emptyRecognitionState,
  isRecognitionTerminal,
  toLiveRowPreview,
  summarizeLiveRows,
  liveRecognitionNote,
} = require('../shared-js/recognitionEvents')

function unwrapPage(res) {
  if (isApiSuccess(res.data)) {
    return getApiData(res.data) || {}
  }
  return {}
}

function fetchRecognitionEvents(taskId, afterSeq) {
  return apiCall({
    url: `/tasks/${taskId}/events`,
    data: { afterSeq: afterSeq || 0, limit: 100 },
    timeout: 15000
  }).then(unwrapPage)
}

function waitRecognitionEvents(taskId, afterSeq) {
  return apiCall({
    url: `/tasks/${taskId}/events/wait`,
    data: { afterSeq: afterSeq || 0, timeoutMs: 25000 },
    timeout: 35000
  }).then(unwrapPage)
}

module.exports = {
  applyRecognitionEvents,
  emptyRecognitionState,
  isRecognitionTerminal,
  toLiveRowPreview,
  summarizeLiveRows,
  liveRecognitionNote,
  fetchRecognitionEvents,
  waitRecognitionEvents,
}
