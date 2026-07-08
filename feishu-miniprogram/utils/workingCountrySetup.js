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

/** 仅当账户工作国家为全局默认（未配置个人国家）时需要首次设置 */
function needsWorkingCountrySetup(userInfo) {
  return core.needsWorkingCountrySetup(userInfo || getUserInfoSafe())
}

module.exports = {
  ...core,
  getUserInfoSafe,
  needsWorkingCountrySetup,
}
