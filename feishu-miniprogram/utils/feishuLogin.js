const { isApiSuccess, getApiData, getApiMessage } = require('./response')
const { t } = require('./i18n')
const { syncCountryConfig } = require('./preferences')
const { refreshApiBase, probeBackend } = require('./apiBase')

function getAppSafe() {
  try {
    return getApp()
  } catch (e) {
    return null
  }
}

function hydrateTokenFromStorage(app) {
  if (!app || !app.globalData) {
    return ''
  }
  if (app.globalData.token) {
    return app.globalData.token
  }
  try {
    const stored = tt.getStorageSync('token')
    if (stored) {
      app.globalData.token = stored
      const userInfo = tt.getStorageSync('userInfo')
      if (userInfo) {
        app.globalData.userInfo = userInfo
      }
      return stored
    }
  } catch (e) {
    // ignore
  }
  return ''
}

function clearSession(app) {
  if (app && app.globalData) {
    app.globalData.token = ''
    app.globalData.userInfo = null
  }
  try {
    tt.removeStorageSync('token')
    tt.removeStorageSync('userInfo')
  } catch (e) {
    // ignore
  }
}

function verifyToken(app) {
  const token = app.globalData.token
  const apiBase = app.globalData.baseUrl
  if (!token || !apiBase) {
    return Promise.reject(new Error('missing token'))
  }
  return new Promise((resolve, reject) => {
    tt.request({
      url: `${apiBase}/auth/profile`,
      method: 'GET',
      header: { Authorization: `Bearer ${token}` },
      timeout: 10000,
      success: (res) => {
        if (isApiSuccess(res.data)) {
          const user = getApiData(res.data)
          if (user) {
            app.globalData.userInfo = user
            tt.setStorageSync('userInfo', user)
          }
          resolve(token)
          return
        }
        reject(new Error('invalid token'))
      },
      fail: (err) => reject(err)
    })
  })
}

function performFeishuLogin(app, silent) {
  return new Promise((resolve, reject) => {
    const apiBase = app.globalData.baseUrl
    if (!apiBase) {
      reject(new Error('missing api base'))
      return
    }
    tt.login({
      success: (res) => {
        if (!res.code) {
          reject(new Error('missing auth code'))
          return
        }
        tt.request({
          url: `${apiBase}/feishu-auth/miniprogram/login`,
          method: 'POST',
          header: { 'Content-Type': 'application/json' },
          data: { code: res.code },
          timeout: 15000,
          success: (loginRes) => {
            const body = loginRes.data
            if (!isApiSuccess(body)) {
              reject(new Error(getApiMessage(body, t('login.loginFail'))))
              return
            }
            const payload = getApiData(body)
            app.globalData.token = payload.token
            app.globalData.userInfo = payload.userInfo
            tt.setStorageSync('token', payload.token)
            tt.setStorageSync('userInfo', payload.userInfo)
            syncCountryConfig(app).catch(() => {})
            if (!silent) {
              tt.showToast({ title: t('login.loginSuccess'), icon: 'success' })
            }
            resolve(payload.token)
          },
          fail: (err) => reject(err)
        })
      },
      fail: (err) => reject(err)
    })
  })
}

/**
 * 使用当前飞书账号登录；已有有效 token 则复用，过期则自动刷新。
 */
function ensureFeishuLogin(options) {
  const silent = options && options.silent
  const force = options && options.force
  const app = getAppSafe()
  if (!app) {
    return Promise.reject(new Error('app unavailable'))
  }

  const baseUrl = refreshApiBase()
  if (!baseUrl) {
    return Promise.reject(new Error('missing api base'))
  }

  return probeBackend(baseUrl).then((probe) => {
    if (!probe.ok) {
      return Promise.reject(new Error('backend unreachable'))
    }
    hydrateTokenFromStorage(app)
    if (!force && app.globalData.token) {
      return verifyToken(app).catch(() => {
        clearSession(app)
        return performFeishuLogin(app, silent)
      })
    }
    clearSession(app)
    return performFeishuLogin(app, silent)
  })
}

module.exports = {
  ensureFeishuLogin,
  hydrateTokenFromStorage,
  clearSession,
  performFeishuLogin,
  verifyToken
}
