import request from './index'

export const getRolePermissions = () => {
  return request({
    url: '/permissions/roles',
    method: 'get',
  })
}

export const updateRolePermissions = (data) => {
  return request({
    url: '/permissions/roles',
    method: 'post',
    data,
  })
}

export const getMyPermissions = () => {
  return request({
    url: '/permissions/me',
    method: 'get',
  })
}
