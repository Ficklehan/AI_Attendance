import { createRouter, createWebHistory } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import i18n from '@/locales'

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
        name: 'TaskRecords',
        component: () => import('@/views/task/TaskRecords.vue'),
        meta: { titleKey: 'nav.taskRecords' },
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
        meta: { requiresAdmin: true },
        redirect: '/settings/ai',
        children: [
          {
            path: 'ai',
            name: 'SettingsAi',
            component: () => import('@/views/config/Config.vue'),
            meta: { titleKey: 'settings.menu.ai', configModule: 'ai' },
          },
          {
            path: 'feishu',
            name: 'SettingsFeishu',
            component: () => import('@/views/config/Config.vue'),
            meta: { titleKey: 'settings.menu.feishu', configModule: 'feishu' },
          },
          {
            path: 'users',
            name: 'SettingsUsers',
            component: () => import('@/views/settings/UserManagement.vue'),
            meta: { titleKey: 'settings.menu.users' },
          },
          {
            path: 'roles',
            name: 'SettingsRoles',
            component: () => import('@/views/settings/RoleManagement.vue'),
            meta: { titleKey: 'settings.menu.roles' },
          },
          { path: 'permissions', redirect: '/settings/roles' },
          { path: 'data-scope', redirect: '/settings/roles' },
          {
            path: 'audit',
            name: 'SettingsAudit',
            component: () => import('@/views/audit/AuditLog.vue'),
            meta: { titleKey: 'settings.menu.audit' },
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
  history: createWebHistory('/attendance/'),
  routes,
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)
  const requiresAdmin = to.matched.some((record) => record.meta.requiresAdmin)

  if (requiresAuth && !authStore.isAuthenticated) {
    next('/login')
    return
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    next('/home')
    return
  }

  if (authStore.isAuthenticated && (to.path === '/' || to.path === '')) {
    next('/home')
    return
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

  next()
})

export default router
