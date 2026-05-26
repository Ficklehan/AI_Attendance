import { ref } from 'vue'
import { message } from 'ant-design-vue'

export function usePagination(loadFunction) {
  const current = ref(1)
  const size = ref(20)
  const total = ref(0)
  const loading = ref(false)
  
  const load = async (params = {}) => {
    loading.value = true
    try {
      const response = await loadFunction({
        current: current.value,
        size: size.value,
        ...params,
      })
      
      if (response.data) {
        if (Array.isArray(response.data.records)) {
          return response
        }
        total.value = response.data.total || 0
      }
      
      return response
    } catch (error) {
      message.error('加载数据失败')
      throw error
    } finally {
      loading.value = false
    }
  }
  
  const reset = () => {
    current.value = 1
    total.value = 0
  }
  
  const setCurrent = (val) => {
    current.value = val
  }
  
  const setSize = (val) => {
    size.value = val
    current.value = 1
  }
  
  return {
    current,
    size,
    total,
    loading,
    load,
    reset,
    setCurrent,
    setSize,
  }
}