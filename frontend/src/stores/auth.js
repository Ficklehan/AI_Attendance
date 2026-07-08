import { defineStore } from 'pinia'
import {
  getToken,
  setToken,
  removeToken,
  getStoredUserInfo,
  setStoredUserInfo,
  removeStoredUserInfo,
  touchActivity,
} from '@/utils/auth'
import { resetSessionValidation } from '@/utils/sessionState'
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
    const userInfo = getStoredUserInfo()
    
    return {
      token: getToken() || '',
      userInfo,
    roles: userInfo?.roles?.length
      ? [...userInfo.roles]
      : (userInfo?.role ? [userInfo.role] : []),
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
    canAccessEmployees: (state) => {
      if (state.roles.includes('admin')) return true
      return state.userInfo?.permissions?.employees === true
    },
  },

  actions: {
    async login(credentials) {
      const response = await loginApi(credentials)
      this.token = response.data.token
      this.userInfo = response.data.userInfo
      this.roles = response.data.userInfo?.roles?.length
        ? [...response.data.userInfo.roles]
        : (response.data.userInfo?.role ? [response.data.userInfo.role] : [])
      setToken(this.token)
      setStoredUserInfo(response.data.userInfo)
      touchActivity()
      syncWorkingCountryFromUserInfo(response.data.userInfo)
      return response
    },

    async fetchUserInfo() {
      try {
        const response = await getUserInfo()
        this.userInfo = response.data
        this.roles = response.data?.roles?.length
          ? [...response.data.roles]
          : (response.data?.role ? [response.data.role] : [])
        setStoredUserInfo(response.data)
        touchActivity()
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
      removeStoredUserInfo()
      clearWorkingCountryConfigured()
      setCachedWorkingCountry('default')
      resetSessionValidation()
    },

    async refreshPermissions(workingCountry) {
      if (!this.userInfo) return null
      try {
        const country = workingCountry || getCachedWorkingCountry()
        const response = await getMyPermissions(country !== 'default' ? country : undefined)
        if (response?.data) {
          this.userInfo = { ...this.userInfo, permissions: response.data }
          setStoredUserInfo(this.userInfo)
        }
        return this.userInfo?.permissions
      } catch (error) {
        console.error('刷新权限失败:', error)
        return this.userInfo?.permissions
      }
    },
  },
})
