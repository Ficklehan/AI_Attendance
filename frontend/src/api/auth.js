import request from './index'

export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'post',
    data,
  })
}

export const register = (data) => {
  return request({
    url: '/auth/register',
    method: 'post',
    data,
  })
}

export const getUserInfo = () => {
  return request({
    url: '/auth/profile',
    method: 'get',
  })
}

export const changePassword = (data) => {
  return request({
    url: '/auth/change-password',
    method: 'post',
    data,
  })
}

export const logout = () => {
  return request({
    url: '/auth/logout',
    method: 'post',
  })
}

export const verifyToken = () => {
  return request({
    url: '/auth/verify',
    method: 'get',
  })
}
