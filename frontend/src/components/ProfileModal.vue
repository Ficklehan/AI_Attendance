<template>
  <a-modal
    :open="open"
    :title="$t('auth.profile')"
    :footer="null"
    width="520px"
    destroy-on-close
    @update:open="(v) => emit('update:open', v)"
  >
    <div class="profile-modal">
      <div class="profile-modal__header">
        <div class="profile-modal__avatar">
          {{ displayInitial }}
        </div>
        <div>
          <div class="profile-modal__name">{{ authStore.realName || authStore.username }}</div>
          <div v-if="authStore.userInfo?.email" class="profile-modal__meta">{{ authStore.userInfo.email }}</div>
          <div v-if="authStore.username" class="profile-modal__meta">{{ $t('auth.username') }}: {{ authStore.username }}</div>
        </div>
      </div>

      <div class="profile-modal__section">
        <div class="profile-modal__label">{{ $t('settings.users.role') }}</div>
        <a-space wrap size="small">
          <a-tag
            v-for="roleKey in roleKeys"
            :key="roleKey"
            :color="roleKey === 'admin' ? 'blue' : 'default'"
          >
            {{ roleNameMap[roleKey] || roleKey }}
          </a-tag>
        </a-space>
      </div>

      <div class="profile-modal__section">
        <div class="profile-modal__label">{{ $t('profile.capabilities') }}</div>
        <a-space v-if="enabledCapabilities.length" wrap size="small">
          <a-tag v-for="cap in enabledCapabilities" :key="cap.key" color="processing">
            {{ $t(cap.nameKey) }}
          </a-tag>
        </a-space>
        <span v-else class="profile-modal__muted">{{ $t('profile.noCapabilities') }}</span>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { listRoles } from '@/api/roles'
import { CAPABILITY_DEFS } from '@/constants/capabilityDefs'

const props = defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open'])

const authStore = useAuthStore()
const systemRoles = ref([])

const displayInitial = computed(() => {
  const name = authStore.realName || authStore.username || '?'
  return name.charAt(0).toUpperCase()
})

const roleKeys = computed(() => {
  if (authStore.roles?.length) return authStore.roles
  return authStore.userInfo?.role ? [authStore.userInfo.role] : ['user']
})

const roleNameMap = computed(() => {
  const map = {}
  systemRoles.value.forEach((role) => {
    map[role.roleKey] = role.roleName
  })
  return map
})

const enabledCapabilities = computed(() => {
  if (authStore.isAdmin) return CAPABILITY_DEFS
  const perms = authStore.userInfo?.permissions || {}
  return CAPABILITY_DEFS.filter((def) => perms[def.key] === true)
})

watch(
  () => props.open,
  async (visible) => {
    if (!visible) return
    try {
      const res = await listRoles()
      systemRoles.value = res.data || []
    } catch {
      systemRoles.value = []
    }
  },
)
</script>

<style scoped lang="scss">
.profile-modal__header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.profile-modal__avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: rgba(22, 119, 255, 0.12);
  color: #1677ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
}

.profile-modal__name {
  font-size: 18px;
  font-weight: 600;
}

.profile-modal__meta {
  color: var(--text-secondary, #666);
  font-size: 13px;
  margin-top: 2px;
}

.profile-modal__section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.profile-modal__label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: rgba(0, 0, 0, 0.65);
}

.profile-modal__muted {
  color: var(--text-secondary, #888);
  font-size: 13px;
}
</style>
