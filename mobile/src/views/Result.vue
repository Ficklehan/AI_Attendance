<template>
  <div class="result-container">
    <div class="result-header">
      <button class="back-btn" @click="goBack">
        <span>←</span>
      </button>
      <span class="header-title">识别结果</span>
      <div class="header-right"></div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <div class="loading-text">加载中...</div>
    </div>

    <div v-else class="result-content">
      <div class="summary-card card">
        <div class="summary-item">
          <span class="summary-value">{{ recordCount }}</span>
          <span class="summary-label">识别记录</span>
        </div>
        <div class="summary-item">
          <span class="summary-value success">{{ validCount }}</span>
          <span class="summary-label">有效记录</span>
        </div>
        <div class="summary-item">
          <span class="summary-value warning">{{ abnormalCount }}</span>
          <span class="summary-label">异常记录</span>
        </div>
      </div>

      <div class="records-section">
        <div class="section-header">
          <span class="section-title">识别详情</span>
        </div>

        <div v-if="records.length > 0" class="records-list">
          <div
            v-for="(record, index) in records"
            :key="index"
            class="record-card"
          >
            <div class="record-header">
              <span class="record-index">#{{ index + 1 }}</span>
              <span
                v-if="record.riskLevel"
                :class="['risk-tag', record.riskLevel]"
              >
                {{ getRiskText(record.riskLevel) }}
              </span>
            </div>
            <div class="record-content">
              <div
                v-for="(value, key) in record"
                :key="key"
                class="record-field"
                v-if="!['riskLevel', 'anomalies', 'deleted'].includes(key)"
              >
                <span class="field-key">{{ key }}:</span>
                <span class="field-value">{{ value }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-records">
          <div class="empty-icon">📋</div>
          <div class="empty-text">暂无识别数据</div>
        </div>
      </div>

      <div class="action-area">
        <button v-if="canConfirm" class="btn-primary" @click="handleConfirm">
          确认提交
        </button>
        <button v-else class="btn-secondary" disabled>
          {{ getButtonText() }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { taskApi } from '@/api'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const taskDetail = ref(null)
const records = ref([])

const recordCount = computed(() => records.value.length)
const validCount = computed(() => records.value.filter(r => !r.deleted).length)
const abnormalCount = computed(() => records.value.filter(r => r.riskLevel && r.riskLevel !== 'none').length)

const canConfirm = computed(() => {
  return taskDetail.value && ['processed', 'PENDING'].includes(taskDetail.value.status)
})

const loadTaskDetail = async () => {
  const taskId = route.params.id
  if (!taskId) {
    router.back()
    return
  }

  loading.value = true
  try {
    const res = await taskApi.getTaskDetail(taskId)
    if (res && res.success) {
      taskDetail.value = res.data
      if (res.data.rawData) {
        try {
          records.value = JSON.parse(res.data.rawData)
        } catch (e) {
          console.error('解析数据失败:', e)
        }
      }
    }
  } catch (error) {
    console.error('加载详情失败:', error)
  } finally {
    loading.value = false
  }
}

const handleConfirm = async () => {
  if (!confirm('确认提交这些记录？')) return

  try {
    const res = await taskApi.confirmTask(taskDetail.value.id, { data: records.value })
    if (res && res.success) {
      alert('提交成功！')
      router.back()
    }
  } catch (error) {
    console.error('提交失败:', error)
  }
}

const getRiskText = (level) => {
  const texts = {
    'none': '正常',
    'low': '低风险',
    'medium': '中风险',
    'high': '高风险'
  }
  return texts[level] || level
}

const getButtonText = () => {
  const status = taskDetail.value?.status
  const texts = {
    'confirmed': '已确认',
    'SUBMITTED': '已提交',
    'COMPLETED': '已完成',
    'cancelled': '已取消',
    'RECOGNIZING': '识别中'
  }
  return texts[status] || '已处理'
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  loadTaskDetail()
})
</script>

<style scoped>
.result-container {
  min-height: 100vh;
  background: var(--bg-color);
  padding-bottom: 100px;
  max-width: 480px;
  margin: 0 auto;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  padding-top: calc(16px + env(safe-area-inset-top));
  background: white;
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  border: none;
  background: none;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  width: 40px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  margin-top: 16px;
  color: var(--text-muted);
  font-size: 14px;
}

.result-content {
  padding: 16px;
}

.summary-card {
  display: flex;
  justify-content: space-around;
  padding: 24px 16px;
  margin-bottom: 16px;
}

.summary-item {
  text-align: center;
}

.summary-value {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.summary-value.success {
  color: var(--success-color);
}

.summary-value.warning {
  color: var(--warning-color);
}

.summary-label {
  font-size: 13px;
  color: var(--text-muted);
}

.records-section {
  margin-bottom: 16px;
}

.section-header {
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color);
}

.record-index {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.risk-tag {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
}

.risk-tag.none {
  background: rgba(82, 196, 26, 0.1);
  color: var(--success-color);
}

.risk-tag.low {
  background: rgba(250, 173, 20, 0.1);
  color: var(--warning-color);
}

.risk-tag.medium {
  background: rgba(250, 173, 20, 0.15);
  color: #e67700;
}

.risk-tag.high {
  background: rgba(255, 77, 79, 0.1);
  color: var(--error-color);
}

.record-content {
  display: grid;
  gap: 8px;
}

.record-field {
  display: flex;
  font-size: 14px;
}

.field-key {
  color: var(--text-muted);
  min-width: 70px;
  margin-right: 8px;
}

.field-value {
  color: var(--text-primary);
  flex: 1;
}

.empty-records {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  background: white;
  border-radius: 12px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.4;
}

.empty-text {
  color: var(--text-muted);
  font-size: 14px;
}

.action-area {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
  background: white;
  border-top: 1px solid var(--border-color);
  max-width: 480px;
  margin: 0 auto;
}

.btn-secondary {
  width: 100%;
  padding: 14px 24px;
  background: var(--bg-color);
  color: var(--text-muted);
  border: none;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 500;
}

.btn-secondary:disabled {
  cursor: not-allowed;
}
</style>
