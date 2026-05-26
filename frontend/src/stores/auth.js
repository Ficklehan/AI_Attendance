import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { getUserInfo, login as loginApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => {
    let userInfo = null
    try {
      const userInfoStr = localStorage.getItem('userInfo')
      if (userInfoStr) {
        userInfo = JSON.parse(userInfoStr)
      }
    } catch (e) {
      console.error('Failed to parse userInfo from localStorage', e)
    }
    
    return {
      token: getToken() || '',
      userInfo,
      roles: userInfo?.role ? [userInfo.role] : [],
    }
  },

  getters: {
    isAuthenticated: (state) => !!state.token,
    username: (state) => state.userInfo?.username || '',
    realName: (state) => state.userInfo?.realName || '',
    isAdmin: (state) => state.roles.includes('admin'),
  },

  actions: {
    async login(credentials) {
      const response = await loginApi(credentials)
      this.token = response.data.token
      this.userInfo = response.data.userInfo
      this.roles = response.data.userInfo?.role ? [response.data.userInfo.role] : []
      setToken(this.token)
      localStorage.setItem('userInfo', JSON.stringify(response.data.userInfo))
      return response
    },

    async fetchUserInfo() {
      try {
        const response = await getUserInfo()
        this.userInfo = response.data
        this.roles = response.data?.role ? [response.data.role] : []
        localStorage.setItem('userInfo', JSON.stringify(response.data))
        return response.data
      } catch (error) {
        this.logout()
        throw error
      }
    },

    logout() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      removeToken()
      localStorage.removeItem('userInfo')
    },
  },
})
