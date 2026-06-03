import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { getToken } from '@/utils/auth'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { showApiError, showErrorMessage } from '@/utils/translateError'
import { API_BASE_PATH } from '@/constants/apiBase'

const request = axios.create({
  baseURL: API_BASE_PATH,
  timeout: 120000,
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    const country = getCachedWorkingCountry()
    if (country) {
      config.headers['X-Country'] = country
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
      const text = showApiError(res)

      if (res.code === 401 || res.code === 1004) {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
      }

      return Promise.reject(new Error(text))
    }
    
    return res
  },
  (error) => {
    const messageText = showErrorMessage(error)
    return Promise.reject(new Error(messageText))
  }
)

export default request