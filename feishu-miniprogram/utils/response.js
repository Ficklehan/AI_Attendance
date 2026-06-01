/** 与后端 Result { code, message, messageKey, messageArgs, data } 对齐 */
const { translateApiError } = require('./translateError')
const { t } = require('./i18n')

function isApiSuccess(data) {
  if (!data) return false
  if (data.success === true) return true
  if (data.success === false) return false
  const code = Number(data.code)
  return code === 200 || code === 0
}

function getApiData(data) {
  return data && data.data !== undefined ? data.data : null
}

function getApiMessage(data, fallback) {
  const fb = fallback || t('errors.requestFailed')
  return translateApiError(data, fb)
}

module.exports = {
  isApiSuccess,
  getApiData,
  getApiMessage
}
