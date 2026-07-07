import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { getUserInfo, login as loginApi } from '@/api/auth'
import { getMyPermissions } from '@/api/permissions'
import { getCachedWorkingCountry, setCachedWorkingCountry, clearWorkingCountryConfigured } from '@/utils/countryHeader'
import { hasPersonalWorkingCountry } from '@/utils/workingCountrySetup'

function syncWorkingCountryFromUserInfo(userInfo) {
  if (!userInfo) return
  if (hasPersonalWorkingCountry(userInfo)) {
    setCachedWorkingCountry(userInfo.personalWorkingCountry || userInfo.workingCountry)
    return
  }
  const cached = getCachedWorkingCountry()
  if (cached && cached !== 'default') return
  setCachedWorkingCountry('default')
}

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
    canCalibrateRecord: (state) => state.userInfo?.permissions?.recordCalibrate === true,
    canDeleteConfirmedTask: (state) => {
      if (state.roles.includes('admin')) return true
      return state.userInfo?.permissions?.taskDeleteConfirmed === true
    },
  },

  actions: {
    async login(credentials) {
      const response = await loginApi(credentials)
      this.token = response.data.token
      this.userInfo = response.data.userInfo
      this.roles = response.data.userInfo?.role ? [response.data.userInfo.role] : []
      setToken(this.token)
      localStorage.setItem('userInfo', JSON.stringify(response.data.userInfo))
      syncWorkingCountryFromUserInfo(response.data.userInfo)
      return response
    },

    async fetchUserInfo() {
      try {
        const response = await getUserInfo()
        this.userInfo = response.data
        this.roles = response.data?.role ? [response.data.role] : []
        localStorage.setItem('userInfo', JSON.stringify(response.data))
        syncWorkingCountryFromUserInfo(response.data)
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
      clearWorkingCountryConfigured()
      setCachedWorkingCountry('default')
    },

    async refreshPermissions(workingCountry) {
      if (!this.userInfo) return null
      try {
        const country = workingCountry || getCachedWorkingCountry()
        const response = await getMyPermissions(country !== 'default' ? country : undefined)
        if (response?.data) {
          this.userInfo = { ...this.userInfo, permissions: response.data }
          localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
        }
        return this.userInfo?.permissions
      } catch (error) {
        console.error('刷新权限失败:', error)
        return this.userInfo?.permissions
      }
    },
  },
})
