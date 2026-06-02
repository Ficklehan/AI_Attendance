import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
      meta: { title: '首页', requiresAuth: true }
    },
    {
      path: '/camera',
      name: 'camera',
      component: () => import('@/views/Camera.vue'),
      meta: { title: '拍照', requiresAuth: true }
    },
    {
      path: '/result/:id',
      name: 'result',
      component: () => import('@/views/Result.vue'),
      meta: { title: '识别结果', requiresAuth: true }
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/Chat.vue'),
      meta: { title: 'AI助手', requiresAuth: true }
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('@/views/Tasks.vue'),
      meta: { title: '任务', requiresAuth: true }
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/Profile.vue'),
      meta: { title: '我的', requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { title: '登录', guest: true }
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title + ' - AI考勤助手'
  }
  
  // 需要登录的页面
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next({ name: 'login' })
  } 
  // 游客页面（已登录用户不允许访问）
  else if (to.meta.guest && authStore.isLoggedIn) {
    next({ name: 'home' })
  }
  // 其他情况正常访问
  else {
    next()
  }
})

export default router
