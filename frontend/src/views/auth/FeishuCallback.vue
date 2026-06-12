<template>
  <div class="callback-container">
    <div class="loading-content">
      <div class="spinner"></div>
      <p>正在处理飞书登录…</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { message } from 'ant-design-vue'
import { setToken } from '@/utils/auth'
import request from '@/api/index'

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  try {
    const urlParams = new URLSearchParams(window.location.search)
    const exchangeCode = urlParams.get('exchange')

    if (!exchangeCode) {
      throw new Error('缺少登录凭证')
    }

    const res = await request({
      url: '/feishu-auth/exchange',
      method: 'post',
      data: { code: exchangeCode },
    })

    const data = res.data
    const userInfo = data.userInfo

    authStore.token = data.token
    authStore.userInfo = userInfo
    authStore.roles = userInfo?.role ? [userInfo.role] : []

    setToken(data.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo))

    message.success('飞书登录成功')

    const redirect = urlParams.get('redirect')
    const target = redirect && redirect.startsWith('/') && !redirect.startsWith('//')
      ? redirect
      : '/'

    setTimeout(() => {
      router.replace(target)
    }, 500)
  } catch (error) {
    console.error('飞书登录失败:', error)
    message.error('登录失败: ' + (error.message || '请重试'))
    setTimeout(() => {
      router.replace('/login')
    }, 1000)
  }
})
</script>

<style lang="scss" scoped>
.callback-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-body;

  .loading-content {
    text-align: center;
    color: $text-primary;
    font-size: 15px;

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(60, 60, 67, 0.12);
      border-top-color: $primary;
      border-radius: 50%;
      animation: spin 0.75s linear infinite;
      margin: 0 auto 16px;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
  }
}
</style>
