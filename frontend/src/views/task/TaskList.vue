<template>
  <div class="task-list-container">
    <a-card class="task-card">
      <div class="card-header">
        <div class="header-left">
          <h3 class="card-title">
            <UnorderedListOutlined />
            <span>{{ $t('tasks.title') }}</span>
          </h3>
          <p class="card-desc">{{ $t('tasks.subtitle') }}</p>
        </div>
        <a-button 
          type="primary" 
          @click="$router.push('/home')"
          class="btn-primary-gradient"
        >
          <PlusOutlined />
          {{ $t('tasks.createNew') }}
        </a-button>
      </div>
      
      <div class="filter-bar">
        <a-select 
          v-model:value="filterStatus" 
          :placeholder="$t('tasks.filterStatus')" 
          allow-clear 
          class="status-select"
          @change="handleFilter"
        >
          <a-select-option value="">{{ $t('tasks.allStatus') }}</a-select-option>
          <a-select-option value="processing">{{ $t('tasks.statusProcessing') }}</a-select-option>
          <a-select-option value="processed">{{ $t('tasks.statusProcessed') }}</a-select-option>
          <a-select-option value="confirmed">{{ $t('tasks.statusConfirmed') }}</a-select-option>
          <a-select-option value="failed">{{ $t('tasks.statusFailed') }}</a-select-option>
          <a-select-option value="cancelled">{{ $t('tasks.statusCancelled') }}</a-select-option>
        </a-select>
        
        <a-select 
          v-model:value="searchField" 
          :placeholder="$t('tasks.searchField')" 
          allow-clear 
          class="search-field-select"
        >
          <a-select-option value="">{{ $t('tasks.allField') }}</a-select-option>
          <a-select-option value="taskId">{{ $t('tasks.taskId') }}</a-select-option>
          <a-select-option value="fileKey">{{ $t('tasks.fileName') }}</a-select-option>
          <a-select-option value="userName">{{ $t('tasks.operator') }}</a-select-option>
        </a-select>
        
        <a-input
          v-model:value="keyword"
          :placeholder="$t('tasks.searchContent')"
          allow-clear
          class="search-input"
          @clear="handleFilter"
          @keyup.enter="handleFilter"
          :prefix-icon="SearchOutlined"
        />
      </div>
      
      <a-table 
        :columns="columns" 
        :data-source="tasks" 
        :loading="loading" 
        :pagination="false"
        class="task-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)" class="status-tag">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'fileKey'">
            <div class="file-cell" @click="previewImages(record)">
              <FileImageOutlined class="file-icon" />
              <span class="file-name">{{ record.fileKey }}</span>
              <span v-if="getImageCount(record) > 1" class="image-count">({{ getImageCount(record) }} {{ $t('tasks.images') }})</span>
              <EyeOutlined class="preview-icon" />
            </div>
          </template>
          <template v-if="column.key === 'action'">
            <div class="action-buttons">
              <a-button type="text" size="small" @click="handleView(record)" class="view-btn">
                <EyeOutlined />
              </a-button>
              <a-button type="text" danger size="small" @click="handleDelete(record)" class="delete-btn">
                <DeleteOutlined />
              </a-button>
            </div>
          </template>
        </template>
      </a-table>
      
      <div class="pagination-wrapper">
        <a-pagination
          v-model:current="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-size-options="[10, 20, 50, 100]"
          show-size-changer
          :show-total="(total) => $t('tasks.totalRecords', { total })"
          class="pagination"
          @change="handleCurrentChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>
    
    <a-modal
      v-model:open="previewVisible"
      :footer="null"
      :width="800"
      centered
      :title="$t('tasks.imagePreview')"
      class="image-preview-modal"
    >
      <div v-if="previewImagesList.length > 0" class="preview-wrapper">
        <div class="preview-header" v-if="previewImagesList.length > 1">
          <span class="preview-count">{{ currentImageIndex + 1 }} / {{ previewImagesList.length }}</span>
        </div>
        <div class="preview-body" ref="previewBodyRef">
          <img 
            :src="previewImagesList[currentImageIndex]" 
            class="preview-img" 
            @error="handleImageError" 
            @load="handleImageLoad"
            :style="imageStyle"
          />
        </div>
        <div class="preview-footer" v-if="previewImagesList.length > 1">
          <a-button type="text" @click="prevImage" class="nav-btn">
            {{ $t('tasks.previous') }}
          </a-button>
          <div class="preview-dots">
            <span 
              v-for="(_, idx) in previewImagesList" 
              :key="idx"
              :class="['dot', { active: currentImageIndex === idx }]"
              @click="currentImageIndex = idx"
            ></span>
          </div>
          <a-button type="text" @click="nextImage" class="nav-btn">
            {{ $t('tasks.next') }}
          </a-button>
        </div>
      </div>
      <a-empty v-else :description="$t('tasks.noImages')" />
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import { 
  PlusOutlined, 
  SearchOutlined, 
  UnorderedListOutlined,
  EyeOutlined,
  DeleteOutlined,
  FileImageOutlined
} from '@ant-design/icons-vue'
import { getTaskList, deleteTask } from '@/api/task'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const tasks = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterStatus = ref('')
const keyword = ref('')
const searchField = ref('')
const previewVisible = ref(false)
const previewImagesList = ref([])
const currentImageIndex = ref(0)
const previewBodyRef = ref(null)
const imageStyle = ref({
  maxWidth: '100%',
  maxHeight: '100%',
  width: 'auto',
  height: 'auto',
  objectFit: 'contain',
  display: 'block',
  flexShrink: '0'
})

