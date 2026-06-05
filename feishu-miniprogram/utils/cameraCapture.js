/**
 * 相机拍照平台差异与临时路径解析。
 * iOS 上 camera 非同层渲染时，组件上方的普通 view 会导致 takePhoto 必败。
 */

function getSystemInfoSafe() {
  try {
    return tt.getSystemInfoSync() || {}
  } catch (e) {
    return {}
  }
}

function isIosPlatform() {
  const platform = String(getSystemInfoSafe().platform || '').toLowerCase()
  return platform === 'ios' || platform === 'iphone'
}

function extractTempImagePath(res) {
  if (!res) return ''
  const direct = res.tempImagePath || res.tempFilePath || res.imagePath || res.filePath || res.path
  if (direct) return String(direct)
  if (res.data && typeof res.data === 'object') {
    return extractTempImagePath(res.data)
  }
  return ''
}

function formatCameraError(error) {
  if (!error) return ''
  return [
    error.errMsg,
    error.errString,
    error.errNo != null ? String(error.errNo) : '',
  ].filter(Boolean).join(' ')
}

function shouldRecreateContext(isIos) {
  return !!isIos
}

module.exports = {
  getSystemInfoSafe,
  isIosPlatform,
  extractTempImagePath,
  formatCameraError,
  shouldRecreateContext,
}
