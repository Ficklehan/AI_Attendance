<template>
  <div class="login-container">
    <div class="login-header">
      <div class="logo">📊</div>
      <h1 class="title">AI考勤助手</h1>
      <p class="subtitle">智能识别 · 高效管理</p>
    </div>

    <div class="login-form">
      <div class="form-item">
        <label class="form-label">用户名</label>
        <input
          v-model="formData.username"
          type="text"
          class="form-input"
          placeholder="请输入用户名"
        />
      </div>

      <div class="form-item">
        <label class="form-label">密码</label>
        <input
          v-model="formData.password"
          type="password"
          class="form-input"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        />
      </div>

      <button class="login-btn" :disabled="loading" @click="handleLogin">
        <span v-if="loading">登录中...</span>
        <span v-else>登录</span>
      </button>

      <div class="form-footer">
        <span class="footer-text">还没有账号？</span>
        <span class="footer-link" @click="goToRegister">立即注册</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'

const router = useRouter()
const authStore = useAuthStore()

const formData = ref({
  username: '',
  password: ''
})
const loading = ref(false)

const handleLogin = async () => {
  if (!formData.value.username || !formData.value.password) {
    alert('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    const res = await authApi.login(formData.value)
    if (res && res.success) {
      authStore.setToken(res.data.token)
      authStore.setUserInfo(res.data.userInfo)
      alert('登录成功')
      router.push('/')
    } else {
      alert(res?.message || '登录失败')
    }
  } catch (error) {
    console.error('登录失败:', error)
    alert('登录失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

const goToRegister = () => {
  alert('注册功能开发中')
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  background: linear-gradient(180deg, #5B8FF9 0%, #7B68EE 100%);
  display: flex;
  flex-direction: column;
  padding: 40px 24px;
  padding-top: calc(80px + env(safe-area-inset-top));
  max-width: 480px;
  margin: 0 auto;
}

.login-header {
  text-align: center;
  margin-bottom: 48px;
}

.logo {
  font-size: 64px;
  margin-bottom: 16px;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin-bottom: 8px;
}

.subtitle {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.8);
}

.login-form {
  background: white;
  border-radius: 24px;
  padding: 32px 24px;
}

.form-item {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  font-size: 15px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus {
  border-color: var(--primary-color);
}

.login-btn {
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  margin-top: 12px;
  transition: opacity 0.2s;
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
}

.footer-text {
  color: var(--text-muted);
}

.footer-link {
  color: var(--primary-color);
  font-weight: 500;
  cursor: pointer;
}
</style>
