<template>
  <div class="login-container">
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
import { API_BASE_PATH } from '@/constants/apiBase'

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
  window.location.href = `${API_BASE_PATH}/feishu-auth/login`
}
</script>

<style lang="scss" scoped>
// Login — Atelier v4
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-body;
  background-image:
    radial-gradient(ellipse 80% 60% at 50% 30%, rgba($primary, 0.05) 0%, transparent 100%),
    radial-gradient(ellipse 40% 40% at 80% 70%, rgba($accent, 0.03) 0%, transparent 100%);

  .login-wrapper {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 24px;

    .login-card {
      width: 420px;
      background: $bg-surface;
      border-radius: $radius-2xl;
      padding: 40px;
      border: 1px solid rgba($border, 0.5);
      box-shadow: $shadow-lg;
      animation: fadeUp 0.5s $ease-out both;

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
            background: $primary-gradient-deep;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 26px;
            color: $text-inverted;
            box-shadow: $shadow-glow;
          }

          .logo-text {
            text-align: left;
            h2 {
              margin: 0 0 4px;
              font-size: 22px;
              font-weight: $font-weight-extrabold;
              color: $text-strong;
              letter-spacing: -0.02em;
            }

            p {
              margin: 0;
              font-size: $font-size-base;
              color: $text-tertiary;
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
          font-weight: $font-weight-medium;
          color: $text-primary;
        }

        .input-wrapper {
          position: relative;

          .input-icon {
            position: absolute;
            left: 16px;
            top: 50%;
            transform: translateY(-50%);
            color: $text-tertiary;
            font-size: 18px;
            z-index: 1;
          }

          .form-input {
            width: 100%;
            background: $bg-muted;
            border: 2px solid transparent;
            border-radius: $radius-lg;
            color: $text-strong;
            padding-left: 48px;
            transition: all 0.3s $ease-smooth;

            &::placeholder {
              color: $text-tertiary;
            }

            &:hover {
              background: $bg-hover;
            }

            &:focus {
              background: $bg-surface;
              border-color: $primary;
              box-shadow: 0 0 0 3px $primary-glow;
            }
          }

          .password-input {
            :deep(.ant-input-password) {
              background: $bg-muted;
              border: 2px solid transparent;
              border-radius: $radius-lg;
              padding-left: 48px;
              transition: all 0.3s $ease-smooth;

              &:hover {
                background: $bg-hover;
              }

              &.ant-input-password-focused {
                background: $bg-surface;
                border-color: $primary;
                box-shadow: 0 0 0 4px $primary-glow;
              }
            }

            :deep(.ant-input) {
              background: transparent !important;
              border: none !important;
              box-shadow: none !important;
              padding-left: 0 !important;
            }

            :deep(.ant-input-suffix) {
              color: $text-tertiary;
            }
          }
        }

        .form-actions {
          margin-bottom: 0;
          margin-top: 8px;

          .login-btn {
            width: 100%;
            background: $primary-gradient;
            border: none;
            border-radius: $radius-lg;
            height: 48px;
            font-size: $font-size-lg;
            font-weight: $font-weight-bold;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            box-shadow: $shadow-glow;
            transition: all 0.2s $ease-smooth;

            &:hover {
              background: $primary-gradient-deep;
              box-shadow: $shadow-glow-lg;
              transform: translateY(-1px);
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
            background: $border;
          }

          .divider-text {
            font-size: $font-size-md;
            color: $text-tertiary;
            font-weight: $font-weight-medium;
          }
        }

        .feishu-btn {
          width: 100%;
          background: $bg-surface;
          border: 1px solid $border-accent;
          border-radius: $radius-lg;
          height: 48px;
          font-size: $font-size-lg;
          font-weight: $font-weight-bold;
          color: $primary;
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 10px;
          transition: all 0.2s $ease-smooth;

          &:hover {
            background: $primary-light;
            border-color: $primary;
            box-shadow: $shadow-glow;
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
        font-size: $font-size-base;
        color: $text-tertiary;
      }
    }
  }
}

</style>
