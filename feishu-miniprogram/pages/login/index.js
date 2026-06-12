const App = getApp()
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { t } = require('../../utils/i18n')
const { isCountryConfigured, syncCountryConfig } = require('../../utils/preferences')
const { refreshApiBase, probeBackend, describeNetworkFailure } = require('../../utils/apiBase')
const { ensureFeishuLogin } = require('../../utils/feishuLogin')
const { consumePendingReturn, buildResultPath, setPendingReturn } = require('../../utils/deepLink')

Page({
  data: {
    loading: false,
    texts: {}
  },

  onLoad: function (options) {
    this.refreshTexts()
    const taskId = options && options.taskId
    if (taskId) {
      setPendingReturn(buildResultPath(taskId))
    }
    const auto = options && (options.auto === '1' || options.auto === 'true')
    if (auto || taskId) {
      this.tryAutoLogin()
    }
  },

  refreshTexts: function () {
    this.setData({
      texts: {
        title: t('login.title'),
        subtitle: t('login.subtitle'),
        feishuLogin: t('login.feishuLogin'),
        feishuLoginAction: t('login.feishuLoginAction'),
        loggingIn: t('login.loggingIn'),
        tip1: t('login.tip1'),
        tip2: t('login.tip2'),
        tip3: t('login.tip3')
      }
    })
  },

  tryAutoLogin: function () {
    if (this._autoLoginStarted) {
      return
    }
    this._autoLoginStarted = true
    this.setData({ loading: true })
    ensureFeishuLogin({ silent: true, force: true })
      .then(() => {
        this.setData({ loading: false })
        this.afterLogin()
      })
      .catch(() => {
        this.setData({ loading: false })
        this._autoLoginStarted = false
      })
  },

  handleFeishuLogin: function () {
    console.log('点击飞书登录')
    this.setData({ loading: true })

    const baseUrl = refreshApiBase()
    console.log('当前 API 地址:', baseUrl)

    probeBackend(baseUrl).then((probe) => {
      if (!probe.ok) {
        console.error('后端不可达:', probe)
        this.setData({ loading: false })
        const detail = describeNetworkFailure(baseUrl, probe)
        tt.showModal({
          title: t('login.networkFail'),
          content: detail,
          showCancel: false
        })
        return
      }

      tt.login({
        success: (res) => {
          console.log('tt.login 成功:', res)
          if (res.code) {
            this.getLoginToken(res.code, baseUrl)
          } else {
            this.setData({ loading: false })
            tt.showToast({ title: t('login.authCodeFail'), icon: 'none' })
          }
        },
        fail: (err) => {
          console.error('tt.login 失败:', err)
          this.setData({ loading: false })
          tt.showToast({ title: t('login.authCodeFail'), icon: 'none' })
        }
      })
    })
  },

  getLoginToken: function (authCode, baseUrl) {
    const apiBase = baseUrl || refreshApiBase()
    const loginUrl = apiBase + '/feishu-auth/miniprogram/login'
    console.log('请求登录接口:', loginUrl)

    tt.request({
      url: loginUrl,
      method: 'POST',
      header: { 'Content-Type': 'application/json' },
      data: { code: authCode },
      timeout: 15000,
      success: (res) => {
        const body = res.data
        console.log('登录接口返回:', res.statusCode, '业务code:', body && body.code, 'message:', body && body.message)
        if (isApiSuccess(body)) {
          const payload = getApiData(body)
          App.globalData.token = payload.token
          App.globalData.userInfo = payload.userInfo

          tt.setStorageSync('token', payload.token)
          tt.setStorageSync('userInfo', payload.userInfo)

          tt.showToast({ title: t('login.loginSuccess'), icon: 'success' })
          setTimeout(() => { this.afterLogin() }, 1500)
        } else {
          this.setData({ loading: false })
          tt.showToast({ title: getApiMessage(body, t('login.loginFail')), icon: 'none' })
        }
      },
      fail: (err) => {
        console.error('登录请求失败:', err, 'URL:', loginUrl)
        this.setData({ loading: false })
        const hint = describeNetworkFailure(apiBase, { ok: false, err })
        tt.showModal({
          title: t('login.networkFail'),
          content: hint,
          showCancel: false
        })
      }
    })
  },

  afterLogin: function () {
    const pendingReturn = consumePendingReturn()
    syncCountryConfig().then(() => {
      if (pendingReturn) {
        tt.redirectTo({ url: pendingReturn })
        return
      }
      if (!isCountryConfigured()) {
        tt.redirectTo({ url: '/pages/settings/index?setup=1' })
        return
      }
      tt.switchTab({ url: '/pages/index/index' })
    }).catch(() => {
      if (pendingReturn) {
        tt.redirectTo({ url: pendingReturn })
        return
      }
      tt.redirectTo({ url: '/pages/settings/index?setup=1' })
    })
  }
})
