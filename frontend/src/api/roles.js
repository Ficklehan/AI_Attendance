import request from './index'

export function listRoles() {
  return request.get('/roles')
}

export function createRole(data) {
  return request.post('/roles', data)
}

export function updateRole(roleKey, data) {
  return request.post(`/roles/${roleKey}/update`, data)
}

export function deleteRole(roleKey) {
  return request.post(`/roles/${roleKey}/delete`)
}

export function getRoleMembers(roleKey, params) {
  return request.get(`/roles/${roleKey}/members`, { params })
}

export function getRoleMemberCandidates(roleKey, params) {
  return request.get(`/roles/${roleKey}/candidates`, { params })
}

export function addRoleMembers(roleKey, userIds) {
  return request.post(`/roles/${roleKey}/members`, { userIds })
}

export function removeRoleMember(roleKey, userId) {
  return request.post(`/roles/${roleKey}/members/${userId}/remove`)
}