const columns = [
  { title: t('tasks.taskId'), dataIndex: 'taskId', key: 'taskId', width: 150, ellipsis: true },
  { title: t('tasks.fileName'), dataIndex: 'fileKey', key: 'fileKey', ellipsis: true },
  { title: t('tasks.operator'), dataIndex: 'userName', key: 'userName', width: 120, ellipsis: true },
  { title: t('tasks.status'), dataIndex: 'status', key: 'status', width: 100 },
  { title: t('tasks.createTime'), dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: t('tasks.operation'), key: 'action', width: 100, fixed: 'right' },
]

const loadTasks = async () => {
  loading.value = true
  try {
    const response = await getTaskList({
      current: currentPage.value,
      size: pageSize.value,
      status: filterStatus.value,
      keyword: keyword.value,
      searchField: searchField.value,
    })
    
    tasks.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error) {
    console.error('加载任务列表失败:', error)
  } finally {
    loading.value = false
  }
}

const getImageCount = (record) => {
  if (!record.imageUrls) return 0
  try {
    const urls = typeof record.imageUrls === 'string' ? JSON.parse(record.imageUrls) : record.imageUrls
    return urls.length || 1
  } catch {
    return 1
  }
}

const previewImages = (record) => {
  if (!record.imageUrls) {
    message.warning(t('tasks.noImages'))
    return
  }
  
  try {
    const urls = typeof record.imageUrls === 'string' ? JSON.parse(record.imageUrls) : record.imageUrls
    previewImagesList.value = urls.map(url => {
      if (url.startsWith('http') || url.startsWith('/api')) {
        return url
      }
      return `/api/local/image/${url}`
    })
    currentImageIndex.value = 0
    previewVisible.value = true
  } catch (e) {
    console.error(t('messages.networkError'), e)
    message.error(t('messages.systemError'))
  }
}

const prevImage = () => {
  if (currentImageIndex.value > 0) {
    currentImageIndex.value--
  } else {
    currentImageIndex.value = previewImagesList.value.length - 1
  }
}

const nextImage = () => {
  if (currentImageIndex.value < previewImagesList.value.length - 1) {
    currentImageIndex.value++
  } else {
    currentImageIndex.value = 0
  }
}

