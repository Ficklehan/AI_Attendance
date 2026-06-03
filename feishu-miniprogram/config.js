/**
 * 后端 API 根路径（与 application.yml server.servlet.context-path 一致）
 * - 本机调试：http://localhost:8080/attendance/api
 * - 真机/飞书客户端：使用内网穿透公网地址（下方 PUBLIC_BASE_URL）
 */
const LOCAL_BASE_URL = 'http://localhost:8080/attendance/api'

/** 内网穿透示例：请改为你的公网域名 + /attendance/api */
const PUBLIC_BASE_URL = 'https://cold-ends-stare.loca.lt/attendance/api'

const USE_PUBLIC_API = false

module.exports = {
  baseUrl: USE_PUBLIC_API ? PUBLIC_BASE_URL : LOCAL_BASE_URL,
  USE_PUBLIC_API,
  PUBLIC_HOST: 'cold-ends-stare.loca.lt'
}
