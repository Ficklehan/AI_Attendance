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
  if (!baseUrl || !String(baseUrl).trim()) {
    return Promise.resolve({ ok: false, url: '(empty)' })
  }
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
  const lines = []
  if (!baseUrl || !String(baseUrl).trim()) {
    lines.push('未配置公网 API 地址')
    lines.push('上传小程序前在本机执行:')
    lines.push('  node scripts/render-deploy-config.mjs --env production')
    lines.push('确认 feishu-miniprogram/config.prod.js 存在后再上传')
    lines.push('config.js 需 USE_PUBLIC_API=true')
    return lines.join('\n')
  }
  lines.push(`无法连接后端：${baseUrl}`)
  if (!probeResult || !probeResult.ok) {
    if (baseUrl.indexOf('localhost') !== -1 || baseUrl.indexOf('127.0.0.1') !== -1) {
      lines.push('手机飞书不能使用 localhost')
      lines.push('请生成 config.prod.js 并重新上传小程序')
    } else {
      lines.push('请检查：')
      lines.push('1) 飞书开放平台 → request 合法域名（仅主机名，如 uat-guanpei.eminxing.com）')
      lines.push('2) 服务器 Nginx 已转发 /attendance/api/')
      lines.push('3) 后端已 ./start.sh restart-prod')
    }
    lines.push('真机预览勿勾选「不校验合法域名」')
  }
  return lines.join('\n')
}

module.exports = {
  refreshApiBase,
  getApiBase,
  probeBackend,
  describeNetworkFailure
}
