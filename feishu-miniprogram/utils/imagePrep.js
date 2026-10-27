/**
 * 与 PC compressImage 对齐：保留去白边后填满 1300² 所需底像素，勿先砍到 1600。
 */
const { t } = require('./i18n')

const MAX_EDGE = 4096
const QUALITY = 90

function getFileInfo(filePath) {
  return new Promise((resolve, reject) => {
    tt.getFileInfo({
      filePath,
      success: (info) => resolve(info || {}),
      fail: (err) => reject(new Error((err && err.errMsg) || t('upload.imageReadFail')))
    })
  })
}

function compress(filePath) {
  return new Promise((resolve, reject) => {
    if (typeof tt.compressImage !== 'function') {
      resolve(filePath)
      return
    }
    tt.compressImage({
      src: filePath,
      quality: QUALITY,
      compressedWidth: MAX_EDGE,
      compressedHeight: MAX_EDGE,
      success: (res) => {
        resolve((res && res.tempFilePath) || filePath)
      },
      fail: () => resolve(filePath)
    })
  })
}

/**
 * @returns {Promise<{ path: string, size: number }>}
 */
function prepareImageForUpload(filePath) {
  return getFileInfo(filePath)
    .then((info) => {
      const size = info.size || 0
      if (size > 0 && size < 1024) {
        return Promise.reject(new Error(t('upload.imageTooSmall', { size })))
      }
      return compress(filePath)
    })
    .then((compressedPath) => getFileInfo(compressedPath).then((info) => ({
      path: compressedPath,
      size: info.size || 0
    })))
    .then(({ path, size }) => {
      if (size > 0 && size < 1024) {
        return Promise.reject(new Error(t('upload.compressTooSmall')))
      }
      return { path, size }
    })
}

module.exports = {
  prepareImageForUpload
}
