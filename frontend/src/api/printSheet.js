import request from './index'

export function createPrintSheetJob(data) {
  return request.post('/print-sheets/jobs', data)
}

export function listPrintSheetDays(params) {
  return request.get('/print-sheets/days', { params })
}

export function getPrintSheetDayDetail(params) {
  return request.get('/print-sheets/days/detail', { params })
}
