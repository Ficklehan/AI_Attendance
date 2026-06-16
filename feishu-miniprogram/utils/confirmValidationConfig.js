const { apiCall } = require('./request')
const { isApiSuccess, getApiData } = require('./response')
const {
  setConfirmValidationConfig,
  getConfirmValidationConfig,
} = require('./requiredRecordFields')

let loadPromise = null

function loadConfirmValidationConfig(force) {
  if (loadPromise && !force) {
    return loadPromise
  }
  loadPromise = apiCall({ url: '/config/confirm-validation', method: 'GET' })
    .then((res) => {
      if (isApiSuccess(res.data)) {
        const cfg = getApiData(res.data)
        if (cfg) {
          setConfirmValidationConfig(cfg)
        }
      }
      return getConfirmValidationConfig()
    })
    .catch((err) => {
      console.warn('load confirm validation config failed', err)
      return getConfirmValidationConfig()
    })
  return loadPromise
}

module.exports = {
  loadConfirmValidationConfig,
  getConfirmValidationConfig,
}
