<template>
  <div class="home-container" :class="{ 'result-mode': showResult }">
    <!-- 初始状态 - 居中布局 -->
    <div v-if="!showResult" class="initial-mode">
      <!-- 顶部引导区 -->
      <div class="guide-section">
        <div class="guide-steps">
          <div class="step-item">
            <div class="step-number">1</div>
            <div class="step-content">
              <div class="step-title">{{ $t('home.guideStep1Title') }}</div>
              <div class="step-desc">{{ $t('home.guideStep1Desc') }}</div>
            </div>
          </div>
          <div class="step-item">
            <div class="step-number">2</div>
            <div class="step-content">
              <div class="step-title">{{ $t('home.guideStep2Title') }}</div>
              <div class="step-desc">{{ $t('home.guideStep2Desc') }}</div>
            </div>
          </div>
          <div class="step-item">
            <div class="step-number">3</div>
            <div class="step-content">
              <div class="step-title">{{ $t('home.guideStep3Title') }}</div>
              <div class="step-desc">{{ $t('home.guideStep3Desc') }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 上传卡片 -->
      <a-card class="upload-card-simple">
        <div class="card-header">
          <h3 class="card-title">
            <ImageIcon />
            <span>{{ $t('home.uploadTitle') }}</span>
          </h3>
          <p class="card-desc">{{ $t('home.uploadDesc') }}</p>
        </div>
        
        <div class="upload-area-wrapper">
          <a-upload
            :multiple="true"
            :file-list="fileList"
            :before-upload="beforeUpload"
            :custom-request="customUpload"
            accept="image/*"
            list-type="picture-card"
            class="upload-area"
          >
            <div class="upload-trigger">
              <UploadOutlined class="upload-icon" />
              <div class="upload-text">{{ $t('home.uploadArea') }}</div>
              <div class="upload-hint">{{ $t('home.uploadHint') }}</div>
            </div>
          </a-upload>
        </div>
        
        <div v-if="fileList.length > 0" class="upload-summary">
          <a-alert type="info" :closable="false" class="summary-alert">
            <template #message>
              <div class="summary-content">
                <FileTextOutlined />
                <span>{{ $t('home.selectedCount', { count: fileList.length }) }}</span>
                <span class="size-info">{{ $t('home.totalSize', { size: totalSizeDisplay }) }}</span>
              </div>
            </template>
          </a-alert>
        </div>
        
        <div class="upload-actions">
          <a-button
            type="primary"
            size="large"
            :loading="uploading"
            @click="handleUpload"
            class="btn-primary-gradient"
            :disabled="fileList.length === 0"
          >
            <RobotOutlined v-if="!uploading" />
            {{ uploading ? $t('home.recognizing') : $t('home.startRecognize') }}
          </a-button>
          <a-button size="large" @click="handleClear" class="btn-secondary" :disabled="uploading">
            <DeleteOutlined />
            {{ $t('home.clear') }}
          </a-button>
        </div>
      </a-card>
    </div>

    <!-- 识别结果模式 - 左右布局 -->
    <div v-else class="main-content">
      <!-- 左侧上传区域 -->
      <div class="left-section compressed">
        <a-card class="upload-card">
          <div class="card-header">
            <h3 class="card-title">
              <ImageIcon />
              <span>{{ $t('home.uploadTitle') }}</span>
            </h3>
          </div>
          
          <div class="upload-actions">
            <a-button
              type="primary"
              size="large"
              :loading="uploading"
              @click="handleUpload"
              class="btn-primary-gradient"
              :disabled="fileList.length === 0"
            >
              {{ uploading ? $t('home.recognizing') : $t('home.continueRecognize') }}
            </a-button>
            <a-button size="large" @click="handleClear" class="btn-secondary">
              <DeleteOutlined />
              {{ $t('home.uploadAgain') }}
            </a-button>
          </div>
          
          <div v-if="uploading" class="processing-indicator">
            <a-spin size="large" />
            <p>{{ $t('home.recognizing') }}</p>
          </div>
        </a-card>
      </div>

      <!-- 右侧识别结果区域 -->
      <div class="right-section active">
        <a-card class="result-card">
          <div class="result-header">
            <h3 class="result-title">
              <DatabaseOutlined />
              {{ $t('home.resultTitle') }}
            </h3>
            <span v-if="records.length > 0" class="record-count">{{ $t('home.recordsCount', { count: records.length }) }}</span>
          </div>
          
          <!-- 统计概览 -->
          <div v-if="records.length > 0" class="stats-overview">
            <div class="stat-item normal">
              <div class="stat-icon">✓</div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.normal }}</div>
                <div class="stat-label">{{ $t('home.statsNormal') }}</div>
              </div>
            </div>
            <div class="stat-item handwriting">
              <div class="stat-icon">✎</div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.handwriting }}</div>
                <div class="stat-label">{{ $t('home.statsHandwriting') }}</div>
              </div>
            </div>
            <div class="stat-item blurred">
              <div class="stat-icon">◐</div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.blurred }}</div>
                <div class="stat-label">{{ $t('home.statsBlurred') }}</div>
              </div>
            </div>
            <div class="stat-item night">
              <div class="stat-icon">🌙</div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.night }}</div>
                <div class="stat-label">{{ $t('home.statsNight') }}</div>
              </div>
            </div>
            <div class="stat-item absent">
              <div class="stat-icon">✗</div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.absent }}</div>
                <div class="stat-label">{{ $t('home.statsAbsent') }}</div>
              </div>
            </div>
            <div class="stat-item deleted">
              <div class="stat-icon">🗑</div>
              <div class="stat-info">
                <div class="stat-number">{{ stats.deleted }}</div>
                <div class="stat-label">{{ $t('home.statsDeleted') }}</div>
              </div>
            </div>
          </div>

          <div v-if="anomalyAlerts.length > 0" class="anomaly-alert-section">
            <a-alert type="warning" show-icon class="anomaly-alert">
              <template #message>
                <div class="anomaly-header" @click="showAnomalyDetail = !showAnomalyDetail">
                  <span class="anomaly-title">{{ $t('home.anomalyAlert', { count: anomalyAlerts.length }) }}</span>
                  <span class="anomaly-toggle">{{ showAnomalyDetail ? $t('home.collapse') : $t('home.expand') }}</span>
                </div>
              </template>
              <template #description>
                <div v-if="showAnomalyDetail" class="anomaly-detail-list">
                  <div v-for="(alert, idx) in anomalyAlerts" :key="idx" class="anomaly-item">
                    <span class="anomaly-index">#{{ alert.index + 1 }}</span>
                    <span class="anomaly-name">{{ alert.name }}</span>
                    <span class="anomaly-reasons">
                      <a-tag v-for="(reason, rIdx) in alert.reasons" :key="rIdx" color="error" size="small">{{ reason }}</a-tag>
                    </span>
                  </div>
                </div>
              </template>
            </a-alert>
          </div>

          <!-- 识别数据表格 -->
          <div v-if="records.length > 0" class="records-wrapper">
            <a-table 
              :columns="columns" 
              :data-source="records" 
              :pagination="false" 
              :scroll="{ y: 'calc(100vh - 380px)' }"
              :size="'small'"
              class="records-table"
              :row-class-name="getRowClassName"
            >
              <template #bodyCell="{ column, record, index }">
                <template v-if="column.key === 'rowType'">
                  <a-tag :color="getRowTypeColor(record)" class="mark-tag">{{ getRowTypeLabel(record) }}</a-tag>
                </template>
                <template v-if="column.key === 'SmartMark'">
                  <a-tag :color="getMarkColor(record.SmartMark)" class="mark-tag">
                    {{ record.SmartMark }}
                  </a-tag>
                </template>
                <template v-if="column.key === 'action'">
                  <a-tooltip :title="record.isDeleted ? $t('common.undo') : $t('common.delete')">
                    <a-button 
                      type="text"
                      :danger="!record.isDeleted"
                      shape="circle"
                      size="small" 
                      @click="deleteRecord(index)"
                    >
                      <DeleteOutlined v-if="!record.isDeleted" />
                      <UndoOutlined v-else />
                    </a-button>
                  </a-tooltip>
                </template>
              </template>
            </a-table>
          </div>

          <!-- 空状态 -->
          <div v-else class="empty-state">
            <div class="empty-icon">📋</div>
            <p>{{ $t('home.noRecords') }}</p>
          </div>
          
          <div v-if="records.length > 0 && !uploading" class="action-buttons">
            <a-button type="success" size="large" :loading="submitting" @click="handleConfirm">
              <CheckCircleOutlined />
              {{ $t('home.confirmAndEdit') }}
            </a-button>
          </div>
        </a-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, h, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { 
  LoadingOutlined, 
  CheckCircleOutlined, 
  UploadOutlined,
  DeleteOutlined,
  UndoOutlined,
  FileTextOutlined,
  DatabaseOutlined,
} from '@ant-design/icons-vue'
import { compressImage, getImageHash, getFileSizeDisplay } from '@/utils/image'

