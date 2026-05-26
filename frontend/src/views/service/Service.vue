<template>
  <div class="service-container">
    <a-card>
      <h3>服务管理</h3>
      
      <div class="service-grid">
        <a-card class="service-card" :class="{ running: status.backend }">
          <div class="service-header">
            <div class="service-icon">
              <CloudServerOutlined />
            </div>
            <div class="service-info">
              <h4>后端服务</h4>
              <p class="status-text">
                <span :class="{ online: status.backend }">
                  {{ status.backend ? '运行中' : '已停止' }}
                </span>
                <span class="port">端口: 3000</span>
              </p>
            </div>
          </div>
          <div class="service-actions">
            <a-button 
              v-if="!status.backend" 
              type="primary" 
              :loading="loading.backend"
              @click="startBackend"
            >
              启动
            </a-button>
            <a-button 
              v-else 
              type="danger" 
              :loading="loading.backend"
              @click="stopBackend"
            >
              停止
            </a-button>
          </div>
        </a-card>
        
        <a-card class="service-card" :class="{ running: status.frontend }">
          <div class="service-header">
            <div class="service-icon">
              <MonitorOutlined />
            </div>
            <div class="service-info">
              <h4>前端服务</h4>
              <p class="status-text">
                <span :class="{ online: status.frontend }">
                  {{ status.frontend ? '运行中' : '已停止' }}
                </span>
                <span class="port">端口: 5173</span>
              </p>
            </div>
          </div>
          <div class="service-actions">
            <a-button 
              v-if="!status.frontend" 
              type="primary" 
              :loading="loading.frontend"
              @click="startFrontend"
            >
              启动
            </a-button>
            <a-button 
              v-else 
              type="danger" 
              :loading="loading.frontend"
              @click="stopFrontend"
            >
              停止
            </a-button>
          </div>
        </a-card>
      </div>
      
      <div class="global-actions">
        <a-button type="success" @click="startAll" :disabled="status.backend && status.frontend">
          <PlayCircleOutlined />
          启动所有服务
        </a-button>
        <a-button type="warning" @click="stopAll" :disabled="!status.backend && !status.frontend">
          <StopOutlined />
          停止所有服务
        </a-button>
      </div>
      
      <div class="tips">
        <a-alert message="提示" description="
          <ul>
            <li>后端服务端口: <code>3000</code></li>
            <li>前端服务端口: <code>5173</code></li>
            <li>服务启动需要一定时间，请耐心等待</li>
            <li>如果服务启动失败，请检查端口是否被占用</li>
          </ul>
        " type="info" :closable="false" />
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import { CloudServerOutlined, MonitorOutlined, PlayCircleOutlined, StopOutlined } from '@ant-design/icons-vue'
import request from '@/api/index'

const status = reactive({
  backend: false,
  frontend: false,
})

const loading = reactive({
  backend: false,
  frontend: false,
})

let refreshInterval = null

const loadStatus = async () => {
  try {
    const response = await request({
      url: '/service/status',
      method: 'get',
    })
    status.backend = response.data.backend
    status.frontend = response.data.frontend
  } catch (error) {
    status.backend = false
    status.frontend = false
  }
}

const startBackend = async () => {
  loading.backend = true
  try {
    await request({
      url: '/service/backend/start',
      method: 'post',
    })
    message.success('后端服务启动成功')
  } catch (error) {
    message.error('后端服务启动失败')
  } finally {
    loading.backend = false
  }
}

const stopBackend = async () => {
  loading.backend = true
  try {
    await request({
      url: '/service/backend/stop',
      method: 'post',
    })
    message.success('后端服务已停止')
  } catch (error) {
    message.error('后端服务停止失败')
  } finally {
    loading.backend = false
  }
}

const startFrontend = async () => {
  loading.frontend = true
  try {
    await request({
      url: '/service/frontend/start',
      method: 'post',
    })
    message.success('前端服务启动成功')
  } catch (error) {
    message.error('前端服务启动失败')
  } finally {
    loading.frontend = false
  }
}

const stopFrontend = async () => {
  loading.frontend = true
  try {
    await request({
      url: '/service/frontend/stop',
      method: 'post',
    })
    message.success('前端服务已停止')
  } catch (error) {
    message.error('前端服务停止失败')
  } finally {
    loading.frontend = false
  }
}

const startAll = async () => {
  await startBackend()
  await new Promise(resolve => setTimeout(resolve, 2000))
  await startFrontend()
}

const stopAll = async () => {
  await stopBackend()
  await stopFrontend()
}

onMounted(() => {
  loadStatus()
  refreshInterval = setInterval(loadStatus, 5000)
})

onUnmounted(() => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
  }
})
</script>

<style lang="scss" scoped>
.service-container {
  .service-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 20px;
    margin-bottom: 20px;
  }
  
  .service-card {
    border: 2px solid #ebeef5;
    transition: all 0.3s;
    
    &.running {
      border-color: #67c23a;
      background: #f0f9eb;
    }
    
    .service-header {
      display: flex;
      align-items: center;
      margin-bottom: 16px;
      
      .service-icon {
        width: 50px;
        height: 50px;
        border-radius: 50%;
        background: #ecf5ff;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-right: 16px;
        font-size: 24px;
        color: #409EFF;
        
        .running & {
          background: #f0f9eb;
          color: #67c23a;
        }
      }
      
      .service-info {
        h4 {
          margin: 0 0 4px;
          color: #303133;
        }
        
        .status-text {
          margin: 0;
          font-size: 14px;
          color: #606266;
          
          span.online {
            color: #67c23a;
            font-weight: bold;
          }
          
          .port {
            margin-left: 12px;
            color: #909399;
          }
        }
      }
    }
    
    .service-actions {
      text-align: center;
    }
  }
  
  .global-actions {
    display: flex;
    justify-content: center;
    gap: 12px;
    margin-bottom: 20px;
  }
  
  .tips {
    code {
      background: #f5f7fa;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 12px;
    }
  }
}
</style>