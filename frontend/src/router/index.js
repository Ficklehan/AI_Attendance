import { createRouter, createWebHistory } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import i18n from '@/locales'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { loadNightShiftRules } from '@/utils/nightShiftRules'
import { isIdleExpired, isTokenExpiredLocally } from '@/utils/auth'
import {
  findSettingsNavByPath,
  firstAccessibleSettingsPath,
  hasAnySettingsAccess,
} from '@/utils/settingsAccess'
import { hasCapability, canManageRoles } from '@/utils/permissions'
import { isSessionValidated, markSessionValidated, resetSessionValidation } from '@/utils/sessionState'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/feishu/callback',
    name: 'FeishuCallback',
    component: () => import('@/views/auth/FeishuCallback.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/Home.vue'),
        meta: { titleKey: 'nav.home' },
      },
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/task/TaskList.vue'),
        meta: { titleKey: 'nav.tasks' },
      },
      {
        path: 'task-records',
        redirect: '/records',
      },
      {
        path: 'attendance',
        redirect: '/records',
      },
      {
        path: 'attendance/records',
        redirect: '/records',
      },
      {
        path: 'attendance/agency-bills',
        redirect: '/agency-bills',
      },
      {
        path: 'clockai',
        redirect: '/records',
      },
      {
        path: 'clockai/records',
        redirect: '/records',
      },
      {
        path: 'clockai/agency-bills',
        redirect: '/agency-bills',
      },
      {
        path: 'records',
        name: 'TaskRecords',
        component: () => import('@/views/task/TaskRecords.vue'),
        meta: { titleKey: 'clockai.menu.records' },
      },
      {
        path: 'agency-bills',
        name: 'AgencyBilling',
        component: () => import('@/views/agency-billing/AgencyBilling.vue'),
        meta: { titleKey: 'clockai.menu.agencyBills' },
      },
      {
        path: 'employees',
        name: 'Employees',
        component: () => import('@/views/employee/EmployeeManagement.vue'),
        meta: { titleKey: 'nav.employees', requiresPermission: 'employees' },
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskEdit',
        component: () => import('@/views/task/TaskEdit.vue'),
        meta: { titleKey: 'nav.taskEdit' },
      },
      {
        path: 'settings',
        component: () => import('@/components/SettingsLayout.vue'),
        redirect: '/settings/ai',
        children: [
          {
            path: 'ai',
            name: 'SettingsAi',
            component: () => import('@/views/config/Config.vue'),
            meta: { titleKey: 'settings.menu.ai', configModule: 'ai', settingsPermission: 'aiConfig' },
          },
          {
            path: 'feishu',
            name: 'SettingsFeishu',
            component: () => import('@/views/config/Config.vue'),
            meta: { titleKey: 'settings.menu.feishu', configModule: 'feishu', settingsPermission: 'feishuConfig' },
          },
          {
            path: 'users',
            name: 'SettingsUsers',
            component: () => import('@/views/settings/UserManagement.vue'),
            meta: { titleKey: 'settings.menu.users', settingsPermission: 'users' },
          },
          {
            path: 'roles',
            name: 'SettingsRoles',
            component: () => import('@/views/settings/RoleManagement.vue'),
            meta: { titleKey: 'settings.menu.roles', requiresSettingsAdmin: true },
          },
          { path: 'permissions', redirect: '/settings/roles' },
          { path: 'data-scope', redirect: '/settings/roles' },
          {
            path: 'reminders',
            name: 'SettingsReminders',
            component: () => import('@/views/settings/SystemRemindersHub.vue'),
            meta: { titleKey: 'settings.menu.systemReminders', settingsPermission: 'reminderConfig' },
          },
          {
            path: 'audit',
            name: 'SettingsAudit',
            component: () => import('@/views/audit/AuditLog.vue'),
            meta: { titleKey: 'settings.menu.audit', settingsPermission: 'audit' },
          },
        ],
      },
      { path: 'config', redirect: '/settings/ai' },
      { path: 'audit', redirect: '/settings/audit' },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login',
  },
]

const router = createRouter({
  history: createWebHistory('/clockai/'),
  routes,
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)
  const requiresAdmin = to.matched.some((record) => record.meta.requiresAdmin)
  const requiresSettingsAdmin = to.matched.some((record) => record.meta.requiresSettingsAdmin)
  const settingsPermission = [...to.matched].reverse().find((record) => record.meta.settingsPermission)?.meta.settingsPermission
  const requiredPermission = [...to.matched].reverse().find((record) => record.meta.requiresPermission)?.meta.requiresPermission
  const t = i18n.global.t

  if (requiresAuth && !authStore.isAuthenticated) {
    next('/login')
    return
  }

  if (authStore.isAuthenticated) {
    if (isTokenExpiredLocally(authStore.token) || isIdleExpired()) {
      authStore.logout()
      resetSessionValidation()
      message.warning(t('auth.sessionExpired'))
      next('/login')
      return
    }

    if (requiresAuth && !isSessionValidated()) {
      try {
        await authStore.fetchUserInfo()
        markSessionValidated()
      } catch {
        resetSessionValidation()
        next('/login')
        return
      }
    }

    loadNightShiftRules(false, getCachedWorkingCountry()).catch(() => {})
  } else {
    resetSessionValidation()
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    next('/home')
    return
  }

  if (authStore.isAuthenticated && (to.path === '/' || to.path === '')) {
    next('/home')
    return
  }

  if (to.path === '/config' && authStore.isAuthenticated) {
    const target = firstAccessibleSettingsPath(authStore)
    if (target) {
      next(target)
    } else {
      message.warning(t('errors.accessDenied'))
      next('/home')
    }
    return
  }

  if (to.path.startsWith('/settings') && authStore.isAuthenticated) {
    if (!hasAnySettingsAccess(authStore)) {
      message.warning(t('errors.accessDenied'))
      next('/home')
      return
    }
    if (to.path === '/settings' || to.path === '/settings/') {
      next(firstAccessibleSettingsPath(authStore) || '/home')
      return
    }
    if (requiresSettingsAdmin && !canManageRoles(authStore)) {
      message.warning(t('errors.adminRequired'))
      next(firstAccessibleSettingsPath(authStore) || '/home')
      return
    }
    if (settingsPermission && !hasCapability(authStore, settingsPermission)) {
      if (!authStore.userInfo?.permissions) {
        try {
          await authStore.fetchUserInfo()
        } catch {
          next('/login')
          return
        }
      }
      const navItem = findSettingsNavByPath(to.path)
      if (navItem && !hasCapability(authStore, settingsPermission)) {
        message.warning(t('errors.accessDenied'))
        next(firstAccessibleSettingsPath(authStore) || '/home')
        return
      }
    }
  }

  if (requiresAdmin && authStore.isAuthenticated) {
    if (!authStore.userInfo?.role) {
      try {
        await authStore.fetchUserInfo()
      } catch {
        next('/login')
        return
      }
    }
    if (!authStore.isAdmin) {
      const t = i18n.global.t
      message.warning(t('errors.adminRequired'))
      next('/home')
      return
    }
  }

  if (requiredPermission && authStore.isAuthenticated && !hasCapability(authStore, requiredPermission)) {
    if (!authStore.userInfo?.permissions) {
      try {
        await authStore.fetchUserInfo()
      } catch {
        next('/login')
        return
      }
    }
    if (!hasCapability(authStore, requiredPermission)) {
      message.warning(t('errors.accessDenied'))
      next('/home')
      return
    }
  }

  next()
})

export default router
