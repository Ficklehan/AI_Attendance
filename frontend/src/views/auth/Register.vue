<template>
  <div class="register-container">
    <a-card class="register-card" :bordered="false">
      <div class="card-header">
        <div class="logo-icon">
          <UserOutlined />
        </div>
        <h2>{{ $t('auth.loginTitle') }}</h2>
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
            block
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
  realName: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在 3 到 50 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: async (_rule, value) => {
        if (value !== form.password) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
}

const handleRegister = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    await register({
      username: form.username,
      email: form.email,
      password: form.password,
      realName: form.realName || undefined
    })
    message.success('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    if (error?.errorFields) return
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-body;
  padding: 24px;
}

.register-card {
  width: 100%;
  max-width: 400px;
  border-radius: $radius-xl;
  border: 1px solid $border;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);

  :deep(.ant-card-body) {
    padding: 32px;
  }

  .card-header {
    text-align: center;
    margin-bottom: 28px;

    .logo-icon {
      width: 48px;
      height: 48px;
      margin: 0 auto 16px;
      border-radius: 12px;
      background: $primary;
      color: $text-inverted;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
    }

    h2 {
      margin: 0 0 $space-2;
      font-size: $font-size-2xl;
      font-weight: $font-weight-extrabold;
      color: $text-strong;
      letter-spacing: -0.02em;
    }

    p {
      margin: 0;
      color: $text-secondary;
      font-size: 14px;
    }
  }

  .form-footer {
    text-align: center;
    font-size: 14px;
    color: $text-secondary;
    margin-top: 8px;

    a {
      color: $primary;
      font-weight: $font-weight-medium;
      text-decoration: none;
      margin-left: 4px;

      &:hover {
        text-decoration: underline;
      }
    }
  }
}
</style>