const router = useRouter()
const { t } = useI18n()

const fileList = ref([])
const uploading = ref(false)
const submitting = ref(false)
const records = ref([])
const currentTaskId = ref(null)
const processedHashes = ref(new Set())
const showResult = ref(false)

const resetState = () => {
  fileList.value = []
  uploading.value = false
  submitting.value = false
  records.value = []
  currentTaskId.value = null
  processedHashes.value = new Set()
  showResult.value = false
}

onMounted(() => {
  resetState()
})

// 统计计算
const stats = computed(() => {
  const result = {
    normal: 0,
    handwriting: 0,
    blurred: 0,
    night: 0,
    absent: 0,
    deleted: 0,
  }
  
  records.value.forEach(record => {
    if (record.isDeleted) {
      result.deleted++
      return
    }
    
    const anomalies = record.anomalies || []
    const mark = record.SmartMark || ''
    
    // 正常 - 根据 anomalies 判断
    if (anomalies.length === 0) {
      result.normal++
    }
    
    // 手写 - 根据工号和姓名判断
    const noValue = record.NO || ''
    const nomPrenomValue = record.NOM_PRENOM || ''
    if (noValue.includes('手写') || nomPrenomValue.includes('手写')) {
      result.handwriting++
    }
    
    if (mark.includes('模糊')) result.blurred++
    if (mark.includes('夜班')) result.night++
    if (mark.includes('未出勤')) result.absent++
  })
  
  return result
})

