<template>
  <a-layout class="layout-container">
    <a-layout-header class="header">
      <div class="header-left">
        <div class="logo">
          <div class="logo-icon">
            <RobotOutlined />
          </div>
          <span class="logo-text">{{ $t('auth.loginTitle') }}</span>
        </div>
        
        <div class="nav-menu">
          <router-link 
            v-for="item in menuItems" 
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: activeMenu === item.path }"
          >
            <component :is="item.icon" />
            <span>{{ $t(item.labelKey) }}</span>
          </router-link>
        </div>
      </div>
      
      <div class="header-right">
        <a-popover 
          v-model:open="langPopoverVisible"
          trigger="click"
          placement="bottomRight"
          :arrow="false"
          class="lang-popover"
        >
          <template #content>
            <div class="lang-popover-content">
              <div class="lang-popover-header">
                <GlobalOutlined class="lang-popover-icon" />
                <span>{{ $t('language.select') }}</span>
              </div>
              <div class="lang-options">
                <div 
                  v-for="lang in languageOptions"
                  :key="lang.value"
                  class="lang-option-item"
                  :class="{ active: currentLocale === lang.value }"
                  @click="handleLocaleChange(lang.value)"
                >
                  <span class="lang-flag">{{ lang.flag }}</span>
                  <span class="lang-name">{{ lang.label }}</span>
                  <CheckOutlined v-if="currentLocale === lang.value" class="lang-check" />
                </div>
              </div>
            </div>
          </template>
          <a-tooltip :title="$t('language.title')">
            <button class="lang-icon-btn">
              <GlobalOutlined />
            </button>
          </a-tooltip>
        </a-popover>
        <a-tooltip :title="$t('messages.notifications')">
          <button class="action-btn">
            <BellOutlined />
          </button>
        </a-tooltip>
        <a-dropdown placement="bottomRight">
            <template #overlay>
              <a-menu @click="handleMenuClick">
                <a-menu-item key="profile">
                  {{ t('auth.profile') }}
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" danger>
                  {{ t('auth.logout') }}
                </a-menu-item>
              </a-menu>
            </template>
            <div class="user-info">
              <div class="user-avatar">
                <UserOutlined />
              </div>
              <span class="user-name">{{ authStore.realName || authStore.username }}</span>
              <DownOutlined />
            </div>
          </a-dropdown>
      </div>
    </a-layout-header>
    
    <a-layout-content class="main-content">
      <router-view :key="route.fullPath" />
    </a-layout-content>
  </a-layout>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { message, Modal } from 'ant-design-vue'
