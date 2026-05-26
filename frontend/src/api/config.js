import request from './index'

export const getAllConfigs = () => {
  return request({
    url: '/config',
    method: 'get',
  })
}

export const updateConfig = (data) => {
  return request({
    url: '/config',
    method: 'put',
    data,
  })
}

export const deleteConfig = (configKey) => {
  return request({
    url: `/config/${configKey}`,
    method: 'delete',
  })
}
