import { API_BASE_PATH } from '@/constants/apiBase'
import { getToken } from '@/utils/auth'

export function parseFeishuBitableUrl(url) {
  if (!url) {
    return { appToken: null, tableId: null, error: 'URL不能为空' }
  }

  try {
    // 飞书多维表链接格式:
    // https://xxx.feishu.cn/base/xxx
    // https://xxx.feishu.cn/base/xxx?table=xxx
    // https://xxx.feishu.cn/base/xxx?table=xxx&field=xxx

    const urlObj = new URL(url)

    // 检查是否是飞书域名
    const hostname = urlObj.hostname
    if (!hostname.includes('feishu.cn') && !hostname.includes('larksuite.com')) {
      return { appToken: null, tableId: null, error: '不是飞书链接' }
    }

    // 检查路径是否包含 /base/
    const pathParts = urlObj.pathname.split('/').filter(p => p)
    const baseIndex = pathParts.findIndex(p => p === 'base')

    if (baseIndex === -1 || pathParts.length <= baseIndex + 1) {
      return { appToken: null, tableId: null, error: '链接格式不正确，缺少App Token' }
    }

    // 提取 App Token
    const appToken = pathParts[baseIndex + 1]

    // 检查 App Token 格式（飞书App Token通常是13位字母数字组合）
    if (!appToken || appToken.length < 10) {
      return { appToken: null, tableId: null, error: 'App Token格式不正确' }
    }

    // 从URL参数中提取 Table ID
    const tableId = urlObj.searchParams.get('table')

    if (!tableId) {
      return {
        appToken,
        tableId: null,
        error: '未找到Table ID，请在链接中添加 ?table=xxx 参数'
      }
    }

    // 验证 Table ID 格式（飞书Table ID通常是 tbl 开头）
    if (!tableId.startsWith('tbl')) {
      return {
        appToken,
        tableId: null,
        error: 'Table ID格式不正确，应以tbl开头'
      }
    }

    return { appToken, tableId, error: null }
  } catch (err) {
    return { appToken: null, tableId: null, error: `链接解析失败: ${err.message}` }
  }
}

export function generateFeishuBitableUrl(appToken, tableId) {
  if (!appToken) {
    return null
  }

  const baseUrl = 'https://feishu.cn/base/'
  let url = `${baseUrl}${appToken}`

  if (tableId) {
    url += `?table=${tableId}`
  }

  return url
}

export async function validateFeishuBitableConnection(appToken, tableId) {
  try {
    const token = getToken()
    if (!token) {
      return { valid: false, error: '未登录，请先登录' }
    }

    const response = await fetch(`${API_BASE_PATH}/bitable/validate?appToken=${appToken}&tableId=${tableId}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
    })

    if (!response.ok) {
      const errorText = await response.text()
      return { valid: false, error: `验证失败: ${errorText}` }
    }

    const result = await response.json()
    return { valid: result.success, error: result.message || null }
  } catch (err) {
    return { valid: false, error: `验证请求失败: ${err.message}` }
  }
}
