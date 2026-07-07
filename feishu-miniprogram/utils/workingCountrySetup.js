const core = require('../shared-js/workingCountrySetupCore')

function getUserInfoSafe() {
  try {
    const app = getApp()
    return (app && app.globalData.userInfo) || tt.getStorageSync('userInfo') || null
  } catch (e) {
    try {
      return tt.getStorageSync('userInfo') || null
    } catch (err) {
      return null
    }
  }
}

function needsWorkingCountrySetup(userInfo) {
  const info = userInfo || getUserInfoSafe()
  if (!core.needsWorkingCountrySetup(info)) {
    return false
  }
  try {
    const configured = tt.getStorageSync('countryConfigured')
    const country = tt.getStorageSync('currentCountry')
    if (configured && country && country !== 'default') {
      return false
    }
  } catch (e) {
    // ignore
  }
  return true
}

module.exports = {
  ...core,
  getUserInfoSafe,
  needsWorkingCountrySetup,
}
