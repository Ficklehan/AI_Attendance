const App = getApp()

Page({
  data: {
    loading: false
  },

  onLoad: function (options) {
    console.log('登录页面加载');
  },

  handleFeishuLogin: function () {
    console.log('点击飞书登录');
    this.setData({ loading: true });
    
    dd.getAuthCode({
      success: (res) => {
        console.log('dd.getAuthCode 成功:', res);
        if (res.authCode) {
          this.getLoginToken(res.authCode);
        } else {
          this.setData({ loading: false });
          dd.showToast({ title: '获取授权码失败', icon: 'none' });
        }
      },
      fail: (err) => {
        console.error('dd.getAuthCode 失败:', err);
        this.setData({ loading: false });
        dd.showToast({ title: '获取授权码失败', icon: 'none' });
      }
    });
  },

  getLoginToken: function (authCode) {
    dd.httpRequest({
      url: App.globalData.baseUrl + '/feishu-auth/miniprogram/login',
      method: 'POST',
      data: { code: authCode },
      success: (res) => {
        console.log('登录接口返回:', res);
        if (res.data && res.data.success) {
          App.globalData.token = res.data.data.token;
          App.globalData.userInfo = res.data.data.userInfo;
          
          dd.setStorageSync({ key: 'token', data: res.data.data.token });
          dd.setStorageSync({ key: 'userInfo', data: res.data.data.userInfo });

          dd.showToast({ title: '登录成功', icon: 'success' });
          setTimeout(() => { this.switchToHome(); }, 1500);
        } else {
          this.setData({ loading: false });
          dd.showToast({ title: res.data.message || '登录失败', icon: 'none' });
        }
      },
      fail: (err) => {
        console.error('登录请求失败:', err);
        this.setData({ loading: false });
        dd.showToast({ title: '网络请求失败', icon: 'none' });
      }
    });
  },

  switchToHome: function () {
    dd.switchTab({ url: '/pages/index/index' });
  }
})
