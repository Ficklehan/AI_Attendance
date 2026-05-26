<template>
  <div class="task-edit-container">
    <a-card :loading="loading" :bordered="false" class="edit-card">
      <template #extra>
        <a-button @click="$router.back()">
          <template #icon><UndoOutlined /></template>
          {{ $t('common.back') }}
        </a-button>
      </template>
      
      <div class="page-header">
        <div class="header-left">
          <h3 class="page-title">{{ $t('taskEdit.title') }}</h3>
          <a-tag color="blue" class="task-id-tag">{{ taskId }}</a-tag>
          <a-tag v-if="task?.status === 'cancelled'" color="default" class="status-tag">{{ $t('tasks.statusCancelled') }}</a-tag>
        </div>
        <div class="header-right">
          <span class="record-count">{{ $t('tasks.totalRecords', { total: records.length }) }}</span>
          <a-button 
            type="default" 
            @click="handleReupload"
            :disabled="task?.status === 'cancelled'"
            :loading="cancelling"
            class="reupload-btn"
          >
            <template #icon><UploadOutlined /></template>
            {{ $t('taskEdit.reupload') }}
          </a-button>
        </div>
      </div>
      
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
      
      <a-tabs v-model:active-key="activeTab" class="edit-tabs">
        <a-tab-pane key="edit" :tab="$t('taskEdit.editData')">
          <div v-if="anomalyAlerts.length > 0" class="anomaly-hint">
            <div class="anomaly-summary">
              <ExclamationCircleOutlined class="anomaly-icon" />
              <span class="anomaly-text">{{ $t('home.anomalyAlert', { count: anomalyAlerts.length }) }}</span>
              <a-button type="link" size="small" @click="showAnomalyDetail = !showAnomalyDetail" class="anomaly-toggle">
                {{ showAnomalyDetail ? $t('home.collapse') : $t('home.expand') }}
              </a-button>
            </div>
            <div v-if="showAnomalyDetail" class="anomaly-detail-list">
              <div v-for="(alert, idx) in anomalyAlerts" :key="idx" class="anomaly-item">
                <span class="anomaly-index">{{ alert.index + 1 }}</span>
                <span class="anomaly-name">{{ alert.name }}</span>
                <span class="anomaly-reasons">
                  <a-tag v-for="(reason, rIdx) in alert.reasons" :key="rIdx" :color="getAnomalyTagColor(reason)" size="small">{{ reason }}</a-tag>
                </span>
              </div>
            </div>
          </div>

          <a-table 
            :columns="columns" 
            :data-source="records" 
            :pagination="false"
            :scroll="{ x: 1100, y: 500 }"
            :row-class-name="getRowClassName"
            size="small"
            class="edit-table"
          >
            <template #bodyCell="{ column, record, index }">
              <template v-if="column.key === 'rowType'">
                <span :class="['row-type-dot', getRowTypeDotClass(record)]"></span>
                <span class="row-type-label">{{ getRowTypeLabel(record) }}</span>
              </template>
              <template v-if="column.key === 'NO'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.NO" size="small" :class="{ 'required-empty': !record.NO }" :bordered="false" />
                <span v-else class="cell-text">{{ record.NO }}</span>
              </template>
              <template v-if="column.key === 'NOM_PRENOM'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.NOM_PRENOM" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.NOM_PRENOM }}</span>
              </template>
              <template v-if="column.key === 'AGENCE_INTERIMAIRE'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.AGENCE_INTERIMAIRE" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.AGENCE_INTERIMAIRE }}</span>
              </template>
              <template v-if="column.key === 'HORAIRES_DU_TRAVAIL'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.HORAIRES_DU_TRAVAIL" size="small" :bordered="false" />
                <span v-else class="cell-text">{{ record.HORAIRES_DU_TRAVAIL }}</span>
              </template>
              <template v-if="column.key === 'Date'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.Date" size="small" :class="{ 'required-empty': !record.Date }" :bordered="false" />
                <span v-else class="cell-text">{{ record.Date }}</span>
              </template>
              <template v-if="column.key === 'ARRIVEE'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.ARRIVEE" size="small" :class="{ 'required-empty': !record.ARRIVEE }" :bordered="false" />
                <span v-else class="cell-text">{{ record.ARRIVEE }}</span>
              </template>
              <template v-if="column.key === 'DEPAR'">
                <a-input v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.DEPAR" size="small" :class="{ 'required-empty': !record.DEPAR }" :bordered="false" />
                <span v-else class="cell-text">{{ record.DEPAR }}</span>
              </template>
              <template v-if="column.key === 'PAUSE'">
                <a-input-number v-if="!record.isDeleted && !isAbsentRow(record)" v-model:value="record.PAUSE" size="small" :class="{ 'required-empty': record.PAUSE === null || record.PAUSE === undefined || record.PAUSE === '' }" :bordered="false" :controls="false" style="width: 100%" />
                <span v-else class="cell-text">{{ record.PAUSE }}</span>
              </template>
              <template v-if="column.key === 'SmartMark'">
                <a-tag v-if="record.isDeleted" color="default" class="mark-tag">{{ $t('home.statsDeleted') }}</a-tag>
                <a-tag v-else-if="isAbsentRow(record)" color="error" class="mark-tag">{{ $t('home.statsAbsent') }}</a-tag>
                <a-tag v-else :color="getMarkColor(record.SmartMark)" class="mark-tag">{{ record.SmartMark || '-' }}</a-tag>
              </template>
              <template v-if="column.key === 'workHours'">
                <span class="work-hours">{{ calculateWorkHours(record) }}</span>
              </template>
              <template v-if="column.key === 'action'">
                <a-tooltip :title="(record?.isDeleted || isAbsentRow(record)) ? $t('taskEdit.restore') : $t('common.delete')">
                  <a-button 
                    v-if="record?.isDeleted || isAbsentRow(record)"
                    type="link"
                    size="small"
                    @click="toggleDelete(record, index)"
                    class="action-btn restore-btn"
                  >
                    <template #icon><UndoOutlined /></template>
                  </a-button>
                  <a-button 
                    v-else
                    type="link"
                    size="small"
                    danger
                    @click="toggleDelete(record, index)"
                    class="action-btn delete-btn"
                  >
                    <template #icon><DeleteOutlined /></template>
                  </a-button>
                </a-tooltip>
              </template>
            </template>
          </a-table>
          
          <div class="action-bar">
            <a-button type="primary" :loading="submitting" @click="handleSubmit" size="large">
              {{ $t('taskEdit.submitConfirm') }}
            </a-button>
            <a-button @click="$router.back()" size="large">{{ $t('common.cancel') }}</a-button>
          </div>
        </a-tab-pane>
        
        <a-tab-pane key="raw" :tab="$t('taskEdit.originalImage')">
          <div v-if="imageUrls.length > 0" class="image-preview-section">
            <div class="image-preview-header">
              <span class="image-preview-title">{{ $t('taskEdit.originalImage') }}（{{ imageUrls.length }}{{ $t('tasks.images') }}）</span>
            </div>
            <div class="image-preview-list">
              <div v-for="(url, idx) in imageUrls" :key="idx" class="image-preview-item">
                <div class="image-card" @click="previewImage(url)">
                  <FileImageOutlined class="image-icon" />
                  <div class="image-info">
                    <div class="image-name">{{ getFileName(url) }}</div>
                    <div class="image-hint">{{ $t('taskEdit.clickToView') }}</div>
                  </div>
                  <EyeOutlined class="preview-icon" />
                </div>
              </div>
            </div>
          </div>
          <a-empty v-else :description="$t('taskEdit.noOriginalImages')" />
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
  
  <a-modal
    v-model:open="previewVisible"
    :footer="null"
    :width="modalWidth"
    :body-style="{ padding: '12px', overflow: 'hidden' }"
    centered
    class="image-preview-modal"
    :mask-closable="false"
    @open-change="handleModalOpenChange"
  >
    <div class="preview-modal-content">
      <div v-if="previewImagesList.length > 1" class="preview-nav-bar">
        <a-button 
          class="preview-nav-btn preview-nav-prev"
          @click="previewPrev"
          :disabled="previewCurrentIndex === 0"
        >
          <LeftOutlined />
          {{ $t('tasks.previous') }}
        </a-button>
        
        <span class="preview-index-text">{{ previewCurrentIndex + 1 }} / {{ previewImagesList.length }}</span>
        
        <a-button 
          class="preview-nav-btn preview-nav-next"
          @click="previewNext"
          :disabled="previewCurrentIndex === previewImagesList.length - 1"
        >
          {{ $t('tasks.next') }}
          <RightOutlined />
        </a-button>
      </div>
      
      <div class="preview-image-wrapper" v-if="previewImageUrl">
        <img 
          :src="previewImageUrl" 
          class="preview-image"
          @error="handleImageError"
          @load="handleImageLoad"
          :alt="$t('tasks.imagePreview')"
        />
      </div>
      <a-empty v-else :description="$t('taskEdit.imageLoadFailed')" />
    </div>
  </a-modal>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal as aModal } from 'ant-design-vue'
