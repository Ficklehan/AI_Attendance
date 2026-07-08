const { t } = require('./i18n')

const CODE_KEYS = {
  401: 'errors.loginRequired',
  404: 'errors.apiNotFound',
  405: 'errors.methodNotAllowed',
  500: 'errors.systemError',
  1001: 'errors.userNotFound',
  1002: 'errors.userAlreadyExists',
  1003: 'errors.passwordWrong',
  1004: 'errors.loginRequired',
  1006: 'errors.accessDenied',
  2001: 'errors.taskNotFound',
  2002: 'errors.taskStatusCannotConfirm',
  429: 'errors.recognitionConcurrentLimit',
  3001: 'errors.imageInvalid',
  4001: 'errors.aiNoParseableRecords',
}

const LEGACY_EXACT = {
  '请先登录': 'errors.loginRequired',
  '无权限访问': 'errors.accessDenied',
  '无权限访问该任务': 'errors.taskAccessDenied',
  '无权限访问该文件': 'errors.fileAccessDenied',
  '任务不存在': 'errors.taskNotFound',
  '只支持图片文件': 'errors.imagesOnly',
  '图片文件无效': 'errors.imageInvalid',
  '系统异常，请稍后重试': 'errors.systemError',
  '请求失败': 'errors.requestFailed',
  '网络错误': 'errors.networkError',
  '识别失败': 'upload.recognizeFail',
  '上传失败': 'upload.fail',
  '获取任务失败': 'upload.fetchTaskFail',
  '启动识别失败': 'upload.startRecognizeFail',
  '未返回任务 ID': 'upload.noTaskId',
  '响应解析失败': 'upload.parseResponseFail',
  '未登录': 'errors.loginRequired',
  '服务端启用了模拟识别': 'upload.simulatedServer',
  '当前为模拟识别': 'upload.simulatedRecognition',
}

function normalizePayload(payload) {
  if (!payload) return {}
  if (typeof payload === 'string') return { message: payload }
  return payload
}

function translateApiError(payload, fallback) {
  const { messageKey, messageArgs, message, code } = normalizePayload(payload)
  const fb = fallback || t('errors.requestFailed')

  if (messageKey) {
    const text = t(messageKey, messageArgs || {})
    if (text && text !== messageKey) return text
  }
  if (message && message.startsWith('errors.')) {
    const text = t(message, messageArgs || {})
    if (text && text !== message) return text
  }
  if (code != null && CODE_KEYS[code]) {
    return t(CODE_KEYS[code], messageArgs || {})
  }
  if (message && LEGACY_EXACT[message]) {
    return t(LEGACY_EXACT[message], messageArgs || {})
  }
  if (message && message.includes('文件不存在:')) {
    const fileKey = message.split(':').slice(1).join(':').trim()
    return t('errors.fileNotFoundWithKey', { fileKey })
  }
  if (message && message.includes('上传的图片无效或过小')) {
    const match = message.match(/（(\d+)\s*bytes）/)
    return t('errors.uploadImageTooSmall', { size: match ? match[1] : 0 })
  }
  if (message && message.includes('图片过小')) {
    const match = message.match(/（(\d+)\s*bytes）/)
    return t('upload.imageTooSmall', { size: match ? match[1] : 0 })
  }
  if (message && message.includes('压缩后图片仍过小')) return t('upload.compressTooSmall')
  if (message && message.includes('无法读取图片')) return t('upload.imageReadFail')
  if (message && message.includes('模型把表头')) return t('errors.aiHeaderEcho')
  if (message && message.includes('未加引号')) return t('errors.aiInvalidJson', { preview: '' })
  if (message && message.includes('未返回可解析')) return t('errors.aiNoParseableRecords', { preview: '' })
  if (message && (message.includes('疑似模型编造') || message.includes('模板姓名'))) return t('errors.aiFabricated')
  if (message && message.includes('臆测而非读图')) return t('errors.aiUnreadableTimes')
  if (message && (message.includes('结构异常') || message.includes('畸形行'))) {
    return t('errors.aiMalformedRecords', messageArgs || {})
  }
  if (message && message.includes('MIMO API Key')) return t('errors.mimoNotConfigured')
  if (message && message.includes('服务端启用了模拟识别')) return t('upload.simulatedServer')
  if (message && message.includes('模拟识别')) return t('upload.simulatedRecognition')
  if (message && message.includes('识别结果为空')) return t('upload.emptyResult')
  if (message && message.includes('识别超时')) return t('upload.recognizeTimeout')
  if (message && message.startsWith('HTTP ')) {
    const status = Number(message.slice(5))
    if (status === 404) return t('errors.apiNotFound')
    if (status === 403 || status === 401) return t('errors.accessDenied')
  }
  return message || fb
}

function translateErrorMessage(error, fallback) {
  if (!error) return translateApiError(null, fallback)
  if (error.messageKey || (error.message && error.code != null)) {
    return translateApiError(error, fallback)
  }
  return translateApiError({ message: error.message, code: error.code }, fallback)
}

module.exports = { translateApiError, translateErrorMessage }
