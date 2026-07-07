import request from './index'

export function getAgencyBillingSummary(params) {
  return request({
    url: '/agency-billing/summary',
    method: 'get',
    params,
  })
}

export function getAgencyBillingDetail(params) {
  return request({
    url: '/agency-billing/detail',
    method: 'get',
    params,
  })
}