import { DeleteOutlined, UndoOutlined, ExclamationCircleOutlined, FileImageOutlined, EyeOutlined, UploadOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { getTaskDetail, confirmTask, cancelTask } from '@/api/task'
import axios from 'axios'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const taskId = computed(() => route.params.taskId)
const activeTab = ref('edit')
const loading = ref(false)
const submitting = ref(false)
const cancelling = ref(false)
const records = ref([])
const rawData = ref('')
const showAnomalyDetail = ref(true)
const imageUrls = ref([])
const previewVisible = ref(false)
const previewImageUrl = ref('')
const previewImagesList = ref([])
const previewCurrentIndex = ref(0)
const task = ref(null)
const modalWidth = ref(900)
const imageNaturalWidth = ref(0)
const imageNaturalHeight = ref(0)

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
    // 统计已删除的记录
    if (record.isDeleted) {
      result.deleted++
      return
    }
    
    // 统计识别结果 - 根据anomalies字段来判断
    const anomalies = record.anomalies || []
    const mark = record.SmartMark || ''
    
    // 如果anomalies为空，说明识别时正常
    if (anomalies.length === 0) {
      result.normal++
    }
    
    // 手写根据SmartMark中标记为手写的统计
    if (mark.includes('手写')) result.handwriting++
    
    // 其他类型根据SmartMark来判断
    if (mark.includes('模糊')) result.blurred++
    if (mark.includes('夜班')) result.night++
    if (mark.includes('未出勤')) result.absent++
  })
  
  return result
})

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
  if (hasRequiredMissing(record)) {
    return {
      style: {
        backgroundColor: '#fff8f0',
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
  { title: '识别结果', key: 'rowType', width: 80, fixed: 'left', customCell: cellStyle },
  { title: t('taskEdit.workerNumber'), dataIndex: 'NO', key: 'NO', width: 90, customCell: cellStyle },
  { title: t('taskEdit.name'), dataIndex: 'NOM_PRENOM', key: 'NOM_PRENOM', width: 100, customCell: cellStyle },
  { title: t('taskEdit.agency'), dataIndex: 'AGENCE_INTERIMAIRE', key: 'AGENCE_INTERIMAIRE', width: 120, customCell: cellStyle },
  { title: t('taskEdit.shift'), dataIndex: 'HORAIRES_DU_TRAVAIL', key: 'HORAIRES_DU_TRAVAIL', width: 90, customCell: cellStyle },
  { title: t('taskEdit.date'), dataIndex: 'Date', key: 'Date', width: 100, customCell: cellStyle },
  { title: t('taskEdit.arrival'), dataIndex: 'ARRIVEE', key: 'ARRIVEE', width: 80, customCell: cellStyle },
  { title: t('taskEdit.departure'), dataIndex: 'DEPAR', key: 'DEPAR', width: 80, customCell: cellStyle },
  { title: t('taskEdit.breakTime'), dataIndex: 'PAUSE', key: 'PAUSE', width: 80, customCell: cellStyle },
  { title: t('taskEdit.mark'), dataIndex: 'SmartMark', key: 'SmartMark', width: 90, customCell: cellStyle },
  { title: t('taskEdit.workHours'), key: 'workHours', width: 80, customCell: cellStyle },
  { title: t('taskEdit.action'), key: 'action', width: 50, fixed: 'right', align: 'center', customCell: cellStyle },
]

const loadTask = async () => {
  loading.value = true
  try {
    const response = await getTaskDetail(taskId.value)
    task.value = response.data
    
    if (task.value.rawData) {
      const parsedRecords = JSON.parse(task.value.rawData)
      records.value = parsedRecords.map(record => ({
        ...record,
        isDeleted: record.isDeleted || false
      }))
    }
    rawData.value = task.value.aiRawOutput || ''
    
    if (task.value.imageUrls) {
      try {
        const urls = typeof task.value.imageUrls === 'string' ? JSON.parse(task.value.imageUrls) : task.value.imageUrls
        previewImagesList.value = urls.map(url => {
          if (url.startsWith('http') || url.startsWith('/api')) {
            return url
          }
          return `/api/local/image/${url}`
        })
        imageUrls.value = previewImagesList.value
      } catch (e) {
        console.error('Failed to parse imageUrls:', e)
        imageUrls.value = []
        previewImagesList.value = []
      }
    }
  } catch (error) {
    message.error(t('taskEdit.loadingFailed'))
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleReupload = async () => {
  try {
    await aModal.confirm({
      title: t('taskEdit.confirmAgain'),
      content: () => h('div', { innerHTML: t('taskEdit.confirmAgainDesc') }),
      okText: t('taskEdit.cancelTask'),
      cancelText: t('common.cancel'),
      okType: 'danger',
      maskClosable: false,
    })
    
    cancelling.value = true
    await cancelTask(taskId.value)
    message.success(t('taskEdit.cancelSuccess'))
    
    setTimeout(() => {
      router.push('/')
    }, 1000)
  } catch (error) {
    if (error !== false) {
      message.error(t('taskEdit.cancelFailed'))
      console.error(error)
    }
  } finally {
    cancelling.value = false
  }
}

const previewImage = (url) => {
  const index = previewImagesList.value.findIndex(img => img === url)
  previewCurrentIndex.value = index >= 0 ? index : 0
  previewImageUrl.value = url
  modalWidth.value = 900
  previewVisible.value = true
}

const previewPrev = () => {
  if (previewCurrentIndex.value > 0) {
    previewCurrentIndex.value--
    previewImageUrl.value = previewImagesList.value[previewCurrentIndex.value]
    modalWidth.value = 900
  }
}

const previewNext = () => {
  if (previewCurrentIndex.value < previewImagesList.value.length - 1) {
    previewCurrentIndex.value++
    previewImageUrl.value = previewImagesList.value[previewCurrentIndex.value]
    modalWidth.value = 900
  }
}

const handleImageLoad = (event) => {
  const img = event.target
  imageNaturalWidth.value = img.naturalWidth
  imageNaturalHeight.value = img.naturalHeight
  
  const maxWidth = Math.min(window.innerWidth * 0.85, img.naturalWidth + 60)
  const maxHeight = Math.min(window.innerHeight * 0.85, img.naturalHeight + 120)
  
  const widthRatio = maxWidth / img.naturalWidth
  const heightRatio = maxHeight / img.naturalHeight
  const scale = Math.min(widthRatio, heightRatio)
  
  const targetWidth = Math.round(img.naturalWidth * scale) + 60
  modalWidth.value = Math.max(500, Math.min(targetWidth, 1200))
}

const handleImageError = (event) => {
  console.error('Image load failed:', previewImageUrl.value)
  event.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBkb21pbmFudC1iYXNlbGluZT0ibWlkZGxlIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmJmIiBmb250LXNpemU9IjE2Ij7lm77niYfliqDovb3lpLHotKU88L3RleHQ+PC9zdmc+'
}

const handleModalOpenChange = (open) => {
  if (open) {
    document.addEventListener('keydown', handleKeydown)
  } else {
    document.removeEventListener('keydown', handleKeydown)
  }
}

const handleKeydown = (event) => {
  if (!previewVisible.value) return
  
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    previewPrev()
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    previewNext()
  }
}

const getFileName = (url) => {
  if (!url) return t('taskEdit.unknownFile')
  const parts = url.split('/')
  const filename = parts[parts.length - 1]
  const nameWithoutExt = filename.replace(/\.[^/.]+$/, '')
  return nameWithoutExt || filename
}

const toggleDelete = (record, index) => {
  if (isAbsentRow(record) && !record.isDeleted) {
    record._prevMark = record.SmartMark
    record.SmartMark = '正常'
    record._restored = true
    records.value.splice(index, 1, record)
    return
  }
  record.isDeleted = !record.isDeleted
  if (record.isDeleted) {
    record._prevMark = record.SmartMark
    record.SmartMark = '已删除'
  } else {
    if (record._prevMark && record._prevMark !== '未出勤') {
      record.SmartMark = record._prevMark
      delete record._prevMark
    } else {
      record.SmartMark = '正常'
    }
  }
  records.value.splice(index, 1, record)
}

const isAbsentRow = (record) => {
  const mark = record?.SmartMark || ''
  return mark.includes('未出勤') && !record?._restored
}

const calculateWorkHours = (record) => {
  if (record?.isDeleted || isAbsentRow(record)) {
    return '-'
  }
  
  const arriveTime = record?.ARRIVEE
  const departTime = record?.DEPAR
  const pauseMinutes = record?.PAUSE
  
  if (!arriveTime || !departTime || arriveTime === '???' || departTime === '???') {
    return '-'
  }
  
  const arriveMinutes = parseTimeToMinutes(arriveTime)
  const departMinutes = parseTimeToMinutes(departTime)
  
  if (arriveMinutes === null || departMinutes === null) {
    return '-'
  }
  
  let totalMinutes = departMinutes - arriveMinutes
  if (totalMinutes < 0) {
    totalMinutes += 24 * 60
  }
  
  const pause = (pauseMinutes !== null && pauseMinutes !== undefined && pauseMinutes !== '') ? Number(pauseMinutes) : 0
  const workMinutes = totalMinutes - pause
  
  if (workMinutes < 0) {
    return '-'
  }
  
  const workHours = (workMinutes / 60).toFixed(2)
  return workHours
}

const parseTimeToMinutes = (timeStr) => {
  if (!timeStr || timeStr.trim() === '' || timeStr === '???') {
    return null
  }
  
  const cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':')
  const parts = cleanTime.split(':')
  
  if (parts.length === 2) {
    const hours = parseInt(parts[0], 10)
    const minutes = parseInt(parts[1], 10)
    if (!isNaN(hours) && !isNaN(minutes)) {
      return hours * 60 + minutes
    }
  } else if (parts.length === 1) {
    const num = parseFloat(parts[0])
    if (!isNaN(num)) {
      return Math.floor(num) * 60 + Math.round((num % 1) * 60)
    }
  }
  
  return null
}

const getRowTypeLabel = (record) => {
  if (record?.isDeleted) return '已删除'
  const mark = record?.SmartMark || ''
  if (mark.includes('未出勤')) return '未出勤'
  if (mark.includes('模糊')) return '模糊'
  return '正常'
}

const getRowTypeDotClass = (record) => {
  if (record?.isDeleted) return 'dot-deleted'
  const mark = record?.SmartMark || ''
  if (mark.includes('未出勤')) return 'dot-absent'
  if (mark.includes('模糊')) return 'dot-blurred'
  return 'dot-normal'
}

const getAnomalyTagColor = (reason) => {
  if (reason.includes(t('home.statsAbsent'))) return 'red'
  if (reason.includes(t('home.statsBlurred'))) return 'orange'
  if (reason.includes(t('home.statsHandwriting'))) return 'blue'
  if (reason.includes(t('home.statsDeleted'))) return 'default'
  return 'default'
}

const getAnomalyTagClass = (reason) => {
  if (reason.includes(t('home.statsAbsent'))) return 'tag-red'
  if (reason.includes(t('home.statsBlurred'))) return 'tag-amber'
  if (reason.includes(t('home.statsHandwriting'))) return 'tag-blue'
  return 'tag-default'
}

const getSmartMarkDisplay = (record) => {
  const mark = record?.SmartMark || ''
  if (mark.includes('未出勤')) {
    const shift = record?.HORAIRES_DU_TRAVAIL || ''
    return shift ? `未出勤-${shift}` : '未出勤'
  }
  return mark
}

const hasRequiredMissing = (record) => {
  if (!record || record?.isDeleted || isAbsentRow(record)) return false
  return !record.NO || !record.Date || !record.ARRIVEE || !record.DEPAR || 
    record.PAUSE === null || record.PAUSE === undefined || record.PAUSE === ''
}

const getRowClassName = (record, index) => {
  if (!record) return ''
  if (record?.isDeleted) return 'deleted-row'
  if (hasRequiredMissing(record)) return 'incomplete-row'
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
  return 'default'
}

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
      if (mark.includes(t('home.statsBlurred'))) reasons.push(t('taskEdit.blurredContent'))
      if (mark.includes(t('home.statsHandwriting'))) reasons.push(t('taskEdit.handwrittenContent'))
      if (mark.includes(t('home.statsAbsent'))) reasons.push(t('taskEdit.absentReason'))
      
      if (reasons.length === 0) return null
      return {
        index,
        name: `${record.NO || '?'} - ${record.NOM_PRENOM || '?'}`,
        reasons: [...new Set(reasons)]
      }
    })
    .filter(Boolean)
})

