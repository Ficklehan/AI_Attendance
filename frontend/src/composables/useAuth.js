import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { login as loginApi } from '@/api/auth'

export function useAuth() {
  const router = useRouter()
  const token = ref(getToken() || '')
  const userInfo = ref(null)
  const loading = ref(false)
  
  const isAuthenticated = computed(() => !!token.value)
  
  const login = async (credentials) => {
    loading.value = true
    try {
      const response = await loginApi(credentials)
      token.value = response.data.token
      userInfo.value = response.data.userInfo
      setToken(response.data.token)
      return response
    } catch (error) {
      throw error
    } finally {
      loading.value = false
    }
  }
  
  const logout = () => {
    token.value = ''
    userInfo.value = null
    removeToken()
    router.push('/login')
  }
  
  const setAuth = (newToken, info) => {
    token.value = newToken
    userInfo.value = info
    setToken(newToken)
  }
  
  const clearAuth = () => {
    token.value = ''
    userInfo.value = null
    removeToken()
  }
  
  return {
    token,
    userInfo,
    loading,
    isAuthenticated,
    login,
    logout,
    setAuth,
    clearAuth,
  }
}