const handleImageError = (event) => {
  event.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iNDAwIiBoZWlnaHQ9IjMwMCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBkb21pbmFudC1iYXNlbGluZT0ibWlkZGxlIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmZiZmJmIiBmb250LXNpemU9IjE2Ij7lm77niYfliqDovb3lpLHotKU88L3RleHQ+PC9zdmc+'
}

const handleImageLoad = (event) => {
  const img = event.target
  const container = previewBodyRef.value
  if (!container) return
  
  const containerWidth = container.offsetWidth
  const containerHeight = container.offsetHeight
  const imgWidth = img.naturalWidth
  const imgHeight = img.naturalHeight
  
  const widthRatio = containerWidth / imgWidth
  const heightRatio = containerHeight / imgHeight
  const scale = Math.min(widthRatio, heightRatio)
  
  if (scale < 1) {
    imageStyle.value = {
      width: `${imgWidth * scale}px`,
      height: `${imgHeight * scale}px`,
      maxWidth: '100%',
      maxHeight: '100%',
      objectFit: 'contain',
      display: 'block',
      flexShrink: '0'
    }
  }
}

const handleFilter = () => {
  currentPage.value = 1
  loadTasks()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  loadTasks()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  loadTasks()
}

const handleView = (record) => {
  router.push(`/tasks/${record.taskId}`)
}

const handleDelete = async (record) => {
  try {
    await Modal.confirm({
      title: t('common.delete'),
      content: t('tasks.deleteConfirm'),
      okText: t('common.confirm'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        await deleteTask(record.taskId)
        message.success(t('tasks.deleteSuccess'))
        loadTasks()
      },
    })
  } catch (error) {
    console.error(t('messages.systemError'), error)
  }
}

const getStatusColor = (status) => {
  const colorMap = {
    processing: 'orange',
    processed: 'blue',
    confirmed: 'green',
    failed: 'red',
    cancelled: 'default',
  }
  return colorMap[status] || 'default'
}

const getStatusText = (status) => {
  const textMap = {
    processing: t('tasks.statusProcessing'),
    processed: t('tasks.statusProcessed'),
    confirmed: t('tasks.statusConfirmed'),
    failed: t('tasks.statusFailed'),
    cancelled: t('tasks.statusCancelled'),
  }
  return textMap[status] || status
}

onMounted(() => {
  loadTasks()
})

watch(() => route.path, (newPath, oldPath) => {
  // 当路径变化时，如果从详情页返回任务列表，重新加载数据
  if (newPath === '/tasks' && oldPath?.startsWith('/tasks/')) {
    loadTasks()
  }
})
</script>

