import axios from 'axios'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { getToken } from '@/utils/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 120000,
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    
    if (res.code !== 200) {
      message.error(res.message || '请求失败')
      
      if (res.code === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
      }
      
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    
    return res
  },
  (error) => {
    const messageText = error.response?.data?.message || error.message || '网络错误'
    message.error(messageText)
    return Promise.reject(error)
  }
)

export default request