const isAbsentRow = (record) => {
  const mark = record?.SmartMark || ''
  return mark.includes('未出勤') && !record?._restored
}

const cellStyle = (record, index) => {
  if (!record) return {}
  if (record?.isDeleted || isAbsentRow(record)) {
    return {
      style: {
        backgroundColor: '#fff1f0',
        color: '#b3736e',
        fontStyle: 'italic',
        textDecoration: 'line-through',
        textDecorationColor: '#d4a5a0',
      }
    }
  }
  if ((record?.SmartMark || '').includes('模糊')) {
    return {
      style: {
        backgroundColor: '#fefce8',
      }
    }
  }
  return {}
}

const columns = [
  { title: '识别结果', key: 'rowType', width: 85, customCell: cellStyle },
  { title: '工号', dataIndex: 'NO', key: 'NO', width: 70, ellipsis: true, customCell: cellStyle },
  { title: '姓名', dataIndex: 'NOM_PRENOM', key: 'NOM_PRENOM', width: 90, ellipsis: true, customCell: cellStyle },
  { title: '日期', dataIndex: 'Date', key: 'Date', width: 90, customCell: cellStyle },
  { title: '到达', dataIndex: 'ARRIVEE', key: 'ARRIVEE', width: 60, customCell: cellStyle },
  { title: '离开', dataIndex: 'DEPAR', key: 'DEPAR', width: 60, customCell: cellStyle },
  { title: '休息', dataIndex: 'PAUSE', key: 'PAUSE', width: 50, customCell: cellStyle },
  { title: '标记', dataIndex: 'SmartMark', key: 'SmartMark', width: 90, customCell: cellStyle },
  { title: '操作', key: 'action', width: 50, fixed: 'right', customCell: cellStyle },
]

