import request from './index'

export const getServiceStatus = () => {
  return request({
    url: '/service/status',
    method: 'get',
  })
}

export const startBackend = () => {
  return request({
    url: '/service/backend/start',
    method: 'post',
  })
}

export const stopBackend = () => {
  return request({
    url: '/service/backend/stop',
    method: 'post',
  })
}

export const startFrontend = () => {
  return request({
    url: '/service/frontend/start',
    method: 'post',
  })
}

export const stopFrontend = () => {
  return request({
    url: '/service/frontend/stop',
    method: 'post',
  })
}

export const startAll = () => {
  return request({
    url: '/service/start-all',
    method: 'post',
  })
}

export const stopAll = () => {
  return request({
    url: '/service/stop-all',
    method: 'post',
  })
}
