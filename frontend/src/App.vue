<template>
  <router-view />
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { startSessionGuard } from '@/utils/sessionGuard'

const authStore = useAuthStore()
const router = useRouter()
const { t } = useI18n()

let stopSessionGuard = null

function handleSessionExpired() {
  if (!authStore.isAuthenticated) return
  authStore.logout()
  message.warning(t('auth.sessionExpired'))
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

watch(
  () => authStore.isAuthenticated,
  (authed) => {
    stopSessionGuard?.()
    stopSessionGuard = null
    if (authed) {
      stopSessionGuard = startSessionGuard(() => handleSessionExpired())
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  stopSessionGuard?.()
})
</script>

<style lang="scss">
#app {
  height: 100%;
}
</style>
