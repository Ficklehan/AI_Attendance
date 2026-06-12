import request from './index'

export function listReminderRules() {
  return request({ url: '/reminder-rules', method: 'get' })
}

export function getReminderRule(id) {
  return request({ url: `/reminder-rules/${id}`, method: 'get' })
}

export function createReminderRule(data) {
  return request({ url: '/reminder-rules', method: 'post', data })
}

export function updateReminderRule(id, data) {
  return request({ url: `/reminder-rules/${id}`, method: 'put', data })
}

export function setReminderRuleEnabled(id, enabled) {
  return request({ url: `/reminder-rules/${id}/enabled`, method: 'patch', data: { enabled } })
}

export function deleteReminderRule(id) {
  return request({ url: `/reminder-rules/${id}`, method: 'delete' })
}

export function getDefaultReminderTemplate() {
  return request({ url: '/reminder-rules/default-template', method: 'get' })
}
