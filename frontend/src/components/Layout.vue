<template>
  <a-layout class="layout-container">
    <a-layout-header class="header">
      <div class="header__left">
        <div class="logo">
          <div class="logo__icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M3 9L12 2L21 9V20C21 20.5304 20.7893 21.0391 20.4142 21.4142C20.0391 21.7893 19.5304 22 19 22H5C4.46957 22 3.96086 21.7893 3.58579 21.4142C3.21071 21.0391 3 20.5304 3 20V9Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M9 22V12H15V22" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="logo__text">{{ $t('auth.loginTitle') }}</span>
        </div>

        <nav class="nav">
          <router-link
            v-for="item in menuItems"
            :key="item.path"
            :to="item.path"
            class="nav__item"
            :class="{ 'nav__item--active': activeMenu === item.path }"
          >
            <component :is="item.icon" class="nav__icon" />
            <span>{{ $t(item.labelKey) }}</span>
          </router-link>
        </nav>
      </div>

      <div class="header__right">
        <!-- Working Country -->
        <a-popover
          v-model:open="countryPopoverVisible"
          trigger="click"
          placement="bottomRight"
          :arrow="false"
          class="country-popover"
        >
          <template #content>
            <div class="country-panel">
              <div class="country-panel__header">
                <span class="country-panel__header-flag">{{ DEFAULT_COUNTRY_FLAG }}</span>
                <span>{{ $t('country.title') }}</span>
              </div>
              <p class="country-panel__hint">{{ $t('country.selectHint') }}</p>
              <a-select
                v-model:value="countryDraft"
                :options="localizedCountryOptions"
                :loading="countryStore.loading"
                show-search
                option-filter-prop="label"
                class="country-panel__select"
                :placeholder="$t('country.placeholder')"
              />
              <div v-if="countryStore.bundle" class="country-panel__tags">
                <a-tag v-if="countryStore.promptFromGlobalFallback" color="orange" size="small">
                  {{ $t('config.aiFallbackGlobal') }}
                </a-tag>
                <a-tag v-else color="blue" size="small">{{ $t('config.aiCountrySpecific') }}</a-tag>
                <a-tag v-if="countryStore.feishuFromGlobalFallback" color="orange" size="small">
                  {{ $t('config.feishuFallbackGlobal') }}
                </a-tag>
                <a-tag v-else color="green" size="small">{{ $t('config.feishuCountrySpecific') }}</a-tag>
              </div>
              <div class="country-panel__actions">
                <a-button type="primary" block :loading="countrySaving" @click="applyWorkingCountry">
                  {{ $t('country.apply') }}
                </a-button>
                <a-button v-if="authStore.isAdmin" type="link" block @click="goCountryConfig">
                  {{ $t('country.manageConfig') }}
                </a-button>
              </div>
            </div>
          </template>
          <a-tooltip :title="countryButtonTooltip">
            <button class="header-btn header-btn--country country-popover" type="button">
              <span class="header-btn__flag" aria-hidden="true">{{ countryButtonFlag }}</span>
            </button>
          </a-tooltip>
        </a-popover>

        <!-- Language Selector -->
        <a-popover
          v-model:open="langPopoverVisible"
          trigger="click"
          placement="bottomRight"
          :arrow="false"
          class="lang-popover"
        >
          <template #content>
            <div class="lang-panel">
              <div class="lang-panel__header">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/>
                  <path d="M2 12H22" stroke="currentColor" stroke-width="2"/>
                  <path d="M12 2C14.5013 4.73835 15.9228 8.29203 16 12C15.9228 15.708 14.5013 19.2616 12 22C9.49872 19.2616 8.07725 15.708 8 12C8.07725 8.29203 9.49872 4.73835 12 2Z" stroke="currentColor" stroke-width="2"/>
                </svg>
                <span>{{ $t('language.select') }}</span>
              </div>
              <div class="lang-panel__options">
                <div
                  v-for="lang in languageOptions"
                  :key="lang.value"
                  class="lang-option"
                  :class="{ 'lang-option--active': currentLocale === lang.value }"
                  @click="handleLocaleChange(lang.value)"
                >
                  <span class="lang-option__flag">{{ lang.flag }}</span>
                  <span class="lang-option__name">{{ lang.label }}</span>
                  <svg v-if="currentLocale === lang.value" class="lang-option__check" width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <path d="M5 12L10 17L20 7" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </div>
              </div>
            </div>
          </template>
          <a-tooltip :title="$t('language.title')">
            <button class="header-btn">
              <GlobalOutlined />
            </button>
          </a-tooltip>
        </a-popover>

        <!-- Notifications -->
        <a-tooltip :title="$t('notification.title')">
          <button
            class="header-btn header-btn--notify"
            :class="{ 'header-btn--notify-active': unreadCount > 0 }"
            type="button"
            @click="notificationDrawerOpen = true"
          >
            <a-badge :count="unreadCount" :overflow-count="99" :offset="[-2, 2]" :show-zero="false">
              <BellOutlined />
            </a-badge>
          </button>
        </a-tooltip>

        <!-- Export center -->
        <a-tooltip :title="$t('export.centerTitle')">
          <button class="header-btn header-btn--export" type="button" @click="openExportCenter">
            <a-badge :count="activeExportCount" :overflow-count="99" :offset="[-2, 2]" :show-zero="false">
              <CloudDownloadOutlined />
            </a-badge>
          </button>
        </a-tooltip>

        <!-- User Dropdown -->
        <a-dropdown placement="bottomRight">
          <template #overlay>
            <a-menu @click="handleMenuClick">
              <a-menu-item key="profile">{{ t('auth.profile') }}</a-menu-item>
              <a-menu-item key="changePassword">{{ t('auth.changePassword') }}</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" danger>{{ t('auth.logout') }}</a-menu-item>
            </a-menu>
          </template>
          <div class="user-pill">
            <div class="user-pill__avatar">
              {{ (authStore.realName || authStore.username || '?').charAt(0).toUpperCase() }}
            </div>
            <span class="user-pill__name">{{ authStore.realName || authStore.username }}</span>
            <DownOutlined class="user-pill__arrow" />
          </div>
        </a-dropdown>
      </div>
    </a-layout-header>

    <a-layout-content class="main-content">
      <router-view :key="route.fullPath" />
    </a-layout-content>

    <ExportJobDrawer v-model:open="exportDrawerOpen" />
    <NotificationDrawer v-model:open="notificationDrawerOpen" @read="refreshUnreadCount" />
    <ChangePasswordModal v-model:open="changePasswordOpen" />
  </a-layout>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useCountryStore } from '@/stores/country'