const handleSubmit = async () => {
  const nonDeletedRecords = records.value.filter(r => !r.isDeleted)
  
  const incompleteRecords = nonDeletedRecords.filter(r => {
    if (isAbsentRow(r)) return false
    return !r.NO || !r.Date || !r.ARRIVEE || !r.DEPAR || r.PAUSE === null || r.PAUSE === undefined || r.PAUSE === ''
  })
  
  if (incompleteRecords.length > 0) {
    const details = incompleteRecords.map((r, i) => {
      const missing = []
      if (!r.NO) missing.push(t('taskEdit.workerNumber'))
      if (!r.Date) missing.push(t('taskEdit.date'))
      if (!r.ARRIVEE) missing.push(t('taskEdit.arrival'))
      if (!r.DEPAR) missing.push(t('taskEdit.departure'))
      if (r.PAUSE === null || r.PAUSE === undefined || r.PAUSE === '') missing.push(t('taskEdit.breakTime'))
      return `${t('taskEdit.missingField', { line: records.value.indexOf(r) + 1, id: r.NO || '?' })}: ${missing.join(', ')}`
    })
    message.error(t('taskEdit.requiredFieldsMissing', { count: incompleteRecords.length }))
    console.warn('Missing fields details:', details)
    return
  }
  
  if (nonDeletedRecords.length === 0) {
    message.warning(t('taskEdit.noValidRecords'))
    return
  }
  
  const anomalyRecords = records.value.filter(r => {
    if (r.isDeleted) return false
    const anomalies = r.anomalies || []
    return anomalies.length > 0
  })
  
  const allAnomalyReasons = []
  anomalyRecords.forEach(r => {
    const reasons = r.anomalies || []
    reasons.forEach(reason => {
      if (!allAnomalyReasons.includes(reason)) {
        allAnomalyReasons.push(reason)
      }
    })
  })
  
  const anomalySummary = JSON.stringify({
    totalRecords: records.value.length,
    validRecords: nonDeletedRecords.length,
    deletedRecords: records.value.filter(r => r.isDeleted).length,
    anomalyRecords: anomalyRecords.length,
    anomalyReasons: allAnomalyReasons,
    riskLevel: anomalyRecords.length > 0 ? 'high' : 'none'
  })
  
  submitting.value = true
  try {
    await confirmTask(taskId.value, { 
      data: nonDeletedRecords,
      anomalySummary: anomalySummary
    })
    message.success(t('taskEdit.submitSuccess'))
    router.push('/tasks')
  } catch (error) {
    message.error(t('taskEdit.submitFailed'))
    console.error(error)
  } finally {
    submitting.value = false
  }
}

