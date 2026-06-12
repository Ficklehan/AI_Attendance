const PENDING_RETURN_KEY = 'pendingReturnPath'

function setPendingReturn(path) {
  if (!path || typeof path !== 'string') {
    return
  }
  try {
    tt.setStorageSync(PENDING_RETURN_KEY, path)
  } catch (e) {
    // ignore
  }
}

function consumePendingReturn() {
  try {
    const path = tt.getStorageSync(PENDING_RETURN_KEY)
    if (path) {
      tt.removeStorageSync(PENDING_RETURN_KEY)
      return String(path)
    }
  } catch (e) {
    // ignore
  }
  return ''
}

function buildResultPath(taskId) {
  if (!taskId) {
    return ''
  }
  return `/pages/result/index?id=${encodeURIComponent(taskId)}`
}

module.exports = {
  PENDING_RETURN_KEY,
  setPendingReturn,
  consumePendingReturn,
  buildResultPath
}
