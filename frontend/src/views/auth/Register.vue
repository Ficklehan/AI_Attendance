<template>
  <div class="register-container">
    <a-card class="register-card">
      <div class="card-header">
        <h2>AI考勤智能助手</h2>
        <p>创建新账户</p>
      </div>
      
      <a-form
        ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @submit.prevent="handleRegister"
      >
        <a-form-item name="username">
          <a-input
            v-model:value="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="UserOutlined"
          />
        </a-form-item>
        
        <a-form-item name="email">
          <a-input
            v-model:value="form.email"
            type="email"
            placeholder="邮箱"
            size="large"
            :prefix-icon="MailOutlined"
          />
        </a-form-item>
        
        <a-form-item name="password">
          <a-input-password
            v-model:value="form.password"
            placeholder="密码"
            size="large"
            :prefix-icon="LockOutlined"
            visibility-toggle
          />
        </a-form-item>
        
        <a-form-item name="confirmPassword">
          <a-input-password
            v-model:value="form.confirmPassword"
            placeholder="确认密码"
            size="large"
            :prefix-icon="LockOutlined"
            visibility-toggle
          />
        </a-form-item>
        
        <a-form-item name="realName">
          <a-input
            v-model:value="form.realName"
            placeholder="真实姓名（可选）"
            size="large"
            :prefix-icon="UserOutlined"
          />
        </a-form-item>
        
        <a-form-item>
          <a-button
            type="primary"
            size="large"
            :loading="loading"
            style="width: 100%"
            @click="handleRegister"
          >
            注册
          </a-button>
        </a-form-item>
        
        <div class="form-footer">
          <span>已有账户？</span>
          <router-link to="/login">立即登录</router-link>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, MailOutlined } from '@ant-design/icons-vue'
import { register } from '@/api/auth'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  realName: '',
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在3-50个字符之间', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    ({ getFieldValue }) => ({
      validator(_, value) {
        if (!value || getFieldValue('password') === value) {
          return Promise.resolve()
        }
        return Promise.reject(new Error('两次输入的密码不一致'))
      },
    }),
  ],
}

const handleRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  
  loading.value = true
  try {
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
      realName: form.realName,
    })
    
    message.success('注册成功，即将自动登录...')
    
    setTimeout(() => {
      router.push('/')
    }, 1500)
  } catch (error) {
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 400px;
  
  .card-header {
    text-align: center;
    margin-bottom: 24px;
    
    h2 {
      margin: 0 0 8px;
      color: #303133;
    }
    
    p {
      margin: 0;
      color: #909399;
      font-size: 14px;
    }
  }
  
  .form-footer {
    text-align: center;
    font-size: 14px;
    color: #606266;
    margin-top: 16px;
    
    a {
      color: #1890ff;
      text-decoration: none;
      margin-left: 4px;
      
      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>