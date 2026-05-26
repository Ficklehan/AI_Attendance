import { ref, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import { getToken } from '@/utils/auth'

export function useSSE(url, options = {}) {
  const { onMessage, onError, onComplete } = options
  
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)
  const records = ref([])
  let eventSource = null
  
  const connect = (params = {}) => {
    if (!getToken()) {
      error.value = new Error('未登录')
      return
    }
    
    loading.value = true
    error.value = null
    
    const queryParams = new URLSearchParams(params).toString()
    const fullUrl = `${import.meta.env.VITE_API_BASE_URL || '/api'}${url}${queryParams ? '?' + queryParams : ''}`
    
    eventSource = new EventSource(fullUrl, {
      withCredentials: true,
    })
    
    eventSource.onopen = () => {
      loading.value = true
    }
    
    eventSource.onmessage = (event) => {
      try {
        const result = JSON.parse(event.data)
        data.value = result
        
        if (onMessage) {
          onMessage(result)
        }
      } catch (e) {
        console.error('SSE数据解析失败:', e)
      }
    }
    
    eventSource.addEventListener('record', (event) => {
      try {
        const record = JSON.parse(event.data)
        if (onMessage) {
          onMessage({ type: 'record', data: record })
        }
      } catch (e) {
        console.error('SSE record事件解析失败:', e)
      }
    })
    
    eventSource.addEventListener('complete', (event) => {
      try {
        const result = JSON.parse(event.data)
        loading.value = false
        if (onComplete) {
          onComplete(result)
        }
      } catch (e) {
        console.error('SSE complete事件解析失败:', e)
      }
    })
    
    eventSource.addEventListener('error', (event) => {
      try {
        const result = JSON.parse(event.data)
        if (onError) {
          onError(result)
        }
      } catch (e) {
        console.error('SSE error事件解析失败:', e)
      }
    })
    
    eventSource.onerror = (e) => {
      loading.value = false
      error.value = e
      message.error('SSE连接错误')
      
      if (onError) {
        onError(e)
      }
      
      close()
    }
  }
  
  const close = () => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    loading.value = false
  }
  
  const send = async (file, additionalParams = {}) => {
    loading.value = true
    error.value = null
    records.value = []
    
    try {
      const formData = new FormData()
      formData.append('image', file)
      
      Object.entries(additionalParams).forEach(([key, value]) => {
        formData.append(key, value)
      })
      
      const token = getToken()
      const response = await fetch('/api/local/upload-stream', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${token}`,
        },
        body: formData,
      })
      
      if (!response.ok) {
        throw new Error('上传失败')
      }
      
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        
        const text = decoder.decode(value)
        const lines = text.split('\n')
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.slice(6))
              
              if (onMessage) {
                onMessage(data)
              }
            } catch (e) {
              console.error('SSE数据解析失败:', e)
            }
          }
        }
      }
      
      if (onComplete) {
        onComplete({ success: true })
      }
    } catch (e) {
      error.value = e
      message.error('上传失败: ' + e.message)
      
      if (onError) {
        onError(e)
      }
    } finally {
      loading.value = false
    }
  }
  
  onUnmounted(() => {
    close()
  })
  
  return {
    data,
    loading,
    error,
    records,
    connect,
    close,
    send,
  }
}