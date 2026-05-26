<template>
  <div class="callback-container">
    <div class="loading-content">
      <div class="spinner"></div>
      <p>正在处理飞书登录...</p>
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
    
    console.log('飞书回调 - 收到参数:', { token: token ? token.substring(0, 20) + '...' : null, userInfoStr })
    
    if (!token || !userInfoStr) {
      throw new Error('缺少登录信息')
    }
    
    const userInfo = JSON.parse(userInfoStr)
    
    // 更新 auth store
    authStore.token = token
    authStore.userInfo = userInfo
    authStore.roles = userInfo.role ? [userInfo.role] : []
    
    // 保存到 localStorage
    setToken(token)
    localStorage.setItem('userInfo', userInfoStr)
    
    console.log('飞书登录成功，准备跳转到首页')
    message.success('飞书登录成功')
    
    // 跳转到首页
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
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  
  .loading-content {
    text-align: center;
    color: #fff;
    font-size: 16px;
    
    .spinner {
      width: 40px;
      height: 40px;
      border: 4px solid rgba(255, 255, 255, 0.3);
      border-top-color: #5B8FF9;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 20px;
    }
    
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
  }
}
</style>