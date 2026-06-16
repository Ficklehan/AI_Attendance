import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import { getToken } from '@/utils/auth'
import { getCachedWorkingCountry } from '@/utils/countryHeader'
import { showApiError, showErrorMessage, translateApiError } from '@/utils/translateError'
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
    const locale = localStorage.getItem('locale')
    if (locale) {
      config.headers['X-Locale'] = locale
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
      const silent = response.config?.silentError === true
      const text = silent ? translateApiError(res) : showApiError(res)

      if (res.code === 401 || res.code === 1004 || res.messageKey === 'errors.userDisabled') {
        const authStore = useAuthStore()
        authStore.logout()
        router.push('/login')
      }

      const err = new Error(text)
      err.messageKey = res.messageKey
      err.messageArgs = res.messageArgs
      err.apiCode = res.code
      return Promise.reject(err)
    }
    
    return res
  },
  (error) => {
    if (error.config?.silentError === true) {
      if (error.response?.data) {
        const data = error.response.data
        const err = new Error(translateApiError(data))
        err.messageKey = data.messageKey
        err.messageArgs = data.messageArgs
        err.apiCode = data.code
        return Promise.reject(err)
      }
      const err = new Error(translateApiError({ message: error.message, code: error.code }))
      err.messageKey = error.messageKey
      return Promise.reject(err)
    }
    const messageText = showErrorMessage(error)
    return Promise.reject(new Error(messageText))
  }
)

export default request