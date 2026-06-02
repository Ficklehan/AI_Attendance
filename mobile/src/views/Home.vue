<template>
  <div class="home-container">
    <div class="header">
      <h1 class="header-title">AI考勤助手</h1>
      <p class="header-subtitle">拍照识别考勤表，一键提交</p>
    </div>

    <div class="upload-area" @click="goToCamera">
      <div class="upload-icon">
        <span>📷</span>
      </div>
      <div class="upload-text">点击拍照或上传图片</div>
      <div class="upload-hint">支持 JPG、PNG 格式，自动压缩</div>
    </div>

    <div v-if="imageList.length > 0" class="image-list">
      <div class="image-list-header">
        <span class="text-base font-medium">已选择 {{ imageList.length }} 张图片</span>
        <span class="text-sm text-muted" @click="clearImages">清空</span>
      </div>
      <div class="image-items">
        <div v-for="(item, index) in imageList" :key="index" class="image-item">
          <img :src="item" alt="" class="image-thumb" />
          <div class="image-delete" @click.stop="deleteImage(index)">
            <span>✕</span>
          </div>
        </div>
      </div>
    </div>

    <div class="country-selector card">
      <div class="selector-header">
        <span class="text-base font-medium">选择国家配置</span>
      </div>
      <div class="country-items">
        <div
          v-for="country in countries"
          :key="country.code"
          :class="['country-item', { active: currentCountry === country.code }]"
          @click="selectCountry(country.code)"
        >
          {{ country.name }}
        </div>
      </div>
    </div>

    <div v-if="imageList.length > 0" class="action-area">
      <button class="btn-primary" :class="{ loading: isRecognizing }" @click="startRecognition">
        <span v-if="isRecognizing">识别中...</span>
        <span v-else>开始识别</span>
      </button>
    </div>

    <div class="quick-tasks card">
      <div class="quick-header">
        <span class="text-base font-medium">最近任务</span>
        <span class="text-sm text-muted" @click="goToTasks">查看全部</span>
      </div>
      <div v-if="recentTasks.length > 0" class="task-list">
        <div v-for="task in recentTasks" :key="task.id" class="task-item" @click="goToResult(task.id)">
          <div class="task-info">
            <span class="task-name ellipsis">{{ task.name }}</span>
            <span class="task-time text-sm text-muted">{{ formatTime(task.createTime) }}</span>
          </div>
          <div :class="['tag', getTaskStatusTag(task.status)]">
            {{ getTaskStatusText(task.status) }}
          </div>
        </div>
      </div>
      <div v-else class="empty-tips">
        <span class="text-muted">暂无任务记录</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { uploadApi, taskApi } from '@/api'

const router = useRouter()

const imageList = ref([])
const currentCountry = ref('CN')
const recentTasks = ref([])
const isRecognizing = ref(false)

const countries = [
  { code: 'CN', name: '中国' },
  { code: 'US', name: '美国' },
  { code: 'UK', name: '英国' },
  { code: 'DE', name: '德国' },
  { code: 'FR', name: '法国' }
]

const goToCamera = () => {
  router.push('/camera')
}

const chooseImage = (event) => {
  const files = event.target.files
  if (files) {
    Array.from(files).forEach(file => {
      const reader = new FileReader()
      reader.onload = (e) => {
        imageList.value.push(e.target.result)
      }
      reader.readAsDataURL(file)
    })
  }
}

const deleteImage = (index) => {
  imageList.value.splice(index, 1)
}

const clearImages = () => {
  if (confirm('确定要清空所有已选择的图片吗？')) {
    imageList.value = []
  }
}

const selectCountry = (code) => {
  currentCountry.value = code
}

const startRecognition = async () => {
  if (!imageList.value.length) {
    alert('请先选择图片')
    return
  }

  isRecognizing.value = true

  try {
    const results = []
    for (let i = 0; i < imageList.value.length; i++) {
      const result = await uploadAndRecognize(imageList.value[i], i)
      if (result) results.push(result)
    }

    if (results.length > 0) {
      const taskId = results[0].data.taskId
      router.push(`/result/${taskId}`)
    } else {
      alert('识别失败')
    }
  } catch (error) {
    console.error('识别异常:', error)
    alert('识别异常')
  } finally {
    isRecognizing.value = false
  }
}

const uploadAndRecognize = async (dataUrl, index) => {
  try {
    const blob = await (await fetch(dataUrl)).blob()
    const file = new File([blob], `image_${index}.jpg`, { type: 'image/jpeg' })
    return await uploadApi.uploadImage(file)
  } catch (error) {
    console.error(`上传第${index}张图片失败:`, error)
    return null
  }
}

const loadRecentTasks = async () => {
  try {
    const res = await taskApi.getTaskList({ limit: 5 })
    if (res && res.success) {
      recentTasks.value = res.data
    }
  } catch (error) {
    console.error('加载任务失败:', error)
  }
}

const goToTasks = () => {
  router.push('/tasks')
}

const goToResult = (taskId) => {
  router.push(`/result/${taskId}`)
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours().toString().padStart(2, '0')
  const minute = date.getMinutes().toString().padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}

const getTaskStatusTag = (status) => {
  const tags = {
    'PENDING': 'tag-default',
    'RECOGNIZING': 'tag-warning',
    'COMPLETED': 'tag-success',
    'FAILED': 'tag-error'
  }
  return tags[status] || 'tag-default'
}

const getTaskStatusText = (status) => {
  const texts = {
    'PENDING': '待识别',
    'RECOGNIZING': '识别中',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return texts[status] || '未知'
}

onMounted(() => {
  loadRecentTasks()
})
</script>

<style scoped>
.home-container {
  padding: 16px;
  padding-bottom: 100px;
}

.header {
  padding: 24px 0;
  text-align: center;
}

.header-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.header-subtitle {
  font-size: 14px;
  color: var(--text-muted);
}

.upload-area {
  background: linear-gradient(135deg, #5B8FF9 0%, #7B68EE 100%);
  border-radius: 16px;
  padding: 48px 24px;
  text-align: center;
  margin-bottom: 16px;
  cursor: pointer;
  transition: transform 0.2s;
}

.upload-area:active {
  transform: scale(0.98);
}

.upload-icon {
  width: 72px;
  height: 72px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  font-size: 36px;
}

.upload-text {
  font-size: 18px;
  color: #ffffff;
  font-weight: 500;
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.image-list {
  margin-bottom: 16px;
}

.image-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.image-items {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.image-item {
  position: relative;
  flex-shrink: 0;
  width: 100px;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
}

.image-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}

.country-selector {
  margin-bottom: 16px;
}

.selector-header {
  margin-bottom: 12px;
}

.country-items {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.country-item {
  flex-shrink: 0;
  padding: 10px 20px;
  background: var(--bg-color);
  border-radius: 20px;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.country-item.active {
  background: var(--primary-color);
  color: #ffffff;
}

.action-area {
  margin-bottom: 16px;
}

.quick-tasks {
  margin-bottom: 16px;
}

.quick-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
}

.task-item:last-child {
  border-bottom: none;
}

.task-info {
  flex: 1;
}

.task-name {
  font-size: 15px;
  color: var(--text-primary);
  display: block;
  margin-bottom: 4px;
}

.task-time {
  font-size: 13px;
}

.empty-tips {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
}
</style>