<style lang="scss" scoped>
.task-list-container {
  .task-card {
    border-radius: 14px;
    border: none;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
    overflow: hidden;
    
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px 24px 16px;
      border-bottom: 1px solid #F0F1F5;
      
      .header-left {
        display: flex;
        flex-direction: column;
        
        .card-title {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 17px;
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
      
      .btn-primary-gradient {
        padding: 8px 20px;
        border-radius: 10px;
        font-weight: 500;
        display: flex;
        align-items: center;
        gap: 6px;
        background: linear-gradient(135deg, #5B8FF9 0%, #7B61FF 100%);
        border: none;
        
        &:hover {
          opacity: 0.95;
          transform: translateY(-1px);
        }
      }
    }
    
    .filter-bar {
      display: flex;
      gap: 12px;
      padding: 16px 24px;
      background: #FAFBFC;
      
      .status-select {
        width: 140px;
        
        :deep(.ant-select-selector) {
          border-radius: 8px;
          border-color: #E5E6EB;
        }
      }
      
      .search-input {
        width: 240px;
        
        :deep(.ant-input) {
          border-radius: 8px;
          border-color: #E5E6EB;
        }
      }
    }
    
    .task-table {
      padding: 0 24px;
      
      :deep(.ant-table) {
        border-radius: 10px;
        overflow: hidden;
      }
      
      :deep(.ant-table-thead > tr > th) {
        background: #FAFBFC;
        border-bottom: 1px solid #F0F1F5;
        font-weight: 600;
        font-size: 13px;
        color: #4E5969;
        padding: 12px 16px;
      }
      
      :deep(.ant-table-tbody > tr) {
        transition: all 0.2s ease;
        
        &:hover {
          background: #FAFBFC;
        }
      }
      
      :deep(.ant-table-tbody > tr > td) {
        padding: 12px 16px;
        font-size: 13px;
        color: #1F2329;
        border-bottom: 1px solid #F5F7FA;
      }
      
      .status-tag {
        border-radius: 6px;
        font-size: 12px;
        font-weight: 500;
        padding: 2px 10px;
      }
      
      .action-buttons {
        display: flex;
        gap: 8px;
        
        .view-btn {
          color: #5B8FF9;
          
          &:hover {
            color: #4070F4;
            background: rgba(91, 143, 249, 0.1);
            border-radius: 6px;
          }
        }
        
        .delete-btn {
          color: #FF4D4F;
          
          &:hover {
            color: #FF7875;
            background: rgba(255, 77, 79, 0.1);
            border-radius: 6px;
          }
        }
      }
    }
    
    .pagination-wrapper {
      padding: 20px 24px;
      border-top: 1px solid #F0F1F5;
      
      .pagination {
        display: flex;
        justify-content: flex-end;
        
        :deep(.ant-pagination-item-active) {
          background: #5B8FF9;
          border-color: #5B8FF9;
        }
      }
    }
    
    .search-field-select {
      width: 120px;
      
      :deep(.ant-select-selector) {
        border-radius: 8px;
        border-color: #E5E6EB;
      }
    }
    
    .file-cell {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      color: #5B8FF9;
      
      &:hover {
        color: #4070F4;
        text-decoration: underline;
      }
      
      .file-icon {
        font-size: 14px;
      }
      
      .file-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      
      .image-count {
        font-size: 12px;
        color: #8F959E;
      }
      
      .preview-icon {
        font-size: 12px;
        opacity: 0.7;
      }
    }
  }
  
  .image-preview-modal {
    :deep(.ant-modal-content) {
      border-radius: 12px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
      width: 800px !important;
      max-width: 95vw !important;
    }
    
    :deep(.ant-modal-body) {
      padding: 16px;
      max-height: 70vh;
      height: 70vh;
      overflow: hidden;
      box-sizing: border-box;
      display: flex;
      flex-direction: column;
    }
    
    .preview-wrapper {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
      flex: 1;
      min-height: 0;
      box-sizing: border-box;
    }
    
    .preview-header {
      text-align: center;
      flex-shrink: 0;
      
      .preview-count {
        font-size: 14px;
        color: #646A73;
        font-weight: 500;
      }
    }
    
    .preview-body {
      flex: 1;
      display: flex;
      justify-content: center;
      align-items: center;
      background: #F5F7FA;
      border-radius: 8px;
      padding: 10px;
      overflow: hidden;
      min-height: 0;
      position: relative;
    }
    
    :deep(.preview-body img) {
      max-width: 100% !important;
      max-height: 100% !important;
      width: auto !important;
      height: auto !important;
      object-fit: contain !important;
      display: block !important;
      border-radius: 6px;
      flex-shrink: 0;
    }
    
    .preview-footer {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      flex-shrink: 0;
      
      .nav-btn {
        color: #5B8FF9;
        font-size: 14px;
        
        &:hover {
          color: #4070F4;
        }
      }
      
      .preview-dots {
        display: flex;
        gap: 8px;
        
        .dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #D0D3D9;
          cursor: pointer;
          transition: all 0.2s;
          
          &.active {
            background: #5B8FF9;
            transform: scale(1.2);
          }
          
          &:hover {
            background: #8F959E;
          }
        }
      }
    }
  }
}
</style>
