import { hasCapability, canManageRoles } from './permissions'

export const SETTINGS_NAV = [
  { path: '/settings/ai', labelKey: 'settings.menu.ai', permission: 'aiConfig' },
  { path: '/settings/feishu', labelKey: 'settings.menu.feishu', permission: 'feishuConfig' },
  { path: '/settings/users', labelKey: 'settings.menu.users', permission: 'users' },
  { path: '/settings/roles', labelKey: 'settings.menu.roles', adminOnly: true },
  { path: '/settings/reminders', labelKey: 'settings.menu.systemReminders', permission: 'reminderConfig' },
  { path: '/settings/audit', labelKey: 'settings.menu.audit', permission: 'audit' },
]

export function canAccessSettingsItem(authStore, item) {
  if (!item) return false
  if (item.adminOnly) return canManageRoles(authStore)
  if (!item.permission) return false
  return hasCapability(authStore, item.permission)
}

export function accessibleSettingsNav(authStore) {
  return SETTINGS_NAV.filter((item) => canAccessSettingsItem(authStore, item))
}

export function firstAccessibleSettingsPath(authStore) {
  return accessibleSettingsNav(authStore)[0]?.path || null
}

export function hasAnySettingsAccess(authStore) {
  return accessibleSettingsNav(authStore).length > 0
}

export function findSettingsNavByPath(path) {
  return SETTINGS_NAV.find((item) => path === item.path || path.startsWith(`${item.path}/`))
}
