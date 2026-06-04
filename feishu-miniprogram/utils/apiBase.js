const { resolveBaseUrl } = require('../config')

function refreshApiBase() {
  const app = getApp()
  const baseUrl = resolveBaseUrl()
  if (app && app.globalData) {
    app.globalData.baseUrl = baseUrl
  }
  return baseUrl
}

function getApiBase() {
  const app = getApp()
  if (app && app.globalData && app.globalData.baseUrl) {
    return app.globalData.baseUrl
  }
  return resolveBaseUrl()
}

/**
 * 登录前探测后端是否可达（GET /config/current-country 为公开接口）
 */
function probeBackend(baseUrl) {
  const url = `${baseUrl}/config/current-country`
  return new Promise((resolve) => {
    tt.request({
      url,
      method: 'GET',
      timeout: 8000,
      success: (res) => {
        resolve({
          ok: res.statusCode >= 200 && res.statusCode < 300,
          statusCode: res.statusCode,
          url
        })
      },
      fail: (err) => {
        resolve({
          ok: false,
          err,
          url
        })
      }
    })
  })
}

function describeNetworkFailure(baseUrl, probeResult) {
  const lines = [`无法连接后端：${baseUrl}`]
  if (!probeResult || !probeResult.ok) {
    if (baseUrl.indexOf('localhost') !== -1 || baseUrl.indexOf('127.0.0.1') !== -1) {
      lines.push('手机飞书不能使用 localhost，请改用 HTTPS 内网穿透地址')
      lines.push('请先运行: node scripts/render-deploy-config.mjs --env production')
      lines.push('并确认 config.js 中 USE_PUBLIC_API=true')
    } else {
      lines.push('请确认后端已启动且穿透隧道有效')
    }
    lines.push('开发者工具请勾选：不校验合法域名')
  }
  return lines.join('\n')
}

module.exports = {
  refreshApiBase,
  getApiBase,
  probeBackend,
  describeNetworkFailure
}
