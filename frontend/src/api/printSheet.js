import request from './index'

export function createPrintSheetJob(data, config = {}) {
  return request.post('/print-sheets/jobs', data, config)
}

export function listPrintSheetDays(params) {
  return request.get('/print-sheets/days', { params })
}

export function getPrintSheetDayDetail(params) {
  return request.get('/print-sheets/days/detail', { params })
}
