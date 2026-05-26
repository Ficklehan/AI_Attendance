<template>
  <div class="login-container">
    <div class="background-decoration">
      <div class="bg-blob b1"></div>
      <div class="bg-blob b2"></div>
      <div class="bg-blob b3"></div>
    </div>
    
    <div class="login-wrapper">
      <div class="login-card">
        <div class="card-header">
          <div class="logo-section">
            <div class="logo-icon">
              <ApartmentOutlined />
            </div>
            <div class="logo-text">
              <h2>{{ $t('auth.loginTitle') }}</h2>
              <p>AI-powered Attendance Assistant</p>
            </div>
          </div>
        </div>
        
        <a-form
          ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
          class="login-form"
        >
          <a-form-item name="username">
            <div class="input-wrapper">
              <UserOutlined class="input-icon" />
              <a-input
                v-model:value="form.username"
                :placeholder="$t('auth.username')"
                size="large"
                class="form-input"
              />
            </div>
          </a-form-item>
          
          <a-form-item name="password">
            <div class="input-wrapper">
              <LockOutlined class="input-icon" />
              <a-input-password
                v-model:value="form.password"
                :placeholder="$t('auth.password')"
                size="large"
                class="form-input password-input"
                @keyup.enter="handleLogin"
              />
            </div>
          </a-form-item>
          
          <a-form-item class="form-actions">
            <a-button
              type="primary"
              size="large"
              :loading="loading"
              class="login-btn"
              @click="handleLogin"
            >
              <LoginOutlined />
              {{ $t('auth.login') }}
            </a-button>
          </a-form-item>
          
          <div class="divider-wrapper">
            <div class="divider-line"></div>
            <span class="divider-text">{{ $t('auth.or') }}</span>
            <div class="divider-line"></div>
          </div>
          
          <a-form-item>
            <a-button
              size="large"
              class="feishu-btn"
              @click="handleFeishuLogin"
            >
              <component :is="FeishuIcon" />
              <span>{{ $t('auth.feishuLogin') }}</span>
            </a-button>
          </a-form-item>
        </a-form>
      </div>
      
      <div class="footer-info">
        <p>{{ $t('auth.copyright') }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, h } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, LoginOutlined, ApartmentOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [
    { required: true, message: t('validation.required', { field: t('auth.username') }), trigger: 'blur' },
    { min: 3, max: 50, message: t('validation.minLength', { field: t('auth.username'), min: 3 }) + ' ' + t('validation.maxLength', { field: '', max: 50 }), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('validation.required', { field: t('auth.password') }), trigger: 'blur' },
    { min: 6, message: t('validation.minLength', { field: t('auth.password'), min: 6 }), trigger: 'blur' },
  ],
}

const FeishuIcon = {
  render() {
    return h('svg', {
      width: '20',
      height: '20',
      viewBox: '0 0 1024 1024',
      fill: '#478FDE',
      xmlns: 'http://www.w3.org/2000/svg',
    }, [
      h('path', {
        d: 'M512 0C229.25 0 0 229.25 0 512s229.25 512 512 512 512-229.25 512-512S794.75 0 512 0zm207.375 724.375c-26.25 0-47.5-21.25-47.5-47.5s21.25-47.5 47.5-47.5 47.5 21.25 47.5 47.5-21.25 47.5-47.5 47.5zm-103.75-120.625c-26.25 0-47.5-21.25-47.5-47.5s21.25-47.5 47.5-47.5 47.5 21.25 47.5 47.5-21.25 47.5-47.5 47.5zm99.375-116.25c-26.25 0-47.5-21.25-47.5-47.5s21.25-47.5 47.5-47.5 47.5 21.25 47.5 47.5-21.25 47.5-47.5 47.5zm-198.75-107.5c-82.5 0-150-67.5-150-150s67.5-150 150-150 150 67.5 150 150-67.5 150-150 150zm0-255c-57.5 0-105 47.5-105 105s47.5 105 105 105 105-47.5 105-105-47.5-105-105-105z',
      }),
    ])
  },
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  
  loading.value = true
  try {
    await authStore.login(form)
    message.success('登录成功')
    router.push('/')
  } catch (error) {
    console.error('登录失败:', error)
  } finally {
    loading.value = false
  }
}

