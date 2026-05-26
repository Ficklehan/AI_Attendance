<template>
  <div class="tasks-container">
    <div class="page-header">
      <h1 class="header-title">任务列表</h1>
    </div>

    <div class="tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        :class="['tab-item', { active: currentTab === tab.key }]"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="taskList.length > 0" class="task-list">
      <div
        v-for="task in filteredTasks"
        :key="task.id"
        class="task-card"
        @click="goToDetail(task.id)"
      >
        <div class="task-main">
          <div class="task-header">
            <span class="task-name ellipsis">{{ task.name || `任务 #${task.id}` }}</span>
            <div :class="['tag', getStatusTag(task.status)]">
              {{ getStatusText(task.status) }}
            </div>
          </div>
          <div class="task-meta">
            <span class="meta-item">{{ formatTime(task.createTime) }}</span>
            <span v-if="task.recordCount" class="meta-item">{{ task.recordCount }}条记录</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <div class="empty-icon">📋</div>
      <div class="empty-text">暂无任务</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { taskApi } from '@/api'

const router = useRouter()

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待处理' },
  { key: 'completed', label: '已完成' }
]

const currentTab = ref('all')
const taskList = ref([])

const filteredTasks = computed(() => {
  if (currentTab.value === 'all') {
    return taskList.value
  } else if (currentTab.value === 'pending') {
    return taskList.value.filter(t => ['PENDING', 'RECOGNIZING', 'processed'].includes(t.status))
  } else {
    return taskList.value.filter(t => ['COMPLETED', 'SUBMITTED', 'confirmed', 'cancelled'].includes(t.status))
  }
})

const switchTab = (key) => {
  currentTab.value = key
}

const loadTasks = async () => {
  try {
    const res = await taskApi.getTaskList({})
    if (res && res.success) {
      taskList.value = res.data || []
    }
  } catch (error) {
    console.error('加载任务失败:', error)
  }
}

const goToDetail = (taskId) => {
  router.push(`/result/${taskId}`)
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`

  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const getStatusTag = (status) => {
  const tags = {
    'PENDING': 'tag-default',
    'RECOGNIZING': 'tag-warning',
    'processed': 'tag-primary',
    'confirmed': 'tag-success',
    'SUBMITTED': 'tag-success',
    'COMPLETED': 'tag-success',
    'cancelled': 'tag-default',
    'FAILED': 'tag-error'
  }
  return tags[status] || 'tag-default'
}

const getStatusText = (status) => {
  const texts = {
    'PENDING': '待识别',
    'RECOGNIZING': '识别中',
    'processed': '待确认',
    'confirmed': '已确认',
    'SUBMITTED': '已提交',
    'COMPLETED': '已完成',
    'cancelled': '已取消',
    'FAILED': '失败'
  }
  return texts[status] || status || '未知'
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.tasks-container {
  padding-bottom: 100px;
}

.page-header {
  background: white;
  padding: 16px;
  padding-top: calc(16px + env(safe-area-inset-top));
  border-bottom: 1px solid var(--border-color);
}

.header-title {
  font-size: 20px;
  font-weight: 600;
}

.tab-bar {
  display: flex;
  background: white;
  padding: 8px 16px;
  gap: 8px;
}

.tab-item {
  flex: 1;
  padding: 8px 16px;
  background: var(--bg-color);
  border: none;
  border-radius: 20px;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.tab-item.active {
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  color: white;
}

.task-list {
  padding: 12px;
}

.task-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.task-card:active {
  background: #f8f8f8;
}

.task-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.task-name {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.task-meta {
  display: flex;
  gap: 12px;
}

.meta-item {
  font-size: 13px;
  color: var(--text-muted);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 24px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.4;
}

.empty-text {
  color: var(--text-muted);
  font-size: 14px;
}
</style>
