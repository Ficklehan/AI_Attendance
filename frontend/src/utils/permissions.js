/**
 * 功能权限：admin 拥有全部能力；其他角色以 permissions.json 为准。
 * 与后端 PermissionService 语义对齐，避免散落 isAdmin 硬编码。
 */
export function hasCapability(authStore, permissionKey) {
  if (!authStore?.isAuthenticated) return false
  if (authStore.isAdmin) return true
  if (!permissionKey) return false
  return authStore.userInfo?.permissions?.[permissionKey] === true
}

export function hasAnyCapability(authStore, permissionKeys) {
  if (!Array.isArray(permissionKeys) || permissionKeys.length === 0) return false
  return permissionKeys.some((key) => hasCapability(authStore, key))
}

/** 角色管理仅 admin（system_role 内置保护，无独立 permission key） */
export function canManageRoles(authStore) {
  return authStore?.isAdmin === true
}