const totalSizeDisplay = computed(() => {
  const total = fileList.value.reduce((sum, file) => sum + (file.size || 0), 0)
  return getFileSizeDisplay(total)
})

const ImageIcon = {
  render() {
    return h('svg', {
      width: '18',
      height: '18',
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      strokeWidth: '2'
    }, [
      h('rect', { x: '3', y: '3', width: '18', height: '18', rx: '2', ry: '2' }),
      h('circle', { cx: '8.5', cy: '8.5', r: '1.5', fill: 'currentColor' }),
      h('polyline', { points: '21 15 16 10 5 21' })
    ])
  }
}

const beforeUpload = async (file) => {
  const hash = await getImageHash(file)
  
  if (processedHashes.value.has(hash)) {
    message.warning(t('home.duplicateImage'))
    return false
  }
  
  const compressedFile = await compressImage(file, {
    maxSizeKB: 2000,
    maxWidth: 1600,
    maxHeight: 1600,
    quality: 0.85
  })
  
  processedHashes.value.add(hash)
  
  const newFile = {
    uid: file.uid,
    name: file.name,
    size: compressedFile.size,
    raw: compressedFile,
    status: 'done',
  }
  
  fileList.value.push(newFile)
  
  return false
}

const customUpload = () => {}

const handleUpload = async () => {
  if (fileList.value.length === 0) {
    message.warning(t('home.selectAtLeastOne'))
    return
  }
  
  uploading.value = true
  showResult.value = true
  records.value = []
  let isComplete = false
  let sharedTaskId = null
  let completedCount = 0
  const imagePreviewUrls = []
  
  try {
    for (const file of fileList.value) {
      const formData = new FormData()
      formData.append('image', file.raw)
      if (sharedTaskId) {
        formData.append('taskId', sharedTaskId)
      }
      
      const token = localStorage.getItem('attendance_token')
      
      const response = await fetch('/api/local/upload-stream', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        body: formData,
      })
      
      if (!response.ok) {
        const errorText = await response.text()
        message.error(t('home.uploadFailed', { status: response.status, statusText: response.statusText }))
        continue
      }
      
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      
      let currentEvent = null
      let currentData = null
      let buffer = ''
      let thisFileComplete = false
      
      const timeoutId = setTimeout(() => {
        if (!thisFileComplete) {
          thisFileComplete = true
          reader.cancel()
        }
      }, 120000)
      
      try {
        while (true) {
          const { done, value } = await reader.read()
          
          if (done || thisFileComplete) {
            break
          }
          
          const text = decoder.decode(value, { stream: true })
          buffer += text
          
          let lines = buffer.split('\n')
          buffer = lines.pop()
          
          for (const line of lines) {
            const trimmedLine = line.trim()
            
            if (trimmedLine.startsWith('event:')) {
              currentEvent = trimmedLine.slice(6).trim()
            } else if (trimmedLine.startsWith('data:')) {
              const newData = trimmedLine.slice(5).trim()
              currentData = currentData ? currentData + newData : newData
            } else if (!trimmedLine && (currentEvent || currentData)) {
              if (currentData) {
                try {
                  const data = JSON.parse(currentData)
                  
                  if (currentEvent === 'start') {
                    if (!sharedTaskId) {
                      sharedTaskId = data.taskId
                    }
                    if (data.imagePreviewUrl) {
                      imagePreviewUrls.push(data.imagePreviewUrl)
                    }
                  } else if (currentEvent === 'record') {
                    if (data.record) {
                      const record = {
                        ...data.record,
                        isDeleted: false,
                      }
                      records.value.push(record)
                    }
                  } else if (currentEvent === 'complete') {
                    thisFileComplete = true
                    completedCount++
                  } else if (currentEvent === 'error') {
                    message.error(data.message || '识别出错')
                    thisFileComplete = true
                    completedCount++
                  } else if (currentEvent === 'info') {
                  } else if (!currentEvent) {
                    if (data.taskId && !data.record && data.rowCount !== undefined) {
                      thisFileComplete = true
                      completedCount++
                    } else if (data.record) {
                      const record = {
                        ...data.record,
                        isDeleted: false,
                      }
                      records.value.push(record)
                    }
                  }
                } catch (e) {
                  console.warn('Failed to parse SSE data:', e)
                }
              }
              currentEvent = null
              currentData = null
            }
          }
        }
      } finally {
        clearTimeout(timeoutId)
        reader.cancel()
      }
    }
    
    if (records.value.length > 0) {
      message.success(t('home.recognizeSuccess', { count: records.value.length }))
      if (sharedTaskId) {
        router.push(`/tasks/${sharedTaskId}`)
      }
    } else {
      message.warning(t('home.noRecordsFound'))
    }
  } catch (error) {
    console.error('Upload error:', error)
    if (error.name !== 'AbortError') {
      message.error('上传失败: ' + (error.message || '未知错误'))
    }
  } finally {
    uploading.value = false
    isComplete = true
  }
}

