<template>
  <div class="tab-bar safe-bottom">
    <div
      v-for="item in tabs"
      :key="item.path"
      :class="['tab-item', { active: currentPath === item.path }]"
      @click="switchTab(item.path)"
    >
      <span class="tab-icon">{{ item.icon }}</span>
      <span class="tab-label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const tabs = [
  { path: '/', label: '识别', icon: '📷' },
  { path: '/chat', label: 'AI助手', icon: '💬' },
  { path: '/tasks', label: '任务', icon: '📋' },
  { path: '/profile', label: '我的', icon: '👤' }
]

const currentPath = computed(() => route.path)

const switchTab = (path) => {
  if (currentPath.value !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
.tab-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  background: white;
  display: flex;
  justify-content: space-around;
  padding: 8px 0;
  padding-bottom: calc(8px + var(--safe-area-bottom));
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  z-index: 100;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 16px;
  cursor: pointer;
  transition: all 0.3s;
}

.tab-icon {
  font-size: 24px;
  margin-bottom: 2px;
  opacity: 0.4;
  transition: opacity 0.3s;
}

.tab-label {
  font-size: 11px;
  color: var(--text-muted);
  transition: color 0.3s;
}

.tab-item.active .tab-icon {
  opacity: 1;
}

.tab-item.active .tab-label {
  color: var(--primary-color);
  font-weight: 500;
}
</style>
