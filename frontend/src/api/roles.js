import request from './index'

export function listRoles() {
  return request.get('/roles')
}

export function createRole(data) {
  return request.post('/roles', data)
}

export function updateRole(roleKey, data) {
  return request.put(`/roles/${roleKey}`, data)
}

export function deleteRole(roleKey) {
  return request.delete(`/roles/${roleKey}`)
}
