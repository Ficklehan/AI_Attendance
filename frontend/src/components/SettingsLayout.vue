<template>
  <div class="settings-layout page-inner">
    <aside class="settings-sidebar surface-card">
      <h2 class="settings-sidebar__title">{{ $t('settings.title') }}</h2>
      <nav class="settings-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="settings-nav__item"
          :class="{ 'settings-nav__item--active': isActive(item.path) }"
        >
          <component :is="item.icon" class="settings-nav__icon" />
          <span>{{ $t(item.labelKey) }}</span>
        </router-link>
      </nav>
    </aside>
    <section class="settings-main">
      <router-view />
    </section>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import {
  RobotOutlined,
  ApiOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  FileSearchOutlined,
  BellOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()

const navItems = [
  { path: '/settings/ai', labelKey: 'settings.menu.ai', icon: RobotOutlined },
  { path: '/settings/feishu', labelKey: 'settings.menu.feishu', icon: ApiOutlined },
  { path: '/settings/users', labelKey: 'settings.menu.users', icon: TeamOutlined },
  { path: '/settings/roles', labelKey: 'settings.menu.roles', icon: SafetyCertificateOutlined },
  { path: '/settings/reminders', labelKey: 'settings.menu.systemReminders', icon: BellOutlined },
  { path: '/settings/audit', labelKey: 'settings.menu.audit', icon: FileSearchOutlined },
]

const isActive = (path) => {
  return route.path === path || route.path.startsWith(path + '/')
}
</script>

<style scoped lang="scss">
.settings-layout {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  min-height: calc(100vh - 120px);
}

.settings-sidebar {
  flex: 0 0 220px;
  padding: 20px 12px;
  border-radius: 12px;
  position: sticky;
  top: 88px;
}

.settings-sidebar__title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 12px 16px;
  color: var(--text-primary, #1a1a1a);
}

.settings-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--text-secondary, #5c5c5c);
  text-decoration: none;
  transition: background 0.15s, color 0.15s;

  &:hover {
    background: rgba(0, 0, 0, 0.04);
    color: var(--text-primary, #1a1a1a);
  }

  &--active {
    background: rgba(22, 119, 255, 0.1);
    color: #1677ff;
    font-weight: 500;
  }
}

.settings-nav__icon {
  font-size: 16px;
}

.settings-main {
  flex: 1;
  min-width: 0;
}
</style>
