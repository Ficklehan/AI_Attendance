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

const router = useRouter()
const authStore = useAuthStore()

onMounted(async () => {
  try {
    const urlParams = new URLSearchParams(window.location.search)
    const token = urlParams.get('token')
    const userInfoStr = urlParams.get('userInfo')

    if (!token || !userInfoStr) {
      throw new Error('缺少登录信息')
    }

    const userInfo = JSON.parse(userInfoStr)

    authStore.token = token
    authStore.userInfo = userInfo
    authStore.roles = userInfo.role ? [userInfo.role] : []

    setToken(token)
    localStorage.setItem('userInfo', userInfoStr)

    message.success('飞书登录成功')

    setTimeout(() => {
      router.replace('/')
    }, 500)
  } catch (error) {
    console.error('飞书登录失败:', error)
    message.error('登录失败: ' + error.message)
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
