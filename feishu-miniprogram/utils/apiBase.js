const { resolveBaseUrl } = require('../config')
const { apiCall } = require('./request')

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
  return apiCall({ url, method: 'GET', timeout: 8000 })
    .then((res) => ({
      ok: res.statusCode >= 200 && res.statusCode < 300,
      statusCode: res.statusCode,
      url
    }))
    .catch((err) => ({
      ok: false,
      err,
      url
    }))
}

function describeNetworkFailure(baseUrl, probeResult) {
  const lines = []
  if (!baseUrl || !String(baseUrl).trim()) {
    lines.push('未配置公网 API 地址')
    lines.push('见 docs/运维手册.md §5.2（域名与环境切换）')
    return lines.join('\n')
  }
  lines.push(`无法连接后端：${baseUrl}`)
  if (!probeResult || !probeResult.ok) {
    if (baseUrl.indexOf('localhost') !== -1 || baseUrl.indexOf('127.0.0.1') !== -1) {
      lines.push('手机飞书不能使用 localhost，请按运维手册 §5.2.4 切公网')
    } else {
      lines.push('请按 docs/运维手册.md §5.2 核对：合法域名、Nginx、./start.sh apply')
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