import { DEFAULT_COUNTRY_FLAG, COUNTRY_FLAG_FALLBACK } from '@/utils/countryCatalog'
import { buildCountrySelectOption, formatCountryLabel } from '@/utils/countryLabels'
import { message, Modal } from 'ant-design-vue'
import {
  CalendarOutlined,
  HomeOutlined,
  UnorderedListOutlined,
  SettingOutlined,
  UserOutlined,
  ApartmentOutlined,
  CloudDownloadOutlined,
  BellOutlined,
  DownOutlined,
  GlobalOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'
import ExportJobDrawer from '@/components/ExportJobDrawer.vue'
import NotificationDrawer from '@/components/NotificationDrawer.vue'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'
import { useExportCenter, startSummaryPolling, stopSummaryPolling } from '@/composables/useExportCenter'
import { getUnreadCount } from '@/api/notification'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { exportDrawerOpen, activeExportCount, openExportCenter, refreshExportSummary } = useExportCenter()
const countryStore = useCountryStore()
const { locale, t } = useI18n()

const currentLocale = ref(locale.value)
const langPopoverVisible = ref(false)
const countryPopoverVisible = ref(false)
const countryDraft = ref('default')
const countrySaving = ref(false)
const changePasswordOpen = ref(false)
const notificationDrawerOpen = ref(false)
const unreadCount = ref(0)
let unreadPollTimer = null

const refreshUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    unreadCount.value = Number(res.data?.count || 0)
  } catch (e) {
    // ignore when logged out
  }
}

watch(
  () => countryStore.workingCountry,
  (code) => {
    countryDraft.value = code || 'default'
  },
  { immediate: true }
)

const localizedCountryOptions = computed(() => {
  void locale.value
  return (countryStore.options || []).map((item) => buildCountrySelectOption(item))
})

const localizedWorkingCountryLabel = computed(() => {
  void locale.value
  const code = countryStore.workingCountry || 'default'
  const found = (countryStore.options || []).find((item) => item.code === code)
  if (found) return formatCountryLabel(found.code, found.flag, found.name)
  if (code === 'default') return formatCountryLabel('default', DEFAULT_COUNTRY_FLAG, '全局默认')
  return formatCountryLabel(code, COUNTRY_FLAG_FALLBACK[code], code)
})

const countryButtonFlag = computed(() => countryStore.workingCountryMeta.flag || DEFAULT_COUNTRY_FLAG)

const countryButtonTooltip = computed(() => {
  return `${t('country.title')}: ${localizedWorkingCountryLabel.value}`
})

