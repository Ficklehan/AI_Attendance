const { shouldPromptWorkingCountrySetup } = require('./preferences')
const { t } = require('./i18n')

/**
 * Country gate: require setup first for recognition flows.
 * @param {() => void} action
 */
function runWithCountryGate(action) {
  if (typeof action !== 'function') return
  if (!shouldPromptWorkingCountrySetup()) {
    action()
    return
  }
  tt.showModal({
    title: t('countryGate.title'),
    content: t('countryGate.message'),
    confirmText: t('countryGate.goSettings'),
    cancelText: t('common.cancel'),
    success: (res) => {
      if (res.confirm) {
        tt.navigateTo({ url: '/pages/settings/index?setup=1' })
      }
    }
  })
}

module.exports = { runWithCountryGate }
