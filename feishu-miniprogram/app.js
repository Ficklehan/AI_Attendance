App({
  globalData: {
    token: '',
    userInfo: null,
    baseUrl: 'http://localhost:3000/api',
    currentCountry: 'CN',
    countries: [
      { code: 'CN', name: '中国' },
      { code: 'US', name: '美国' },
      { code: 'UK', name: '英国' },
      { code: 'DE', name: '德国' },
      { code: 'FR', name: '法国' }
    ]
  },

  onLaunch: function () {
    console.log('飞书小程序启动');
  }
})