const languageOptions = [
  { value: 'zh-CN', label: '简体中文', flag: '🇨🇳' },
  { value: 'en-US', label: 'English', flag: '🇺🇸' },
  { value: 'fr-FR', label: 'Français', flag: '🇫🇷' },
  { value: 'nl-NL', label: 'Nederlands', flag: '🇳🇱' },
  { value: 'cs-CZ', label: 'Čeština', flag: '🇨🇿' },
  { value: 'pl-PL', label: 'Polski', flag: '🇵🇱' },
  { value: 'de-DE', label: 'Deutsch', flag: '🇩🇪' },
  { value: 'es-ES', label: 'Español', flag: '🇪🇸' },
]

const menuItems = computed(() => {
  const items = [
    { path: '/home', labelKey: 'nav.home', icon: HomeOutlined },
    { path: '/tasks', labelKey: 'nav.tasks', icon: UnorderedListOutlined },
    { path: '/task-records', labelKey: 'nav.taskRecords', icon: CalendarOutlined },
  ]
  if (authStore.isAdmin) {
    items.push({ path: '/settings/ai', labelKey: 'nav.settings', icon: SettingOutlined })
  }
  return items
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/settings')) return '/settings/ai'
  return route.path
})

const applyWorkingCountry = async () => {
  if (!countryDraft.value) {
    message.warning(t('country.placeholder'))
    return
  }
  countrySaving.value = true
  try {
    await countryStore.setWorkingCountry(countryDraft.value)
    countryPopoverVisible.value = false
    message.success(t('country.saved'))
  } catch (error) {
    console.error('设置工作国家失败:', error)
  } finally {
    countrySaving.value = false
  }
}

const goCountryConfig = () => {
  countryPopoverVisible.value = false
  if (authStore.isAdmin) {
    router.push('/settings/ai')
  }
}

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
  } else if (key === 'changePassword') {
    changePasswordOpen.value = true
  }
}

onMounted(async () => {
  const savedLocale = localStorage.getItem('locale')
  if (savedLocale && ['zh-CN', 'en-US', 'fr-FR', 'nl-NL', 'cs-CZ', 'pl-PL', 'de-DE', 'es-ES'].includes(savedLocale)) {
    currentLocale.value = savedLocale
    locale.value = savedLocale
  }
  if (authStore.isAuthenticated) {
    try {
      await countryStore.hydrate()
    } catch (error) {
      console.error('加载工作国家失败:', error)
    }
    startSummaryPolling()
    refreshUnreadCount()
    unreadPollTimer = window.setInterval(refreshUnreadCount, 60_000)
  }
})

onUnmounted(() => {
  stopSummaryPolling()
  if (unreadPollTimer) {
    clearInterval(unreadPollTimer)
    unreadPollTimer = null
  }
})

watch(
  () => authStore.isAuthenticated,
  (ok) => {
    if (ok) {
      startSummaryPolling()
      refreshUnreadCount()
      refreshExportSummary()
    } else {
      stopSummaryPolling()
    }
  },
)

const updateDocumentTitle = () => {
  const titleKey = route.meta.titleKey
  document.title = titleKey ? t(titleKey) : t('auth.loginTitle')
}

watch(() => [route.meta.titleKey, locale.value], updateDocumentTitle, { immediate: true })
</script>

<style lang="scss" scoped>
// ═══════════════════════════════════════════════════════════
// Layout — Atelier v4
// ═══════════════════════════════════════════════════════════

.layout-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: $bg-body;
}

// ── Header ──
.header {
  height: $header-height;
  line-height: $header-height;
  background: rgba($bg-surface, 0.92);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border-bottom: 1px solid rgba($border, 0.5);
  padding: 0 $space-6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 100;

  // Override ant layout header defaults
  :deep(&) {
    background: rgba($bg-surface, 0.92) !important;
  }
}

.header__left {
  display: flex;
  align-items: center;
  gap: $space-8;
}

.header__right {
  display: flex;
  align-items: center;
  gap: $space-2;
}

// ── Logo ──
.logo {
  display: flex;
  align-items: center;
  gap: $space-3;
  text-decoration: none;

  &__icon {
    width: 32px;
    height: 32px;
    border-radius: $radius-md;
    background: $primary-gradient;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    flex-shrink: 0;
    box-shadow: 0 2px 8px rgba($primary, 0.25);
  }

  &__text {
    font-size: $font-size-lg;
    font-weight: $font-weight-extrabold;
    color: $text-strong;
    letter-spacing: -0.02em;
  }
}

