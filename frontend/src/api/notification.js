import request from './index'
import i18n from '@/locales'

export function listNotifications(params = {}) {
  const locale = i18n.global.locale.value
  return request({
    url: '/notifications',
    method: 'get',
    params: { ...params, locale },
  })
}

export function getUnreadCount() {
  return request({ url: '/notifications/unread-count', method: 'get' })
}

export function markNotificationRead(id) {
  return request({ url: `/notifications/${id}/read`, method: 'post' })
}

export function markAllNotificationsRead() {
  return request({ url: '/notifications/read-all', method: 'post' })
}

export function clearAllNotifications() {
  return request({ url: '/notifications/clear-all', method: 'post' })
}