const handleConfirm = () => {
  if (!currentTaskId.value) {
    message.warning(t('home.selectAtLeastOne'))
    return
  }
  router.push(`/tasks/${currentTaskId.value}`)
}

const handleClear = () => {
  resetState()
}

const deleteRecord = (index) => {
  const record = records.value[index]
  if (record) {
    record.isDeleted = !record.isDeleted
    if (record.isDeleted) {
      record._prevMark = record.SmartMark
      record.SmartMark = '已删除'
    } else {
      if (record._prevMark) {
        record.SmartMark = record._prevMark
        delete record._prevMark
      } else {
        record.SmartMark = '正常'
      }
    }
  }
}

const getRowTypeLabel = (record) => {
  if (record?.isDeleted) return '已删除'
  const mark = record?.SmartMark || ''
  if (mark.includes('未出勤')) return '未出勤'
  if (mark.includes('模糊')) return '模糊'
  return '正常'
}

const getRowTypeColor = (record) => {
  if (record?.isDeleted) return 'default'
  const mark = record?.SmartMark || ''
  if (mark.includes('未出勤')) return 'error'
  if (mark.includes('模糊')) return 'warning'
  return 'success'
}

const showAnomalyDetail = ref(true)

const anomalyAlerts = computed(() => {
  return records.value
    .map((record, index) => {
      if (record.isDeleted) return null
      const anomalies = record.anomalies || []
      const mark = record.SmartMark || ''
      const reasons = []
      
      if (anomalies.length > 0) {
        reasons.push(...anomalies)
      }
      if (mark.includes('模糊')) reasons.push('内容模糊')
      if (mark.includes('手写')) reasons.push('手写内容')
      if (mark.includes('未出勤')) reasons.push('未出勤')
      
      if (reasons.length === 0) return null
      const no = record.NO || '?'
      const name = record.NOM_PRENOM || '?'
      return {
        index,
        name: no + ' - ' + name,
        reasons: [...new Set(reasons)]
      }
    })
    .filter(Boolean)
})

const getRowClassName = (record, index) => {
  if (!record || record?.isDeleted) return 'deleted-row'
  const mark = record?.SmartMark || ''
  if (mark.includes('未出勤')) return 'absent-row'
  if (mark.includes('模糊')) return 'blurred-row'
  return ''
}

const getMarkColor = (mark) => {
  if (!mark) return 'default'
  if (mark.includes('正常')) return 'success'
  if (mark.includes('手写')) return 'processing'
  if (mark.includes('模糊')) return 'warning'
  if (mark.includes('夜班')) return 'purple'
  if (mark.includes('未出勤')) return 'error'
  return 'default'
}
</script>

