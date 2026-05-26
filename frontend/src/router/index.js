import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
        meta: { title: '首页' },
      },
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/task/TaskList.vue'),
        meta: { title: '任务列表' },
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskEdit',
        component: () => import('@/views/task/TaskEdit.vue'),
        meta: { title: '任务编辑' },
      },
      {
        path: 'config',
        name: 'Config',
        component: () => import('@/views/config/Config.vue'),
        meta: { title: '配置管理' },
      },
      {
        path: 'audit',
        name: 'AuditLog',
        component: () => import('@/views/audit/AuditLog.vue'),
        meta: { title: '审计日志' },
      },
      {
        path: 'service',
        name: 'Service',
        component: () => import('@/views/service/Service.vue'),
        meta: { title: '服务管理' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  
  console.log('路由守卫 - to:', to.path)
  console.log('路由守卫 - isAuthenticated:', authStore.isAuthenticated)
  console.log('路由守卫 - token:', authStore.token ? authStore.token.substring(0, 20) + '...' : '无')
  
  if (requiresAuth && !authStore.isAuthenticated) {
    console.log('需要认证但未登录，跳转到登录页')
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    console.log('已登录但访问登录页，跳转到首页')
    next('/')
  } else {
    console.log('正常通行')
    next()
  }
})

export default router
