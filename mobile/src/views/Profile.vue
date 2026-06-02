<template>
  <div class="profile-container">
    <div class="profile-header">
      <div class="avatar">
        <span>{{ userInitial }}</span>
      </div>
      <div class="user-info">
        <div class="user-name">{{ userInfo?.username || '未登录' }}</div>
        <div class="user-email">{{ userInfo?.email || '-' }}</div>
      </div>
    </div>

    <div class="stats-section card">
      <div class="stat-item">
        <div class="stat-value">{{ stats.total || 0 }}</div>
        <div class="stat-label">总任务</div>
      </div>
      <div class="stat-item">
        <div class="stat-value">{{ stats.pending || 0 }}</div>
        <div class="stat-label">待处理</div>
      </div>
      <div class="stat-item">
        <div class="stat-value">{{ stats.completed || 0 }}</div>
        <div class="stat-label">已完成</div>
      </div>
    </div>

    <div class="menu-section">
      <div class="menu-item">
        <span class="menu-icon">⚙️</span>
        <span class="menu-text">设置</span>
        <span class="menu-arrow">›</span>
      </div>
      <div class="menu-item">
        <span class="menu-icon">❓</span>
        <span class="menu-text">帮助中心</span>
        <span class="menu-arrow">›</span>
      </div>
      <div class="menu-item">
        <span class="menu-icon">📝</span>
        <span class="menu-text">反馈建议</span>
        <span class="menu-arrow">›</span>
      </div>
    </div>

    <div v-if="isLoggedIn" class="logout-section">
      <button class="logout-btn" @click="handleLogout">退出登录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { taskApi } from '@/api'

const router = useRouter()
const authStore = useAuthStore()

const userInfo = computed(() => authStore.userInfo)
const isLoggedIn = computed(() => authStore.isLoggedIn)

const stats = ref({ total: 0, pending: 0, completed: 0, records: 0 })

const userInitial = computed(() => {
  if (userInfo.value?.username) {
    return userInfo.value.username.charAt(0).toUpperCase()
  }
  return '?'
})

const loadStats = async () => {
  if (!isLoggedIn.value) return
  try {
    const res = await taskApi.getTaskStats()
    if (res && res.success) {
      stats.value = res.data
    }
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

const handleLogout = () => {
  if (confirm('确定要退出登录吗？')) {
    authStore.logout()
    router.push('/login')
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.profile-container {
  padding-bottom: 100px;
}

.profile-header {
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  padding: 40px 24px;
  padding-top: calc(40px + env(safe-area-inset-top));
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
  font-weight: 600;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: white;
  margin-bottom: 4px;
}

.user-email {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.stats-section {
  margin: 16px;
  display: flex;
  justify-content: space-around;
  padding: 24px 16px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--primary-color);
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-muted);
}

.menu-section {
  background: white;
  margin: 0 16px;
  border-radius: 12px;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-icon {
  font-size: 20px;
  margin-right: 12px;
}

.menu-text {
  flex: 1;
  font-size: 15px;
  color: var(--text-primary);
}

.menu-arrow {
  color: var(--text-muted);
  font-size: 18px;
}

.logout-section {
  padding: 24px 16px;
}

.logout-btn {
  width: 100%;
  padding: 14px;
  background: white;
  color: var(--error-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}
</style>