import { 
  HomeOutlined, 
  UnorderedListOutlined, 
  SettingOutlined, 
  FileTextOutlined, 
  UserOutlined,
  RobotOutlined,
  BellOutlined,
  DownOutlined,
  GlobalOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { locale, t } = useI18n()

const currentLocale = ref(locale.value)
const langPopoverVisible = ref(false)

const languageOptions = [
  { value: 'zh-CN', label: '简体中文', flag: '🇨🇳' },
  { value: 'en-US', label: 'English', flag: '🇺🇸' },
  { value: 'fr-FR', label: 'Français', flag: '🇫🇷' },
  { value: 'nl-NL', label: 'Nederlands', flag: '🇳🇱' },
  { value: 'cs-CZ', label: 'Čeština', flag: '🇨🇿' },
  { value: 'pl-PL', label: 'Polski', flag: '🇵🇱' },
  { value: 'de-DE', label: 'Deutsch', flag: '🇩🇪' },
]

const menuItems = [
  { path: '/home', label: '首页', labelKey: 'nav.home', icon: HomeOutlined },
  { path: '/tasks', label: '任务列表', labelKey: 'nav.tasks', icon: UnorderedListOutlined },
  { path: '/config', label: '配置管理', labelKey: 'nav.config', icon: SettingOutlined },
  { path: '/audit', label: '审计日志', labelKey: 'nav.audit', icon: FileTextOutlined },
]

const activeMenu = computed(() => route.path)

const handleLocaleChange = (value) => {
  locale.value = value
  localStorage.setItem('locale', value)
  currentLocale.value = value
  langPopoverVisible.value = false
  message.success(t('messages.savePreferences'))
}

const handleMenuClick = ({ key }) => {
  if (key === 'logout') {
    Modal.confirm({
      title: t('auth.logout'),
      content: t('messages.confirmLogout'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: () => {
        authStore.logout()
        router.push('/login')
      },
    })
  } else if (key === 'profile') {
    message.info(t('messages.profileFeature'))
  }
}

onMounted(() => {
  const savedLocale = localStorage.getItem('locale')
  if (savedLocale && ['zh-CN', 'en-US', 'fr-FR', 'nl-NL', 'cs-CZ', 'pl-PL', 'de-DE'].includes(savedLocale)) {
    currentLocale.value = savedLocale
    locale.value = savedLocale
  }
})
</script>

<style lang="scss" scoped>
.layout-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F5F7FA;
}

.header {
  height: 48px;
  line-height: 48px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(31, 35, 41, 0.06);
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 100;
  
  .header-left {
    display: flex;
    align-items: center;
    gap: 32px;
    
    .logo {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .logo-icon {
        width: 28px;
        height: 28px;
        border-radius: 6px;
        background: linear-gradient(135deg, #5B8FF9 0%, #7B61FF 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 14px;
        color: #fff;
        flex-shrink: 0;
      }
      
      .logo-text {
        font-size: 15px;
        font-weight: 600;
        color: #1F2329;
        letter-spacing: 0.3px;
      }
    }
    
    .nav-menu {
      display: flex;
      align-items: center;
      gap: 4px;
      height: 48px;
      
      .nav-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 0 12px;
        border-radius: 6px;
        text-decoration: none;
        color: #646A73;
        font-size: 13px;
        font-weight: 500;
        transition: all 0.2s ease;
        height: 48px;
        position: relative;
        
        &:hover {
          color: #1F2329;
          background: #F5F7FA;
        }
        
        &.active {
          color: #5B8FF9;
          background: rgba(91, 143, 249, 0.08);
          
          &::after {
            content: '';
            position: absolute;
            bottom: 0;
            left: 50%;
            transform: translateX(-50%);
            width: 20px;
            height: 2px;
            background: #5B8FF9;
            border-radius: 2px 2px 0 0;
          }
        }
      }
    }
  }
  
  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
    
    .lang-icon-btn {
      width: 36px;
      height: 36px;
      border: none;
      background: transparent;
      border-radius: 8px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #646A73;
      font-size: 18px;
      transition: all 0.2s ease;
      
      &:hover {
        background: rgba(91, 143, 249, 0.08);
        color: #5B8FF9;
      }
    }
    
    .action-btn {
      width: 36px;
      height: 36px;
      border: none;
      background: transparent;
      border-radius: 8px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #646A73;
      font-size: 18px;
      transition: all 0.2s ease;
      
      &:hover {
        background: rgba(91, 143, 249, 0.08);
        color: #5B8FF9;
      }
    }
    
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 12px 4px 4px;
      border-radius: 6px;
      cursor: pointer;
      transition: background-color 0.2s ease;
      
      &:hover {
        background: #F5F7FA;
      }
      
      .user-avatar {
        width: 28px;
        height: 28px;
        border-radius: 6px;
        background: linear-gradient(135deg, #5B8FF9 0%, #7B61FF 100%);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        font-size: 13px;
      }
      
      .user-name {
        font-size: 13px;
        font-weight: 500;
        color: #1F2329;
      }
      
      :deep(.anticon) {
        font-size: 10px;
        color: #8F959E;
      }
    }
  }
}

.main-content {
  flex: 1;
  padding: 24px;
  min-height: calc(100vh - 48px);
}

// Language selector styles
.lang-popover {
  :deep(.ant-popover-inner) {
    padding: 0;
    border-radius: 12px;
    box-shadow: 0 6px 16px 0 rgba(31, 35, 41, 0.08), 
                0 3px 6px -4px rgba(31, 35, 41, 0.12), 
                0 9px 28px 8px rgba(31, 35, 41, 0.05);
    overflow: hidden;
  }

  :deep(.ant-popover-arrow) {
    display: none;
  }
}

.lang-popover-content {
  min-width: 220px;
  background: #ffffff;
}

.lang-popover-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid #F0F1F5;
  background: linear-gradient(135deg, #F8F9FF 0%, #F0F4FF 100%);

  .lang-popover-icon {
    font-size: 20px;
    color: #5B8FF9;
  }

  span {
    font-size: 15px;
    font-weight: 600;
    color: #1F2329;
  }
}

.lang-options {
  padding: 8px;
}

.lang-option-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;

  &:hover {
    background: #F5F7FA;
  }

  &.active {
    background: rgba(91, 143, 249, 0.08);

    .lang-name {
      color: #5B8FF9;
      font-weight: 600;
    }

    .lang-flag {
      transform: scale(1.1);
    }
  }

  .lang-flag {
    font-size: 22px;
    line-height: 1;
    transition: transform 0.2s ease;
  }

  .lang-name {
    flex: 1;
    font-size: 14px;
    color: #1F2329;
    font-weight: 500;
  }

  .lang-check {
    font-size: 14px;
    color: #5B8FF9;
    font-weight: 700;
  }
}
</style>