// ── Navigation ──
.nav {
  display: flex;
  align-items: center;
  gap: $space-1;
  height: $header-height;

  &__item {
    display: flex;
    align-items: center;
    gap: $space-2;
    padding: 0 $space-3;
    height: calc(#{$header-height} - 4px);
    margin-top: 2px;
    border-radius: $radius-md;
    text-decoration: none;
    color: $text-secondary;
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
    transition: all $duration-base $ease-smooth;
    position: relative;

    &:hover {
      color: $text-strong;
      background: $bg-hover;
    }

    &--active {
      color: $primary;
      background: $primary-light;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 20px;
        height: 3px;
        border-radius: 2px;
        background: $primary;
      }
    }
  }

  &__icon {
    font-size: 16px;
  }
}

// ── Header Button ──
.header-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: $radius-md;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $text-secondary;
  font-size: 17px;
  transition: all $duration-fast $ease-smooth;

  &:hover {
    background: $bg-hover;
    color: $text-strong;
  }
}

.header-btn--notify-active {
  color: $primary;

  &:hover {
    background: $primary-light;
    color: $primary-dark;
  }
}

.header-btn--country {
  font-size: 20px;
  line-height: 1;
}

.header-btn__flag {
  display: block;
  font-size: 20px;
  line-height: 1;
}

// ── User Pill ──
.user-pill {
  display: flex;
  align-items: center;
  gap: $space-2;
  padding: $space-1 $space-3 $space-1 $space-1;
  border-radius: $radius-full;
  cursor: pointer;
  transition: background $duration-fast;
  margin-left: $space-2;

  &:hover {
    background: $bg-hover;
  }

  &__avatar {
    width: 30px;
    height: 30px;
    border-radius: 50%;
    background: $primary-gradient;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
    flex-shrink: 0;
  }

  &__name {
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
    color: $text-strong;
  }

  &__arrow {
    font-size: 10px;
    color: $text-tertiary;
  }
}

// ── Main Content ──
.main-content {
  flex: 1;
  width: 100%;
  padding: $space-5 $space-6 $space-6;
  min-height: calc(100vh - #{$header-height});
  background: $bg-body;
  box-sizing: border-box;
}

// ── Language Panel ──
.lang-popover {
  :deep(.ant-popover-inner) {
    padding: 0;
    border-radius: $radius-xl;
    box-shadow: $shadow-lg;
    overflow: hidden;
    border: 1px solid rgba($border, 0.5);
  }

  :deep(.ant-popover-arrow) {
    display: none;
  }
}

.lang-panel {
  min-width: 220px;
  background: $bg-surface;

  &__header {
    display: flex;
    align-items: center;
    gap: $space-3;
    padding: $space-4 $space-5;
    border-bottom: 1px solid $border-light;
    background: $bg-muted;
    color: $primary;

    span {
      font-size: $font-size-lg;
      font-weight: $font-weight-semibold;
      color: $text-strong;
    }
  }

  &__options {
    padding: $space-2;
  }
}

.country-popover {
  :deep(.ant-popover-inner) {
    padding: 0;
    border-radius: $radius-xl;
    box-shadow: $shadow-lg;
    overflow: hidden;
    border: 1px solid rgba($border, 0.5);
  }

  :deep(.ant-popover-arrow) {
    display: none;
  }
}

.country-panel {
  width: 280px;
  padding: $space-4;
  background: $bg-surface;

  &__header {
    display: flex;
    align-items: center;
    gap: $space-2;
    font-weight: $font-weight-semibold;
    color: $text-strong;
    margin-bottom: $space-2;
  }

  &__header-flag {
    font-size: 18px;
    line-height: 1;
  }

  &__hint {
    font-size: $font-size-sm;
    color: $text-secondary;
    margin: 0 0 $space-3;
    line-height: 1.5;
  }

  &__select {
    width: 100%;
    margin-bottom: $space-3;
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: $space-1;
    margin-bottom: $space-3;
  }

  &__actions {
    display: flex;
    flex-direction: column;
    gap: $space-1;
  }
}

.lang-option {
  display: flex;
  align-items: center;
  gap: $space-3;
  padding: $space-3 $space-3;
  border-radius: $radius-md;
  cursor: pointer;
  transition: all $duration-fast;

  &:hover {
    background: $bg-hover;
  }

  &--active {
    background: $primary-light;

    .lang-option__name {
      color: $primary;
      font-weight: $font-weight-semibold;
    }
  }

  &__flag {
    font-size: 20px;
    line-height: 1;
  }

  &__name {
    flex: 1;
    font-size: $font-size-md;
    color: $text-primary;
    font-weight: $font-weight-medium;
  }

  &__check {
    color: $primary;
    flex-shrink: 0;
  }
}
</style>
