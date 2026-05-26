import request from './index'

export const getAuditLogList = (params) => {
  return request({
    url: '/audit',
    method: 'get',
    params,
  })
}
