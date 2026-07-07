import request from './index'

function withRegionCodes(params = {}) {
  const { regionCodes, regionCode, ...rest } = params
  const query = { ...rest }
  if (Array.isArray(regionCodes) && regionCodes.length > 0) {
    query.regionCodes = regionCodes.join(',')
  } else if (regionCode) {
    query.regionCode = regionCode
  }
  return query
}

export const getEmployeeList = (params) => {
  return request({
    url: '/employees',
    method: 'get',
    params: withRegionCodes(params),
  })
}

export const getWeeklyAttendance = (params) => {
  return request({
    url: '/employees/weekly',
    method: 'get',
    params: withRegionCodes(params),
  })
}

export const backfillEmployees = () => {
  return request({
    url: '/employees/backfill',
    method: 'post',
  })
}