let isComponentMounted = true

onMounted(() => {
  isComponentMounted = true
  loadTask()
})

onUnmounted(() => {
  isComponentMounted = false
})

watch(taskId, () => {
  if (isComponentMounted) {
    loadTask()
  }
})
</script>

<style lang="scss" scoped>
.task-edit-container {
  padding: 0;

  .edit-card {
    border-radius: 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .header-right {
      .record-count {
        font-size: 13px;
        color: #8c8c8c;
        background: #f5f5f5;
        padding: 4px 12px;
        border-radius: 16px;
      }
    }

    .page-title {
      margin: 0;
      font-size: 20px;
      font-weight: 700;
      color: #1f1f1f;
    }

    .task-id-tag {
      font-size: 13px;
    }
    
    .status-tag {
      font-size: 13px;
    }
    
    .header-right {
      display: flex;
      align-items: center;
      gap: 16px;
      
      .reupload-btn {
        height: 36px;
        border-radius: 6px;
        font-weight: 500;
      }
    }
  }

  .stats-overview {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 12px;
    margin-bottom: 24px;
    
    .stat-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 14px 16px;
      border-radius: 10px;
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
        font-size: 22px;
        line-height: 1;
      }
      
      .stat-info {
        flex: 1;
        
        .stat-number {
          font-size: 20px;
          font-weight: 700;
          color: #1F2329;
          line-height: 1.2;
        }
        
        .stat-label {
          font-size: 12px;
          color: #8F959E;
          line-height: 1.2;
        }
      }
    }
  }

  .edit-tabs {
    :deep(.ant-tabs-nav) {
      margin-bottom: 20px;
    }
  }

  .cell-text {
    font-size: 13px;
    color: #333;
  }

  .mark-tag {
    font-size: 12px;
    border-radius: 4px;
  }

  .anomaly-hint {
    margin-bottom: 20px;
    padding: 14px 18px;
    background: linear-gradient(135deg, #f0f7ff 0%, #e6f2ff 100%);
    border-radius: 10px;
    border-left: 4px solid #3b82f6;

    .anomaly-summary {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .anomaly-icon {
      color: #3b82f6;
      font-size: 18px;
    }

    .anomaly-text {
      font-size: 14px;
      color: #1e40af;
      font-weight: 600;
    }

    .anomaly-toggle {
      font-size: 13px;
      color: #3b82f6;
      padding: 0;
      height: auto;
    }

    .anomaly-detail-list {
      margin-top: 14px;
      padding-top: 14px;
      border-top: 1px solid #bfdbfe;

      .anomaly-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 8px 0;
        font-size: 13px;

        .anomaly-index {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 22px;
          height: 22px;
          border-radius: 50%;
          background: #bfdbfe;
          color: #1e40af;
          font-size: 12px;
          font-weight: 700;
          flex-shrink: 0;
        }

        .anomaly-name {
          color: #333;
          font-weight: 600;
          min-width: 120px;
        }

        .anomaly-reasons {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
        }
      }
    }
  }

  .edit-table {
    :deep(.ant-table) {
      border-radius: 10px;
      border: 1px solid #e8e8e8;
      overflow: hidden;
    }

    :deep(.ant-table-thead > tr > th) {
      background: linear-gradient(180deg, #fafafa 0%, #f5f5f5 100%);
      font-size: 12px;
      font-weight: 700;
      color: #555;
      padding: 12px 10px;
      white-space: nowrap;
      border-bottom: 2px solid #e8e8e8;
    }

    :deep(.ant-table-tbody > tr > td) {
      padding: 10px 10px;
      font-size: 13px;
      vertical-align: middle;
      border-bottom: 1px solid #f0f0f0;
    }

    :deep(.ant-table-tbody > tr:hover > td) {
      background: #fafafa !important;
    }

    :deep(.ant-input) {
      font-size: 13px;
      padding: 4px 8px;
      border-radius: 6px;
      background: transparent;
      transition: all 0.2s;

      &:focus, &:hover {
        background: #fff;
        box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.15);
      }
    }

    :deep(.ant-input-number) {
      font-size: 13px;

      .ant-input-number-input {
        padding: 4px 8px;
      }
    }

    :deep(.ant-input-number-focused) {
      box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.15);
    }
  }

  .row-type-dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-right: 6px;
    vertical-align: middle;

    &.dot-normal { background-color: #22c55e; }
    &.dot-blurred { background-color: #f59e0b; }
    &.dot-absent { background-color: #ef4444; }
    &.dot-deleted { background-color: #d9d9d9; }
  }

  .row-type-label {
    font-size: 12px;
    color: #666;
    vertical-align: middle;
    font-weight: 500;
  }

  .action-btn {
    padding: 4px 8px;
  }

  :deep(.required-empty) {
    background: #fff !important;
    border-color: #ff4d4f !important;
    border-radius: 6px;

    &:hover {
      border-color: #ff7875 !important;
    }

    input {
      border-color: transparent !important;
    }
  }

  .image-preview-section {
    padding: 16px 0;

    .image-preview-header {
      margin-bottom: 18px;

      .image-preview-title {
        font-weight: 700;
        font-size: 15px;
        color: #333;
      }
    }

    .image-preview-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: 16px;

      .image-preview-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;

        .image-card {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 16px;
          background: linear-gradient(135deg, #f8f9ff 0%, #f0f4ff 100%);
          border: 1px solid #e8eeff;
          border-radius: 10px;
          cursor: pointer;
          transition: all 0.3s ease;
          width: 100%;

          &:hover {
            background: linear-gradient(135deg, #f0f4ff 0%, #e8eeff 100%);
            border-color: #5B8FF9;
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(91, 143, 249, 0.15);
          }

          .image-icon {
            font-size: 32px;
            color: #5B8FF9;
            flex-shrink: 0;
          }

          .image-info {
            flex: 1;
            min-width: 0;

            .image-name {
              font-size: 13px;
              font-weight: 600;
              color: #333;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
              margin-bottom: 4px;
            }

            .image-hint {
              font-size: 11px;
              color: #8F959E;
            }
          }

          .preview-icon {
            font-size: 20px;
            color: #8F959E;
            flex-shrink: 0;
          }
        }
      }
    }
  }

  .preview-modal-content {
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
    align-items: center;
    min-height: 300px;
    max-height: 75vh;
    padding: 8px;
    overflow: hidden;
    gap: 16px;

    .preview-nav-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      gap: 12px;
    }

    .preview-nav-btn {
      display: flex;
      align-items: center;
      gap: 4px;
      height: 36px;
      padding: 0 14px;
      border-radius: 6px;
      font-size: 14px;
      transition: all 0.2s;
      background: #fff;
      border: 1px solid #d9d9d9;

      &:hover:not(:disabled) {
        background: #1890ff;
        border-color: #1890ff;
        color: #fff;
      }

      &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
      }
    }

    .preview-index-text {
      font-size: 14px;
      color: #666;
      font-weight: 500;
    }

    .preview-image-wrapper {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: center;
      max-height: 100%;
      overflow: hidden;
    }

    .preview-image {
      max-width: 100%;
      max-height: calc(75vh - 68px);
      width: auto;
      height: auto;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      object-fit: contain;
    }
  }

  .action-bar {
    margin-top: 28px;
    padding-top: 24px;
    border-top: 1px solid #f0f0f0;
    display: flex;
    justify-content: center;
    gap: 20px;
  }
}
</style>

<style lang="scss">
.task-edit-container {
  .edit-table {
    .ant-table-tbody > tr.deleted-row,
    .ant-table-tbody > tr.absent-row {
      td {
        background-color: #fff5f5;
      }
    }
    
    .ant-table-tbody > tr.deleted-row:hover > td,
    .ant-table-tbody > tr.absent-row:hover > td {
      background-color: #ffe8e8 !important;
    }
    
    .ant-table-tbody > tr.incomplete-row {
      td {
        background-color: #fffbf0;
      }
    }
    
    .ant-table-tbody > tr.incomplete-row:hover > td {
      background-color: #fff8e1 !important;
    }
    
    .ant-table-tbody > tr.blurred-row {
      td {
        background-color: #fffef0;
      }
    }
    
    .ant-table-tbody > tr.blurred-row:hover > td {
      background-color: #fffdd0 !important;
    }
  }
}
</style>
