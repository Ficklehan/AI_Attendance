import request from './index'

export function listUsers(params) {
  return request.get('/users', { params })
}

export function getUser(userId) {
  return request.get(`/users/${userId}`)
}

export function createUser(data) {
  return request.post('/users', data)
}

export function updateUser(userId, data) {
  return request.put(`/users/${userId}`, data)
}

export function updateUserStatus(userId, status) {
  return request.patch(`/users/${userId}/status`, { status })
}

export function deleteUser(userId) {
  return request.delete(`/users/${userId}`)
}