const handleFeishuLogin = () => {
  window.location.href = '/api/feishu-auth/login'
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #F5F7FA 0%, #E8F0FE 50%, #F0F4FF 100%);
  position: relative;
  overflow: hidden;
  
  .background-decoration {
    position: absolute;
    width: 100%;
    height: 100%;
    pointer-events: none;
    
    .bg-blob {
      position: absolute;
      border-radius: 50%;
      filter: blur(100px);
      
      &.b1 {
        width: 500px;
        height: 500px;
        background: rgba(91, 143, 249, 0.25);
        top: -150px;
        left: -100px;
        animation: float-blob 12s ease-in-out infinite;
      }
      
      &.b2 {
        width: 450px;
        height: 450px;
        background: rgba(123, 97, 255, 0.2);
        bottom: -100px;
        right: -80px;
        animation: float-blob 10s ease-in-out infinite 3s;
      }
      
      &.b3 {
        width: 350px;
        height: 350px;
        background: rgba(71, 143, 222, 0.2);
        top: 40%;
        right: 15%;
        animation: float-blob 14s ease-in-out infinite 6s;
      }
    }
  }
  
  .login-wrapper {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;
    
    .login-card {
      width: 420px;
      background: #ffffff;
      border-radius: 24px;
      padding: 40px;
      box-shadow: 
        0 20px 60px rgba(91, 143, 249, 0.15),
        0 8px 24px rgba(0, 0, 0, 0.08);
      
      .card-header {
        margin-bottom: 32px;
        text-align: center;
        
        .logo-section {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 14px;
          
          .logo-icon {
            width: 52px;
            height: 52px;
            border-radius: 14px;
            background: linear-gradient(135deg, #5B8FF9 0%, #7B61FF 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 26px;
            color: #fff;
            box-shadow: 0 8px 24px rgba(91, 143, 249, 0.4);
          }
          
          .logo-text {
            text-align: left;
            h2 {
              margin: 0 0 4px;
              font-size: 22px;
              font-weight: 700;
              color: #1F2329;
            }
            
            p {
              margin: 0;
              font-size: 13px;
              color: #86909C;
              letter-spacing: 0.5px;
            }
          }
        }
      }
      
      .login-form {
        :deep(.ant-form-item) {
          margin-bottom: 20px;
        }
        
        :deep(.ant-form-item-label > label) {
          font-weight: 500;
          color: #4E5969;
        }
        
        .input-wrapper {
          position: relative;
          
          .input-icon {
            position: absolute;
            left: 16px;
            top: 50%;
            transform: translateY(-50%);
            color: #86909C;
            font-size: 18px;
            z-index: 1;
          }
          
          .form-input {
            width: 100%;
            background: #F7F8FA;
            border: 2px solid transparent;
            border-radius: 12px;
            color: #1F2329;
            padding-left: 48px;
            transition: all 0.3s ease;
            
            &::placeholder {
              color: #B4B9C0;
            }
            
            &:hover {
              background: #F0F2F5;
            }
            
            &:focus {
              background: #ffffff;
              border-color: #5B8FF9;
              box-shadow: 0 0 0 4px rgba(91, 143, 249, 0.1);
            }
          }
          
          .password-input {
            :deep(.ant-input-password) {
              background: #F7F8FA;
              border: 2px solid transparent;
              border-radius: 12px;
              padding-left: 48px;
              transition: all 0.3s ease;
              
              &:hover {
                background: #F0F2F5;
              }
              
              &.ant-input-password-focused {
                background: #ffffff;
                border-color: #5B8FF9;
                box-shadow: 0 0 0 4px rgba(91, 143, 249, 0.1);
              }
            }
            
            :deep(.ant-input) {
              background: transparent !important;
              border: none !important;
              box-shadow: none !important;
              padding-left: 0 !important;
            }
            
            :deep(.ant-input-suffix) {
              color: #86909C;
            }
          }
        }
        
        .form-actions {
          margin-bottom: 0;
          margin-top: 8px;
          
          .login-btn {
            width: 100%;
            background: linear-gradient(135deg, #5B8FF9 0%, #7B61FF 100%);
            border: none;
            border-radius: 12px;
            height: 48px;
            font-size: 15px;
            font-weight: 600;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            box-shadow: 0 6px 20px rgba(91, 143, 249, 0.4);
            transition: all 0.3s ease;
            
            &:hover {
              transform: translateY(-2px);
              box-shadow: 0 10px 30px rgba(91, 143, 249, 0.5);
            }
            
            &:active {
              transform: translateY(0);
            }
          }
        }
        
        .divider-wrapper {
          display: flex;
          align-items: center;
          gap: 16px;
          margin: 24px 0;
          
          .divider-line {
            flex: 1;
            height: 1px;
            background: #E5E6EB;
          }
          
          .divider-text {
            font-size: 14px;
            color: #86909C;
            font-weight: 500;
          }
        }
        
        .feishu-btn {
          width: 100%;
          background: #F0F7FF;
          border: 2px solid #D4E6FF;
          border-radius: 12px;
          height: 48px;
          font-size: 15px;
          font-weight: 500;
          color: #478FDE;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 10px;
          transition: all 0.3s ease;
          
          &:hover {
            background: #E6F0FF;
            border-color: #A8C8FF;
            transform: translateY(-1px);
          }
          
          svg {
            width: 20px;
            height: 20px;
          }
        }
      }
    }
    
    .footer-info {
      p {
        margin: 0;
        font-size: 13px;
        color: #86909C;
      }
    }
  }
}

@keyframes float-blob {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(30px, -30px) scale(1.1);
  }
}
</style>
