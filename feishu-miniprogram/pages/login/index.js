const App = getApp()
const { isApiSuccess, getApiData, getApiMessage } = require('../../utils/response')
const { t } = require('../../utils/i18n')
const { isCountryConfigured, syncCountryConfig } = require('../../utils/preferences')

Page({
  data: {
    loading: false,
    texts: {}
  },

  onLoad: function () {
    this.refreshTexts()
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

  handleFeishuLogin: function () {
    console.log('点击飞书登录')
    this.setData({ loading: true })

    tt.login({
      success: (res) => {
        console.log('tt.login 成功:', res)
        if (res.code) {
          this.getLoginToken(res.code)
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
  },

  getLoginToken: function (authCode) {
    tt.request({
      url: App.globalData.baseUrl + '/feishu-auth/miniprogram/login',
      method: 'POST',
      header: { 'Content-Type': 'application/json' },
      data: { code: authCode },
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
        console.error('登录请求失败:', err)
        this.setData({ loading: false })
        tt.showToast({ title: t('login.networkFail'), icon: 'none' })
      }
    })
  },

  afterLogin: function () {
    syncCountryConfig().then(() => {
      if (!isCountryConfigured()) {
        tt.redirectTo({ url: '/pages/settings/index?setup=1' })
        return
      }
      tt.switchTab({ url: '/pages/index/index' })
    }).catch(() => {
      tt.redirectTo({ url: '/pages/settings/index?setup=1' })
    })
  }
})
