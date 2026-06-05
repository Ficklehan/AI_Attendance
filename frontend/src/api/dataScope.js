import request from './index'

export function getRoleDataScopes() {
  return request.get('/data-scope/roles')
}

export function updateRoleDataScope(role, data) {
  return request.put(`/data-scope/roles/${role}`, data)
}

export function getDataScopeDimensionOptions() {
  return request.get('/data-scope/dimension-options')
}

export function getMyDataScope() {
  return request.get('/data-scope/me')
}
