/**
 * API 地址配置（详见 docs/feishu-miniprogram.md）
 * - 本机调试：http://localhost:3000/api
 * - 真机/飞书客户端：使用内网穿透公网地址（下方 PUBLIC_BASE_URL）
 */
const LOCAL_BASE_URL = 'http://localhost:3000/api'

/** 内网穿透公网根地址（localtunnel 每次重启会变，需同步改飞书「服务器域名」） */
const PUBLIC_BASE_URL = 'https://cold-ends-stare.loca.lt/api'

/**
 * true = 真机/飞书客户端（必须配置合法域名）
 * false = 仅开发者工具连本机后端
 */
const USE_PUBLIC_API = false

module.exports = {
  baseUrl: USE_PUBLIC_API ? PUBLIC_BASE_URL : LOCAL_BASE_URL,
  USE_PUBLIC_API,
  PUBLIC_HOST: 'cold-ends-stare.loca.lt'
}