<style lang="scss" scoped>
.home-container {
  padding: 20px;
  
  &.result-mode {
    height: calc(100vh - 64px);
    padding: 20px;
  }
}

// 初始模式
.initial-mode {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.guide-section {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 2px rgba(31, 35, 41, 0.06);
  
  .guide-steps {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
    
    .step-item {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 12px;
      background: #FAFBFC;
      border-radius: 8px;
      border: 1px solid #F0F1F5;
      transition: all 0.2s ease;
      
      &:hover {
        border-color: #5B8FF9;
        background: #F5F9FF;
      }
      
      .step-number {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        background: linear-gradient(135deg, #5B8FF9 0%, #7B61FF 100%);
        color: #fff;
        font-size: 12px;
        font-weight: 600;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      
      .step-content {
        flex: 1;
        
        .step-title {
          font-size: 13px;
          font-weight: 600;
          color: #1F2329;
          margin-bottom: 2px;
        }
        
        .step-desc {
          font-size: 12px;
          color: #8F959E;
          line-height: 1.4;
        }
      }
    }
  }
}

.upload-card-simple {
  background: #ffffff;
  border-radius: 12px;
  border: none;
  box-shadow: 0 1px 2px rgba(31, 35, 41, 0.06);
  
  :deep(.ant-card-body) {
    padding: 20px;
  }
  
  .card-header {
    padding-bottom: 16px;
    border-bottom: 1px solid #F0F1F5;
    margin-bottom: 20px;
    
    .card-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 15px;
      font-weight: 600;
      color: #1F2329;
      margin: 0 0 4px;
      
      svg {
        color: #5B8FF9;
      }
    }
    
    .card-desc {
      margin: 0;
      font-size: 13px;
      color: #8F959E;
    }
  }
  
  .upload-area-wrapper {
    .upload-area {
      width: 100%;
      
      :deep(.ant-upload-select) {
        width: 100% !important;
        height: 140px !important;
        margin: 0 !important;
        border: 2px dashed #E5E6EB !important;
        border-radius: 8px !important;
        background: #FAFBFC !important;
        transition: all 0.3s ease !important;
        
        &:hover {
          border-color: #5B8FF9 !important;
          background: #F5F9FF !important;
        }
      }
      
      .upload-trigger {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        height: 100%;
        gap: 6px;
        
        .upload-icon {
          font-size: 32px;
          color: #8F959E;
        }
        
        .upload-text {
          font-size: 14px;
          font-weight: 500;
          color: #1F2329;
        }
        
        .upload-hint {
          font-size: 12px;
          color: #8F959E;
        }
      }
    }
  }
  
  .upload-summary {
    margin-top: 16px;
    
    .summary-alert {
      border: none;
      background: #F5F9FF;
      border-radius: 6px;
      
      .summary-content {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        color: #5B8FF9;
        
        .size-info {
          margin-left: auto;
          color: #8F959E;
        }
      }
    }
  }
  
  .upload-actions {
    margin-top: 20px;
    display: flex;
    gap: 12px;
    
    .btn-primary-gradient {
      flex: 1;
      height: 40px;
      border-radius: 6px;
      font-weight: 500;
    }
    
    .btn-secondary {
      width: 100px;
      height: 40px;
      border-radius: 6px;
    }
  }
}

// 结果模式
.main-content {
  display: flex;
  gap: 20px;
  height: 100%;
}

// 左侧上传区域
.left-section {
  width: 380px;
  flex-shrink: 0;
  transition: width 0.3s ease;
  
  &.compressed {
    width: 300px;
    
    .upload-card {
      .card-title {
        font-size: 14px;
      }
      
      .upload-summary {
        .summary-content {
          font-size: 12px;
        }
      }
    }
  }
  
  .upload-card {
    height: 100%;
    display: flex;
    flex-direction: column;
    
    :deep(.ant-card-body) {
      flex: 1;
      display: flex;
      flex-direction: column;
      padding: 16px;
    }
    
    .card-header {
      margin-bottom: 16px;
      
      .card-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 15px;
        font-weight: 600;
        color: #1F2329;
        margin: 0 0 4px;
        
        svg {
          color: #5B8FF9;
        }
      }
      
      .card-desc {
        margin: 0;
        font-size: 12px;
        color: #8F959E;
      }
    }
    
    .upload-area-wrapper {
      flex-shrink: 0;
      margin-bottom: 12px;
      
      .upload-area {
        width: 100%;
        
        :deep(.ant-upload-select) {
          width: 100% !important;
          height: 120px !important;
          margin: 0 !important;
          border: 2px dashed #E5E6EB !important;
          border-radius: 8px !important;
          background: #FAFBFC !important;
          transition: all 0.3s ease !important;
          
          &:hover {
            border-color: #5B8FF9 !important;
            background: #F5F9FF !important;
          }
        }
        
        .upload-trigger {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          height: 100%;
          gap: 4px;
          
          .upload-icon {
            font-size: 28px;
            color: #8F959E;
          }
          
          .upload-text {
            font-size: 13px;
            font-weight: 500;
            color: #1F2329;
          }
          
          .upload-hint {
            font-size: 11px;
            color: #8F959E;
          }
        }
      }
    }
    
    .upload-summary {
      flex-shrink: 0;
      margin-bottom: 12px;
      
      .summary-content {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 13px;
        color: #5B8FF9;
        background: #F5F9FF;
        padding: 10px 12px;
        border-radius: 6px;
      }
    }
    
    .upload-actions {
      flex-shrink: 0;
      display: flex;
      gap: 10px;
      
      .btn-primary-gradient {
        flex: 1;
        height: 38px;
        border-radius: 6px;
        font-weight: 500;
      }
      
      .btn-secondary {
        height: 38px;
        border-radius: 6px;
      }
    }
    
    .processing-indicator {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 20px;
      background: linear-gradient(135deg, #F5F9FF 0%, #EEF5FF 100%);
      border-radius: 8px;
      margin-top: 12px;
      
      p {
        margin: 12px 0 0;
        color: #4E5969;
        font-size: 12px;
      }
    }
  }
}

// 右侧结果区域
.right-section {
  flex: 0 0 0;
  opacity: 0;
  transform: translateX(20px);
  transition: all 0.3s ease;
  pointer-events: none;
  overflow: hidden;
  
  &.active {
    flex: 1;
    opacity: 1;
    transform: translateX(0);
    pointer-events: auto;
  }
  
  .result-card {
    height: 100%;
    display: flex;
    flex-direction: column;
    
    :deep(.ant-card-body) {
      flex: 1;
      display: flex;
      flex-direction: column;
      padding: 16px;
      overflow: hidden;
    }
    
    .result-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid #F0F1F5;
      
      .result-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 15px;
        font-weight: 600;
        color: #1F2329;
        margin: 0;
        
        svg {
          color: #5B8FF9;
        }
      }
      
      .record-count {
        font-size: 13px;
        color: #8F959E;
        background: #F5F7FA;
        padding: 4px 10px;
        border-radius: 12px;
      }
    }
    
    // 统计概览
    .stats-overview {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 10px;
      margin-bottom: 16px;
      
      .stat-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        border-radius: 8px;
        background: #F5F7FA;
        transition: all 0.2s ease;
        
        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
        }
        
        &.normal {
          background: linear-gradient(135deg, #E8FFEA 0%, #D3F4D6 100%);
          
          .stat-icon {
            color: #00B42A;
          }
        }
        
        &.handwriting {
          background: linear-gradient(135deg, #E8F3FF 0%, #D3E5FF 100%);
          
          .stat-icon {
            color: #1677FF;
          }
        }
        
        &.blurred {
          background: linear-gradient(135deg, #FFF7E8 0%, #FFECC7 100%);
          
          .stat-icon {
            color: #FF7D00;
          }
        }
        
        &.night {
          background: linear-gradient(135deg, #F5F0FF 0%, #E7DEFF 100%);
          
          .stat-icon {
            color: #722ED1;
          }
        }
        
        &.absent {
          background: linear-gradient(135deg, #FFF0F0 0%, #FFD8D8 100%);
          
          .stat-icon {
            color: #F53F3F;
          }
        }
        
        &.deleted {
          background: linear-gradient(135deg, #F5F5F5 0%, #E8E8E8 100%);
          
          .stat-icon {
            color: #86909C;
          }
        }
        
        .stat-icon {
          font-size: 20px;
          line-height: 1;
        }
        
        .stat-info {
          flex: 1;
          
          .stat-number {
            font-size: 18px;
            font-weight: 700;
            color: #1F2329;
            line-height: 1.2;
          }
          
          .stat-label {
            font-size: 11px;
            color: #8F959E;
            line-height: 1.2;
          }
        }
      }
    }
    
    .anomaly-alert-section {
      margin-bottom: 16px;
      
      .anomaly-alert {
        border-radius: 8px;
        border: 1px solid #ffe58f;
        background: #fffbe6;
      }
      
      .anomaly-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        cursor: pointer;
        
        .anomaly-title {
          font-weight: 600;
          font-size: 13px;
          color: #d48806;
        }
        
        .anomaly-toggle {
          font-size: 12px;
          color: #8c8c8c;
        }
      }
      
      .anomaly-detail-list {
        max-height: 200px;
        overflow-y: auto;
        margin-top: 8px;
        
        .anomaly-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 0;
          border-bottom: 1px solid #fff1b8;
          font-size: 12px;
          
          &:last-child {
            border-bottom: none;
          }
          
          .anomaly-index {
            color: #8c8c8c;
            font-weight: 500;
            min-width: 30px;
          }
          
          .anomaly-name {
            color: #434343;
            font-weight: 500;
            min-width: 120px;
          }
          
          .anomaly-reasons {
            display: flex;
            gap: 4px;
            flex-wrap: wrap;
          }
        }
      }
    }
    
    // 记录表格
    .records-wrapper {
      flex: 1;
      overflow: hidden;
      
      .records-table {
        height: 100%;
        
        :deep(.ant-table) {
          border-radius: 8px;
          overflow: hidden;
          height: 100%;
        }
        
        :deep(.ant-table-container) {
          height: 100%;
        }
        
        :deep(.ant-table-body) {
          &::-webkit-scrollbar {
            width: 6px;
          }
          
          &::-webkit-scrollbar-thumb {
            background: #D9D9D9;
            border-radius: 3px;
          }
        }
        
        :deep(.ant-table-thead > tr > th) {
          background: #FAFBFC;
          border-bottom: 1px solid #F0F1F5;
          font-size: 11px;
          font-weight: 600;
          color: #646A73;
          padding: 8px 10px;
        }
        
        :deep(.ant-table-tbody > tr:hover > td) {
          background: #FAFBFC;
        }
        
        :deep(.ant-table-tbody > tr > td) {
          padding: 8px 10px;
          font-size: 11px;
          color: #1F2329;
          border-bottom: 1px solid #F5F7FA;
        }
        
        .mark-tag {
          border-radius: 4px;
          font-size: 10px;
          padding: 1px 6px;
        }
      }
    }
    
    // 空状态
    .empty-state {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      color: #8F959E;
      
      .empty-icon {
        font-size: 64px;
        margin-bottom: 16px;
        opacity: 0.5;
      }
      
      p {
        font-size: 14px;
        margin: 0;
      }
    }
    
    // 操作按钮
    .action-buttons {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid #F0F1F5;
      text-align: center;
      
      :deep(.ant-btn-success) {
        height: 40px;
        padding: 0 32px;
        border-radius: 6px;
        font-weight: 500;
      }
    }
  }
}
</style>

<style lang="scss">
.records-table {
  .ant-table-tbody > tr.deleted-row:hover > td,
  .ant-table-tbody > tr.absent-row:hover > td {
    background-color: #fff1f0 !important;
  }
}
</style>
