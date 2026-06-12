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

export const getSystemConfig = () => {
  return request({ url: '/config/system', method: 'get' })
}

export const updateSystemConfig = (data) => {
  return request({ url: '/config/system', method: 'put', data })
}

export const getConfirmValidationConfig = () => {
  return request({ url: '/config/confirm-validation', method: 'get' })
}
